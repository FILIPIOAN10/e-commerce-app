package com.ecommerce.project.config;

import com.ecommerce.project.security.WebSocketAuthInterceptor;
import com.ecommerce.project.security.WebSocketPrincipalHandshakeHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;
    private final WebSocketPrincipalHandshakeHandler handshakeHandler;

    @Value("${frontend.url}")
    private String frontendUrl;

    public WebSocketConfig(WebSocketAuthInterceptor authInterceptor,
                           WebSocketPrincipalHandshakeHandler handshakeHandler) {
        this.authInterceptor = authInterceptor;
        this.handshakeHandler = handshakeHandler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // /topic  — broadcast destinations
        // /queue  — per-user destinations; convertAndSendToUser(...) relays through
        //           here, so without /queue registered the broker silently drops
        //           every user notification.
        config.enableSimpleBroker("/topic", "/queue");
        // client -> server
        config.setApplicationDestinationPrefixes("/app");
        // /user/** is rewritten per-session against the handshake Principal
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Under /api on purpose: the access-token cookie is scoped to path /api
        // (JwtUtils), so the SockJS handshake only carries it when the endpoint
        // lives there too. Anywhere else the handshake arrives with no cookie and
        // WebSocketAuthInterceptor can only answer 401.
        registry.addEndpoint("/api/ws-notifications")
                .setAllowedOrigins(frontendUrl)
                .addInterceptors(authInterceptor)
                .setHandshakeHandler(handshakeHandler)
                .withSockJS();
    }
}
