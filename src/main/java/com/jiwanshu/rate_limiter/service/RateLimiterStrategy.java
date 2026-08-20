package com.jiwanshu.rate_limiter.service;

import com.jiwanshu.rate_limiter.model.RateLimitResult;

public interface RateLimiterStrategy {

    RateLimitResult checkLimit(String compositeKey, int limit, int windowSeconds);
}
