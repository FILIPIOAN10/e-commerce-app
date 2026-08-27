package com.ecommerce.project.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Turns the email stashed by {@link WebSocketAuthInterceptor} into the STOMP
 * session {@link Principal}. Its {@link Principal#getName() name} is what
 * {@code convertAndSendToUser(email, ...)} resolves against, so user-targeted
 * destinations only reach a browser once this has run.
 */
@Component
public class WebSocketPrincipalHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Object email = attributes.get(WebSocketAuthInterceptor.USER_EMAIL_ATTRIBUTE);
        return email != null ? new StompPrincipal(email.toString()) : null;
    }
}
