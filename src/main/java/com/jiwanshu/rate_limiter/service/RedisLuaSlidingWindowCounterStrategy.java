package com.jiwanshu.rate_limiter.service;

import com.jiwanshu.rate_limiter.model.RateLimitResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisLuaSlidingWindowCounterStrategy implements RateLimiterStrategy {

    private final StringRedisTemplate redisTemplate;

    private final RedisScript<List> slidingWindowScript=
            RedisScript.of(new ClassPathResource("scripts/sliding_window_counter.lua"),List.class);

    public RedisLuaSlidingWindowCounterStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    @Override
    public RateLimitResult checkLimit(String compositeKey, int limit, int windowSeconds) {

        long nowSeconds = System.currentTimeMillis() / 1000; // curren titme in seconds
        long currentWindowStart = (nowSeconds/windowSeconds)*windowSeconds;
        long previousWindowStart = currentWindowStart-windowSeconds;

        // Redis keys are window-labeled — old ones just expire naturally via TTL,
        // no manual "shift" logic needed like our in-memory version
        String currentWindowKey = compositeKey+":"+currentWindowStart;
        String previousWindowKey = compositeKey+":"+previousWindowStart;


        long timeElapsedInCurrWindow = nowSeconds - currentWindowStart;
        double overlapPercentage = 1.0 - ((double)timeElapsedInCurrWindow / windowSeconds);

        List<String> keys = List.of(previousWindowKey,currentWindowKey);

        long resetAt = currentWindowStart+windowSeconds;

        List<Object> result;
        try{
            result= redisTemplate.execute(
                    slidingWindowScript,
                    keys,
                    String.valueOf(limit),
                    String.valueOf(overlapPercentage),
                    String.valueOf(windowSeconds)
            );
        } catch (Exception e) {
            // choosing fail- open over fail-close
            return new RateLimitResult(true, limit, resetAt);
        }

        boolean allowed = "1".equals(String.valueOf(result.get(0)));
        double estimatedCount=Double.parseDouble(String.valueOf(result.get(1)));
        int remaining = (int) Math.floor(limit- estimatedCount-1);

        return new RateLimitResult(allowed,Math.max(remaining,0),resetAt);
    }
}

