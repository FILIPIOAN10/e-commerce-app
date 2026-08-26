package com.ecommerce.project.security.redis;


import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "login_attempts:";

    private final StringRedisTemplate redisTemplate;

    public boolean isLocked(String username) {
        String key = KEY_PREFIX + username;
        String attempts = redisTemplate.opsForValue().get(key);
        if (attempts == null) {
            return false;
        }
        return Integer.parseInt(attempts) >= MAX_ATTEMPTS;
    }

    public void recordFailedAttempt(String username) {
        String key = KEY_PREFIX + username;
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, LOCK_DURATION);
        }
    }

    public void resetAttempts(String username) {
        redisTemplate.delete(KEY_PREFIX + username);
    }

    public void unlockUser(String username) {
        redisTemplate.delete(KEY_PREFIX + username);
    }

    public int getRemainingAttempts(String username) {
        String key = KEY_PREFIX + username;
        String attempts = redisTemplate.opsForValue().get(key);
        if (attempts == null) {
            return MAX_ATTEMPTS;
        }
        return Math.max(MAX_ATTEMPTS - Integer.parseInt(attempts), 0);
    }

    public long getLockTimeRemaining(String username) {
        String key = KEY_PREFIX + username;
        Long ttl = redisTemplate.getExpire(key);
        if (ttl == null || ttl < 0) {
            return 0;
        }
        return ttl;
    }
}