package com.ecommerce.project.ratelimit;


import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.Collections;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisRateLimitService {

    private final StringRedisTemplate redisTemplate;

    private static final String LUA_SCRIPT =
            "local current = redis.call('INCR', KEYS[1]) " +
                    "if current == 1 then " +
                    "  redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
                    "end " +
                    "return current";

    public RateLimitResult checkLimit(String key, RateLimitRule rule) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
        Long currentRequests = redisTemplate.execute(
                script, Collections.singletonList(key),
                String.valueOf(rule.getWindow().toSeconds()));

        long safeCurrentRequests = currentRequests == null ? 1 : currentRequests;
        Long expireSeconds = redisTemplate.getExpire(key);
        long retryAfterSeconds = expireSeconds == null || expireSeconds < 0
                ? rule.getWindow().toSeconds()
                : expireSeconds;
        long remainingRequests = Math.max(rule.getLimit() - safeCurrentRequests, 0);

        return new RateLimitResult(
                safeCurrentRequests <= rule.getLimit(),
                safeCurrentRequests,
                remainingRequests,
                retryAfterSeconds
        );
    }

}
