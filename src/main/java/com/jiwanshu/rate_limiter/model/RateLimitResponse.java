package com.jiwanshu.rate_limiter.model;

public class RateLimitResponse {

    private boolean allowed;
    private int remaining;
    private long resetAt;

    public RateLimitResponse() {
    }
    public RateLimitResponse(boolean allowed, int remaining, long resetAt) {
        this.allowed = allowed;
        this.remaining = remaining;
        this.resetAt = resetAt;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public int getRemaining() {
        return remaining;
    }

    public void setRemaining(int remaining) {
        this.remaining = remaining;
    }

    public long getResetAt() {
        return resetAt;
    }

    public void setResetAt(long resetAt) {
        this.resetAt = resetAt;
    }
}
