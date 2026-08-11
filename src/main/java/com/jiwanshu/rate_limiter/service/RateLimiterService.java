package com.jiwanshu.rate_limiter.service;

import org.springframework.stereotype.Service;

import java.util.Map;
// central class to choose among different algorithms

@Service
public class RateLimiterService {

    private final Map<String,RateLimiterStrategy> strategies;

    public RateLimiterService(FixedWindowStrategy fixedWindowStrategy,
                              SlidingWindowLogStrategy slidingWindowLogStrategy,
                              SlidingWindowCounterStrategy slidingWindowCounterStrategy) {
        this.strategies = Map.of("fixed_window", fixedWindowStrategy,
                "sliding_window_log",slidingWindowLogStrategy,
                "sliding_window_counter",slidingWindowCounterStrategy);
    }

    public RateLimitResult checkLimit(String algorithm, String compositeKey, int limit, int windowSeconds){
        RateLimiterStrategy strategy = this.strategies.get(algorithm);

        if(strategy == null){
            throw new IllegalArgumentException("unknown algorithm"+algorithm);
        }

        return strategy.checkLimit(compositeKey,limit,windowSeconds);
    }
}
