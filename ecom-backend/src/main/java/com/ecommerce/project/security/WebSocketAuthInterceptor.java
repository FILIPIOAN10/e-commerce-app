package com.ecommerce.project.security;

import com.ecommerce.project.security.jwt.JwtUtils;
import com.ecommerce.project.security.redis.TokenBlacklistService;
import com.ecommerce.project.security.services.UserDetailsImpl;
import com.ecommerce.project.security.services.UserDetailsServiceImpl;
import jakarta.servlet.http.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Authenticates the STOMP/SockJS handshake before a WebSocket session is opened.
 * <p>
 * The access token lives in an {@code HttpOnly} cookie, so the browser cannot copy
 * it into a STOMP {@code CONNECT} header — it has to be read here, on the HTTP
 * handshake request, where the cookie is still present. A handshake without a
 * valid, non-blacklisted access token is answered with 401 and never becomes a
 * session. On success the resolved email is stashed in the handshake attributes
 * for {@link WebSocketPrincipalHandshakeHandler} to turn into the session
 * {@link java.security.Principal}.
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    /** Handshake-attribute key carrying the authenticated user's email. */
    static final String USER_EMAIL_ATTRIBUTE = "ws.userEmail";

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    public WebSocketAuthInterceptor(JwtUtils jwtUtils,
                                    UserDetailsServiceImpl userDetailsService,
                                    TokenBlacklistService tokenBlacklistService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String jwt = readJwtCookie(request);
        if (jwt == null || !jwtUtils.validateJwtToken(jwt) || tokenBlacklistService.isBlacklisted(jwt)) {
            log.debug("Rejecting WebSocket handshake: missing or invalid access token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            String username = jwtUtils.getUserNameFromJWTToken(jwt);
            UserDetailsImpl user = (UserDetailsImpl) userDetailsService.loadUserByUsername(username);
            attributes.put(USER_EMAIL_ATTRIBUTE, user.getEmail());
            return true;
        } catch (UsernameNotFoundException e) {
            log.debug("Rejecting WebSocket handshake: token references an unknown user");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String readJwtCookie(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return null;
        }
        Cookie[] cookies = servletRequest.getServletRequest().getCookies();
        if (cookies == null) {
            return null;
        }
        String cookieName = jwtUtils.getJwtCookieName();
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
