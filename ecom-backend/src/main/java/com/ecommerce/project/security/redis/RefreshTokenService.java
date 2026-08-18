package com.ecommerce.project.security.redis;

import com.ecommerce.project.exception.InvalidCredentialsException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    @Value("${spring.ecom.app.refreshTokenCookieName}")
    private String refreshTokenCookieName;

    @Value("${spring.app.refreshTokenExpirationMs:604800000}")
    private long refreshTokenExpirationMs;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private static final String KEY_PREFIX = "refresh:";
    private static final Set<String> NON_SECURE_PROFILES = Set.of("dev", "ci");

    public String createRefreshToken(String username) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                KEY_PREFIX + token,
                username,
                Duration.ofMillis(refreshTokenExpirationMs)
        );
        return token;
    }

    public String validateAndGetUsername(String token) {
        String username = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        if (username == null) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }
        return username;
    }

    public String rotate(String oldToken) {
        String username = validateAndGetUsername(oldToken);
        delete(oldToken);
        return createRefreshToken(username);
    }

    public void delete(String token) {
        if (token != null && !token.isBlank()) {
            redisTemplate.delete(KEY_PREFIX + token);
        }
    }

    public String getRefreshTokenFromCookies(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, refreshTokenCookieName);
        return cookie != null ? cookie.getValue() : null;
    }

    public ResponseCookie generateRefreshCookie(String token) {
        return ResponseCookie.from(refreshTokenCookieName, token)
                .path("/api/auth")
                .maxAge(refreshTokenExpirationMs / 1000)
                .httpOnly(true)
                .secure(!NON_SECURE_PROFILES.contains(activeProfile))
                .sameSite("Lax")
                .build();
    }

    public ResponseCookie getCleanRefreshCookie() {
        return ResponseCookie.from(refreshTokenCookieName, "")
                .path("/api/auth")
                .maxAge(0)
                .httpOnly(true)
                .secure(!NON_SECURE_PROFILES.contains(activeProfile))
                .sameSite("Lax")
                .build();
    }
}
