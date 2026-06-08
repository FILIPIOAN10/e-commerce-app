package com.ecommerce.project.ratelimit;


import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisRateLimitService {

    private final StringRedisTemplate redisTemplate;

    public RateLimitResult checkLimit(String key, RateLimitRule rule) {
        Long currentRequests = redisTemplate.opsForValue().increment(key);
        long safeCurrentRequests = currentRequests == null ? 1: currentRequests;
        if(safeCurrentRequests==1){
            redisTemplate.expire(key,rule.getWindow());
        }

        Long expireSeconds = redisTemplate.getExpire(key);
        long retryAfterSeconds = expireSeconds == null || expireSeconds<0
                ? rule.getWindow().toSeconds()
                : expireSeconds;
        long remainingRequests = Math.max(rule.getLimit() -safeCurrentRequests,0);
        return new RateLimitResult(
                safeCurrentRequests <= rule.getLimit(),
                safeCurrentRequests,
                remainingRequests,
                retryAfterSeconds
        );

    }

    private long getRetryAfterSeconds(String key, Duration window) {
        Long ttl = redisTemplate.getExpire(key);
        if (ttl == null || ttl < 0) {
            return window.toSeconds();
        }
        return ttl;
    }
}
