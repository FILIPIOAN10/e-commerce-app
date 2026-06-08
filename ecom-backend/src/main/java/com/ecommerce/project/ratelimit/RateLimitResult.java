package com.ecommerce.project.ratelimit;



public class RateLimitResult {
    private final boolean allowed;
    private final long currentRequests;
    private final long remainingRequests;
    private final long retryAfterSeconds;

    public RateLimitResult(boolean allowed, long currentRequests, long remainingRequests, long retryAfterSeconds) {
        this.allowed = allowed;
        this.currentRequests = currentRequests;
        this.remainingRequests = remainingRequests;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public long getCurrentRequests() {
        return currentRequests;
    }

    public long getRemainingRequests() {
        return remainingRequests;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}