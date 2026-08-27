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

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitService.class);

    private final StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
                "local current = redis.call('INCR', KEYS[1]) " +
                "if current == 1 then " +
                "  redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
                "end " +
                "local ttl = redis.call('TTL', KEYS[1]) " +
                "return current * 100000 + math.max(ttl, 0)",
                Long.class
        );
    }

    public RateLimitResult checkLimit(String key, RateLimitRule rule) {
        Long result = redisTemplate.execute(
                RATE_LIMIT_SCRIPT, Collections.singletonList(key),
                String.valueOf(rule.getWindow().toSeconds()));

        if (result == null) {
            log.warn("Redis unavailable for rate limiting on key {}; failing open", key);
            return new RateLimitResult(
                    true, 0, rule.getLimit(), rule.getWindow().toSeconds());
        }

        long currentRequests = result / 100000;
        long retryAfterSeconds = result % 100000;
        if (retryAfterSeconds <= 0) {
            retryAfterSeconds = rule.getWindow().toSeconds();
        }
        long remainingRequests = Math.max(rule.getLimit() - currentRequests, 0);

        return new RateLimitResult(
                currentRequests <= rule.getLimit(),
                currentRequests,
                remainingRequests,
                retryAfterSeconds
        );
    }

}
