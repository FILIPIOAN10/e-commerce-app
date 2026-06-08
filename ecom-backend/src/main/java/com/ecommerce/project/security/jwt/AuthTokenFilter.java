package com.ecommerce.project.security.jwt;

import com.ecommerce.project.security.services.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AuthTokenFilter.class);
    private JwtUtils jwtUtils;
    private UserDetailsServiceImpl userDetailsService;


    public AuthTokenFilter(JwtUtils jwtUtils, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }


    /**
     * Executed for every incoming request.
     * Extracts JWT from cookies, validates it, loads the user, and sets authentication in context.
     */

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        logger.debug("AuthTokenFilter called for URI: {} ",
                request.getRequestURI());

        if(request.getRequestURI().startsWith("/api/auth/")){
            filterChain.doFilter(request,response);
            return;
        }
        try {
            // extract the token
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                // Extract username
                String username = jwtUtils.getUserNameFromJWTToken(jwt);
                // Load user from DB
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                // Create Spring Security authentication object
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                // atach the request details to the auth object

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                // Store authentication in context
                SecurityContextHolder.getContext().setAuthentication(authentication);

                logger.debug("Roles from JWT: {} ", userDetails.getAuthorities());
            }

        } catch (Exception e) {
            logger.error("Cannot set user authentication", e);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the JWT token from cookies.
     */
//    private String parseJwt(HttpServletRequest request) throws Exception {
//        String jwt = jwtUtils.getJwtFromCookies(request);
//        logger.debug("AuthTokenFilter.java {}", jwt);
//        return jwt;
//    }
    private String parseJwt(HttpServletRequest request) throws Exception {
        String jwtFromHeader = jwtUtils.getJwtFromHeader(request);
        if (jwtFromHeader != null) {
            return jwtFromHeader;
        }
        String jwtFromCookie = jwtUtils.getJwtFromCookies(request);
        if (jwtFromCookie != null) {
            return jwtFromCookie;
        }

        return null;
    }

}
