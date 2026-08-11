package com.jiwanshu.rate_limiter.service;

import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

/*
* Sliding window log - stores every request timestamp per key
* precise but more memory per key
* */
@Component
public class SlidingWindowLogStrategy implements  RateLimiterStrategy{

    private final ConcurrentHashMap<String, LinkedList<Long>> store = new ConcurrentHashMap<>();

    @Override
    public RateLimitResult checkLimit(String compositeKey, int limit, int windowSeconds) {
        long nowMills = System.currentTimeMillis();
        long windowStartMills = nowMills - (windowSeconds * 1000L);

        LinkedList<Long> timestamps = store.computeIfAbsent(compositeKey, k -> new LinkedList<>());
        // using locks as linkedList is not thread safe
        synchronized (timestamps) {
            // case1 : removing old timestamps
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStartMills) {
                timestamps.pollFirst();
            }
            long resetAt = (nowMills + (windowSeconds * 1000L)) / 1000;

            if (timestamps.size() > limit) {
                return new RateLimitResult(false, 0, resetAt);
            }

            timestamps.add(nowMills);
            int remaining = limit - timestamps.size();

            return new RateLimitResult(true, remaining, resetAt);

        }
    }
}
