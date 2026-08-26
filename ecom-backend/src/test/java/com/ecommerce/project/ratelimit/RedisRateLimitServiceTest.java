package com.ecommerce.project.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RedisRateLimitService tests")
class RedisRateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisRateLimitService service;

    private final RateLimitRule rule = new RateLimitRule("test", "GET", "/api/test", 5, Duration.ofSeconds(60), RateLimitKeyType.IP);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new RedisRateLimitService(redisTemplate);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(100060L);
    }

    @Test
    @DisplayName("first request is allowed with full remaining")
    void firstRequest_allowed() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(100060L); // current=1, ttl=60

        RateLimitResult result = service.checkLimit("rate_limit:test:1.2.3.4", rule);

        assertTrue(result.isAllowed());
        assertEquals(1, result.getCurrentRequests());
        assertEquals(4, result.getRemainingRequests());
        assertEquals(60, result.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("request at limit boundary is allowed")
    void atLimit_allowed() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(500045L); // current=5, ttl=45

        RateLimitResult result = service.checkLimit("rate_limit:test:1.2.3.4", rule);

        assertTrue(result.isAllowed());
        assertEquals(5, result.getCurrentRequests());
        assertEquals(0, result.getRemainingRequests());
    }

    @Test
    @DisplayName("request over limit is denied")
    void overLimit_denied() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(600030L); // current=6, ttl=30

        RateLimitResult result = service.checkLimit("rate_limit:test:1.2.3.4", rule);

        assertFalse(result.isAllowed());
        assertEquals(6, result.getCurrentRequests());
        assertEquals(0, result.getRemainingRequests());
        assertEquals(30, result.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("fails open when Redis is unavailable (null result)")
    void redisUnavailable_failsOpen() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(null);

        RateLimitResult result = service.checkLimit("rate_limit:test:1.2.3.4", rule);

        assertTrue(result.isAllowed());
        assertEquals(0, result.getCurrentRequests());
        assertEquals(5, result.getRemainingRequests());
        assertEquals(60, result.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("single Lua roundtrip — no separate getExpire call")
    void singleLuaRoundtrip() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(100060L);

        service.checkLimit("rate_limit:test:1.2.3.4", rule);

        // Only one Redis call should have been made (the Lua script)
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any(Object[].class));
        verify(redisTemplate, never()).getExpire(anyString());
    }
}
