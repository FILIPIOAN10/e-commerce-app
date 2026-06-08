package com.ecommerce.project.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RateLimitFilter  extends OncePerRequestFilter {

    private static final String ERROR_BODY = "{\"message\":\"Too many reuqests. Try again later.\",\"status\":false}";

    private final RedisRateLimitService redisRateLimitService;
    private final List<RateLimitRule> rateLimitRules;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();


    // verify current request and block with 429 if limit is exceed
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Optional<RateLimitRule> matchedRule= findRule(request);

        if(matchedRule.isEmpty()){
            filterChain.doFilter(request,response);
            return;
        }

        RateLimitRule rule = matchedRule.get();
        RateLimitResult  result = redisRateLimitService.checkLimit(buildRedisKey(request,rule),rule);


        response.setHeader("X-RateLimit-Limit",String.valueOf(rule.getLimit()));
        response.setHeader("X-RateLimit-Remaining",String.valueOf(result.getRemainingRequests()));
        response.setHeader("Retry-After",String.valueOf(result.getRetryAfterSeconds()));


        if(!result.isAllowed()){
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.CONTENT_TYPE,"application/json");
            response.getOutputStream().write(ERROR_BODY.getBytes(StandardCharsets.UTF_8));
            return;
        }
        filterChain.doFilter(request,response);
    }
    private Optional<RateLimitRule> findRule(HttpServletRequest request){
        String method = request.getMethod();
        String path = request.getRequestURI();

        return rateLimitRules.stream()
                .filter(rule -> rule.getMethod().equalsIgnoreCase(method))
                .filter(rule-> pathMatcher.match(rule.getPathPattern(),path))
                .findFirst();
    }
    private String buildRedisKey(HttpServletRequest request,RateLimitRule rule){
        String identity = rule.getKeyType() == RateLimitKeyType.USER
                ? authenticatedUserOrIp(request)
                : clientIp(request);
        return "rate_limit:" +rule.getName()+ ":"+identity;
    }

    private String authenticatedUserOrIp(HttpServletRequest request){
        Authentication  authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication !=null && authentication.isAuthenticated() && authentication.getName() !=null){
            return "user" +authentication.getName();
        }
        return "ip" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request){
        String forwarderFor = request.getHeader("X-Forwarded-for");
        if(forwarderFor !=null && !forwarderFor.isBlank()){
            return forwarderFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
