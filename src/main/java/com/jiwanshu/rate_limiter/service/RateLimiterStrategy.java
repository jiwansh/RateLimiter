package com.jiwanshu.rate_limiter.service;

public interface RateLimiterStrategy {

    RateLimitResult checkLimit(String compositeKey, int limit, int windowSeconds);
}
