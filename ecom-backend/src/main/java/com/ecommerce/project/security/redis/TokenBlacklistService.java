package com.ecommerce.project.security.redis;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "blacklist:";

    public void blacklistToken(String token, Claims claims) {
        long now = System.currentTimeMillis();
        long expiration = claims.getExpiration().getTime();
        long ttlSeconds = (expiration - now) / 1000;

        if (ttlSeconds > 0) {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + token,
                    "revoked",
                    Duration.ofSeconds(ttlSeconds)
            );
        }
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }
}
