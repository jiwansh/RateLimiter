package com.jiwanshu.rate_limiter.service;

import com.jiwanshu.rate_limiter.model.RateLimitRequest;
import com.jiwanshu.rate_limiter.model.RateLimitResponse;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FixedWindowRateLimiterService {

    // key = "userId:endpoint", value = the current window's state for that key
    private final ConcurrentHashMap<String,WindowState> store = new ConcurrentHashMap<>();

    public RateLimitResponse checkLimit(RateLimitRequest request){

        String compositeKey = request.getKey()+":"+ request.getEndpoint();
        long nowSeconds = System.currentTimeMillis() / 1000;

        //calculates curr window start time
        long currWindowStart = (nowSeconds / request.getWindowSeconds()) * request.getWindowSeconds();

        //updates or initializes a rate-limiting track record in a thread-safe map (store)
        // using a atomic atomic approach
        WindowState state = store.compute(compositeKey,(k,existing)->{
            if(existing == null || existing.windowStart != currWindowStart){
                return new WindowState(currWindowStart,new AtomicInteger());
            }
            return existing;
        });
        int newCount = state.count.incrementAndGet();
        long resetAt =  currWindowStart + request.getWindowSeconds();

        if(newCount > request.getLimit()){
            return new RateLimitResponse(false, 0, resetAt);
        }

        int remaining = request.getLimit()- newCount;
        return new RateLimitResponse(true, remaining, resetAt);
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
