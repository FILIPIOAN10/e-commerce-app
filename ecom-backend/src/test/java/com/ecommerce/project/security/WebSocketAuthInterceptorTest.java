package com.ecommerce.project.security;

import com.ecommerce.project.security.jwt.JwtUtils;
import com.ecommerce.project.security.redis.TokenBlacklistService;
import com.ecommerce.project.security.services.UserDetailsImpl;
import com.ecommerce.project.security.services.UserDetailsServiceImpl;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WebSocketAuthInterceptor — handshake authentication")
class WebSocketAuthInterceptorTest {

    private static final String COOKIE_NAME = "springBootEcom";

    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private UserDetailsServiceImpl userDetailsService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private WebSocketAuthInterceptor interceptor;
    private MockHttpServletResponse rawResponse;
    private ServerHttpResponse response;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthInterceptor(jwtUtils, userDetailsService, tokenBlacklistService);
        when(jwtUtils.getJwtCookieName()).thenReturn(COOKIE_NAME);
        rawResponse = new MockHttpServletResponse();
        response = new ServletServerHttpResponse(rawResponse);
        attributes = new HashMap<>();
    }

    private ServerHttpRequest requestWithCookie(String value) {
        MockHttpServletRequest raw = new MockHttpServletRequest("GET", "/ws-notifications");
        if (value != null) {
            raw.setCookies(new Cookie(COOKIE_NAME, value));
        }
        return new ServletServerHttpRequest(raw);
    }

    @Test
    @DisplayName("rejects a handshake that carries no auth cookie")
    void rejectsWhenNoCookie() {
        boolean allowed = interceptor.beforeHandshake(requestWithCookie(null), response, null, attributes);

        assertThat(allowed).isFalse();
        assertThat(rawResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("rejects a handshake whose token fails validation")
    void rejectsWhenTokenInvalid() {
        when(jwtUtils.validateJwtToken("bad-token")).thenReturn(false);

        boolean allowed = interceptor.beforeHandshake(requestWithCookie("bad-token"), response, null, attributes);

        assertThat(allowed).isFalse();
        assertThat(rawResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("rejects a handshake whose token has been blacklisted")
    void rejectsWhenTokenBlacklisted() {
        when(jwtUtils.validateJwtToken("revoked")).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted("revoked")).thenReturn(true);

        boolean allowed = interceptor.beforeHandshake(requestWithCookie("revoked"), response, null, attributes);

        assertThat(allowed).isFalse();
        assertThat(rawResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("rejects a handshake whose token references an unknown user")
    void rejectsWhenUserUnknown() {
        when(jwtUtils.validateJwtToken("orphan")).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted("orphan")).thenReturn(false);
        when(jwtUtils.getUserNameFromJWTToken("orphan")).thenReturn("ghost");
        when(userDetailsService.loadUserByUsername("ghost"))
                .thenThrow(new UsernameNotFoundException("User Not Found with username ghost"));

        boolean allowed = interceptor.beforeHandshake(requestWithCookie("orphan"), response, null, attributes);

        assertThat(allowed).isFalse();
        assertThat(rawResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("accepts a valid handshake and stashes the user's email for the Principal")
    void acceptsValidHandshake() {
        UserDetailsImpl user = new UserDetailsImpl(1L, "admin", "admin@example.com", "hash", List.of());
        when(jwtUtils.validateJwtToken("good")).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted("good")).thenReturn(false);
        when(jwtUtils.getUserNameFromJWTToken("good")).thenReturn("admin");
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(user);

        boolean allowed = interceptor.beforeHandshake(requestWithCookie("good"), response, null, attributes);

        assertThat(allowed).isTrue();
        assertThat(rawResponse.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(attributes).containsEntry(WebSocketAuthInterceptor.USER_EMAIL_ATTRIBUTE, "admin@example.com");
    }

    @Test
    @DisplayName("Principal handshake handler names the session by the stashed email")
    void handshakeHandlerBuildsPrincipalFromEmail() {
        WebSocketPrincipalHandshakeHandler handler = new WebSocketPrincipalHandshakeHandler();
        attributes.put(WebSocketAuthInterceptor.USER_EMAIL_ATTRIBUTE, "admin@example.com");

        var principal = handler.determineUser(requestWithCookie("good"), null, attributes);

        assertThat(principal).isNotNull();
        assertThat(principal.getName()).isEqualTo("admin@example.com");
    }
}
