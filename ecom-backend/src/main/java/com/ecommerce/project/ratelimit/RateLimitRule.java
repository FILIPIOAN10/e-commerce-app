package com.ecommerce.project.ratelimit;

import java.time.Duration;

public class RateLimitRule {
    private final String name;
    private final String method;
    private final String pathPattern;
    private final long limit;
    private final Duration window;
    private final RateLimitKeyType keyType;


    public RateLimitRule(String name, String method, String pathPattern, long limit, Duration window, RateLimitKeyType keyType) {
        this.name = name;
        this.method = method;
        this.pathPattern = pathPattern;
        this.limit = limit;
        this.window = window;
        this.keyType = keyType;
    }

    public String getName() {
        return name;
    }

    public String getMethod() {
        return method;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public long getLimit() {
        return limit;
    }

    public Duration getWindow() {
        return window;
    }

    public RateLimitKeyType getKeyType() {
        return keyType;
    }
}