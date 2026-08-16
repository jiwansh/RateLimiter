package com.jiwanshu.rate_limiter.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed sliding window counter — NAIVE first version.
 * Uses separate GET/INCR/EXPIRE calls. This has a real race condition
 * across multiple app instances hitting Redis concurrently — we'll
 * prove it, then fix it with a Lua script.
 */

@Component
public class RedisNaiveSlidingWindowCounterStrategy implements RateLimiterStrategy {

    private final StringRedisTemplate redisTemplate;

    @Autowired
    public RedisNaiveSlidingWindowCounterStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitResult checkLimit(String compositeKey, int limit, int windowSeconds) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        long currentWindowStart = (nowSeconds/windowSeconds)*windowSeconds;
        long previousWindowStart = currentWindowStart-windowSeconds;

        // Redis keys are window-labeled — old ones just expire naturally via TTL,
        // no manual "shift" logic needed like our in-memory version
        String currentWindowKey = compositeKey+":"+currentWindowStart;
        String previousWindowKey = compositeKey+":"+previousWindowStart;

        // step 1: read both counts
        String currentCountStr = redisTemplate.opsForValue().get(currentWindowKey);
        String previousCountStr = redisTemplate.opsForValue().get(previousWindowKey);

        int currentCount = currentCountStr == null? 0:Integer.parseInt(currentCountStr);
        int previousCount = previousCountStr == null? 0:Integer.parseInt(previousCountStr);

        long timeElapsedInCurrWindow = nowSeconds - currentWindowStart;
        double overlapPercentage = 1.0 - ((double)timeElapsedInCurrWindow / windowSeconds);
        double estimatedCount = (overlapPercentage*previousCount)+currentCount;

        long resetAt = currentWindowStart+windowSeconds;

        if(estimatedCount>=limit){
            return new RateLimitResult(false,0,resetAt);
        }

        // STEP 2: increment — ANOTHER separate network call, AFTER the check above
        // gap between step 1's read and this write is exactly the race window
        Long newCount = redisTemplate.opsForValue().increment(currentWindowKey);

        // set expiry so old window keys clean themselves up automatically
        // (only needs to be set once, but setting every time is simpler for now — harmless, just slightly wasteful)
        redisTemplate.expire(currentWindowKey,windowSeconds*2L,java.util.concurrent.TimeUnit.SECONDS);

        int remaining = (int) Math.floor(limit- estimatedCount-1);
        return new RateLimitResult(true,Math.max(remaining,0),resetAt);
    }
}
