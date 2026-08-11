package com.jiwanshu.rate_limiter.service;

/*
* using approximations with how much percentage of time user is in curr window
* then calculation remaing percentage for prev window
* add count of both window
* return it
* */

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SlidingWindowCounterStrategy implements RateLimiterStrategy{

    private final ConcurrentHashMap<String,TwoWindowState>  store = new ConcurrentHashMap<>();

    @Override
    public RateLimitResult checkLimit(String compositeKey, int limit, int windowSeconds) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        long currentWindowStart = (nowSeconds /windowSeconds)*windowSeconds;
        long previousWindowStart = currentWindowStart- windowSeconds;

        TwoWindowState state = store.compute(compositeKey, (k, existing)->{
            // fresh start
            if(existing == null){
                return new TwoWindowState(currentWindowStart, new AtomicInteger(0), new AtomicInteger(0));
            }
            // if its already in curr window
            if(existing.currentWindowStart == currentWindowStart){
                // no changes needed
                return existing;
            }
            // if existing has prevStart then make prev -> curr & curr to 0;
            if(existing.currentWindowStart== previousWindowStart){
                return new TwoWindowState(currentWindowStart,new AtomicInteger(0),existing.currentCount);
            }
            // more than one window has passed - reset and start fresh
            return new TwoWindowState(currentWindowStart, new AtomicInteger(0), new AtomicInteger(0));
        });

        long elapsedInCurrentWindow = nowSeconds - currentWindowStart;
        double overlapPercentage = 1.0 - ((double) elapsedInCurrentWindow/windowSeconds);
        double estimatedCount = (overlapPercentage*state.previousCount.get()) + state.currentCount.get();

        long resetAt = currentWindowStart + windowSeconds;
        if(estimatedCount>=limit){
            return new RateLimitResult(false, 0, resetAt);
        }

        state.currentCount.incrementAndGet();
        int remaining = (int)Math.floor(limit - estimatedCount-1); // 1 for this curr request
        return new RateLimitResult(true,Math.max(remaining,0),resetAt);

    }
    private static class TwoWindowState{
        final long currentWindowStart;
        final AtomicInteger currentCount;
        final AtomicInteger previousCount;

        TwoWindowState(long currentWindowStart, AtomicInteger currentCount, AtomicInteger previousCount) {
            this.currentWindowStart = currentWindowStart;
            this.currentCount = currentCount;
            this.previousCount = previousCount;
        }
    }
}
