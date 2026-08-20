package com.jiwanshu.rate_limiter.service;

import com.jiwanshu.rate_limiter.model.RateLimitResult;
import com.jiwanshu.rate_limiter.model.TokenBucketState;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenBucketStrategy implements RateLimiterStrategy {

    private final Map<String, TokenBucketState> buckets = new ConcurrentHashMap<>();

    @Override
    public RateLimitResult checkLimit(String compositeKey, int limit, int windowSeconds) {
        // limit = capacity (max tokens)
        double refillRate = (double) limit /  windowSeconds;  //-> tokens per second

        long now = System.currentTimeMillis();

        TokenBucketState bucket = buckets.computeIfAbsent(compositeKey,
                k -> new TokenBucketState(limit, now));  // start full

        synchronized (bucket) {
            // 1. compute elapsed time since last refill (in seconds)
            double elapsedSeconds = (now- bucket.getLastRefillTimestamp())/1000.0;

            // 2. tokensToAdd = elapsed * refillRate
            double tokensToAdd = elapsedSeconds * refillRate;

            // 3. newTokens = min(capacity, bucket.getTokens() + tokensToAdd)
            double newTokens = Math.min(limit,bucket.getTokens() + tokensToAdd);

            // 4. if newTokens >= 1: deduct 1, update bucket, return ALLOWED
            if(newTokens>=1){
                bucket.setTokens(newTokens-1.0);
                bucket.setLastRefillTimestamp(now);

                int remaining = (int)Math.floor(newTokens -1.0);

                // since token fills at constant rate, and its alredy available
                // so returning 0 as reset at
                long resetAt = now/1000;
                return new RateLimitResult(true,remaining, resetAt);
            }
            //    else: update bucket (save refill progress), return DENIED
            else{
                bucket.setTokens(newTokens);
                bucket.setLastRefillTimestamp(now);

                long secondsUntilRetry = (long) Math.ceil((1.0-newTokens)/refillRate);
                long resetAt = (now/1000 )+  secondsUntilRetry;

                return new RateLimitResult(false,0,resetAt);
            }





        }
    }
}