package com.jiwanshu.rate_limiter;

import com.jiwanshu.rate_limiter.service.RateLimitResult;
import com.jiwanshu.rate_limiter.service.RedisSlidingWindowCounterStrategy;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class RedisSlidingWindowStressTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisSlidingWindowCounterStrategy strategy;

    @Test
    void testRedisSlidingWindowCountConcurrency() throws InterruptedException {
        String key = "stress:redis:user123";
        int limit = 10;
        int windowSeconds =60;
        int numberOfThreads = 100;

        //1.Flush test keys first
        redisTemplate.delete(redisTemplate.keys(key+"*"));
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(numberOfThreads);
        AtomicInteger allowedCount = new AtomicInteger(0);

        for(int i=0;i<numberOfThreads;i++){
            executor.submit(() -> {
                try{
                    startSignal.await();
                    RateLimitResult result = strategy.checkLimit(key,limit,windowSeconds);
                    if(result.isAllowed()){
                        allowedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }finally {
                    doneSignal.countDown();
                }
            });
        }
        startSignal.countDown(); // fire at once
        boolean completed = doneSignal.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "Threads did not complete in time — possible deadlock or connection pool exhaustion");
        executor.shutdown();

        System.out.println("Allowed: "+allowedCount.get()+"/Limit: "+limit );
        assertTrue(allowedCount.get() <= limit, "Race Condition: allowed exceeded limit");

    }

}
