package com.ecommerce.project.security.redis;

import com.ecommerce.project.security.jwt.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final StringRedisTemplate redisTemplate;
    private final JwtUtils jwtUtils;

    @Value("${app.password-reset.token-expiration-minutes:15}")
    private long tokenExpirationMinutes;

    private static final String KEY_PREFIX = "reset_token:";
    private static final String RESET_PURPOSE = "password_reset";

    public String generateResetToken(String email) {
        String tokenId = UUID.randomUUID().toString();
        long expirationMs = tokenExpirationMinutes * 60 * 1000L;

        String token = jwtUtils.generateToken(email + "|" + tokenId, RESET_PURPOSE, expirationMs);

        redisTemplate.opsForValue().set(
                KEY_PREFIX + email,
                tokenId,
                Duration.ofMinutes(tokenExpirationMinutes)
        );

        return token;
    }

    public boolean validateResetToken(String token, String email) {
        try {
            Claims claims = jwtUtils.parseClaims(token);
            String subject = claims.getSubject();
            String purpose = claims.get("purpose", String.class);

            if (!RESET_PURPOSE.equals(purpose)) {
                return false;
            }

            String[] parts = subject.split("\\|");
            if (parts.length != 2) {
                return false;
            }

            String storedTokenId = redisTemplate.opsForValue().get(KEY_PREFIX + email);
            if (storedTokenId == null || !storedTokenId.equals(parts[1])) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extractEmailFromToken(String token) {
        Claims claims = jwtUtils.parseClaims(token);
        String subject = claims.getSubject();
        return subject.split("\\|")[0];
    }

    public void invalidateToken(String email) {
        redisTemplate.delete(KEY_PREFIX + email);
    }
}