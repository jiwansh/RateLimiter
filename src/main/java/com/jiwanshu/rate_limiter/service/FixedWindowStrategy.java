package com.jiwanshu.rate_limiter.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FixedWindowStrategy implements RateLimiterStrategy {

    // key = "userId:endpoint", value = the current window's state for that key
    private final ConcurrentHashMap<String,WindowState> store = new ConcurrentHashMap<>();

    @Override
    public RateLimitResult checkLimit(String compositeKey, int limit, int windowSeconds) {

        long nowSeconds = System.currentTimeMillis() / 1000;
        
        //calculates curr window start time
        long currWindowStart = (nowSeconds / windowSeconds) * windowSeconds;

        //updates or initializes a rate-limiting track record in a thread-safe map (store)
        // using a atomic atomic approach
        WindowState state = store.compute(compositeKey,(k,existing)->{
            if(existing == null || existing.windowStart != currWindowStart){
                return new WindowState(currWindowStart,new AtomicInteger());
            }
            return existing;
        });
        int newCount = state.count.incrementAndGet();
        long resetAt =  currWindowStart + windowSeconds;

        if(newCount > limit){
            return new RateLimitResult(false, 0, resetAt);
        }

        int remaining = limit- newCount;
        return new RateLimitResult(true, remaining, resetAt);
    }

    private static class WindowState{
        final long windowStart;
        final AtomicInteger count;

        WindowState(long windowStart,AtomicInteger count){
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
