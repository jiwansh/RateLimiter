package com.jiwanshu.rate_limiter.model;

// used for internal result of rate limit check between interface & implementation
public class RateLimitResult {
    private final boolean allowed;
    private final int remaining;
    private final long resetAt;

    public RateLimitResult(boolean allowed, int remaining, long resetAt) {
        this.allowed = allowed;
        this.remaining = remaining;
        this.resetAt = resetAt;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public int getRemaining() {
        return remaining;
    }

    public long getResetAt() {
        return resetAt;
    }
}

