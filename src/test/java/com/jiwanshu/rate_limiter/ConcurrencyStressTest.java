package com.jiwanshu.rate_limiter;

import com.jiwanshu.rate_limiter.service.FixedWindowStrategy;
import com.jiwanshu.rate_limiter.service.RateLimitResult;
import com.jiwanshu.rate_limiter.service.SlidingWindowCounterStrategy;
import com.jiwanshu.rate_limiter.service.SlidingWindowLogStrategy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConcurrencyStressTest {

    @Test
    void fixedWindow_shouldNotAllowMoreThanLimit_underConcurrentLoad() throws InterruptedException {

        FixedWindowStrategy strategy = new FixedWindowStrategy();

        int limit =10;
        int windowSeconds = 60;
        String key = "stress-test-key";

        int numberOfThreads = 100;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startSignal = new CountDownLatch(1); // act as a gate , hold threada til we say to go
        CountDownLatch doneSignal = new CountDownLatch(numberOfThreads); // count down as each thread finishes

        AtomicInteger allowedCount = new AtomicInteger(0); // thread safe - to count no of request is true;

        for(int i =0 ;i<numberOfThreads;i++){
            executor.submit(()->{
                try{
                    startSignal.await(); // block here till all release

                    RateLimitResult result = strategy.checkLimit(key,limit,windowSeconds);
                    if(result.isAllowed()) {
                        allowedCount.incrementAndGet();
                    }
                }
                catch(InterruptedException e){
                   /*sets the thread's "interrupted status" flag to true
                   It does not immediately kill or stop the thread;
                   it simply signals to the thread that something has requested its termination.
                   * */
                    Thread.currentThread().interrupt();
                }
                finally {
                    doneSignal.countDown(); // signal that this thread is finshed
                }
            });
        }
        startSignal.countDown(); // releases all threads at once
        doneSignal.await(); // block main thread till all finsished
        executor.shutdown();

        System.out.println("Total allowed: " + allowedCount.get() + " (limit was: " + limit + ")");
        assertTrue(allowedCount.get() <= limit,
                "Race condition detected! Allowed " + allowedCount.get() + " requests but limit was " + limit);
    }

    @Test
    void slidingWindowLog_shouldNotAllowMoreThanLimit_underConcurrentLoad() throws InterruptedException {

        SlidingWindowLogStrategy strategy = new SlidingWindowLogStrategy();

        int limit =10;
        int windowSeconds = 60;
        String key = "stress-test-key-log";

        int numberOfThreads = 100;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startSignal = new CountDownLatch(1); // act as a gate , hold threada til we say to go
        CountDownLatch doneSignal = new CountDownLatch(numberOfThreads); // count down as each thread finishes

        AtomicInteger allowedCount = new AtomicInteger(0); // thread safe - to count no of request is true;

        for(int i =0 ;i<numberOfThreads;i++){
            executor.submit(()->{
                try{
                    startSignal.await(); // block here till all release
                    RateLimitResult result = strategy.checkLimit(key,limit,windowSeconds);
                    if(result.isAllowed()) {
                        allowedCount.incrementAndGet();
                    }
                } catch(InterruptedException e){
                   /*sets the thread's "interrupted status" flag to true
                   It does not immediately kill or stop the thread;
                   it simply signals to the thread that something has requested its termination.
                   * */
                    Thread.currentThread().interrupt();
                }
                finally {
                    doneSignal.countDown(); // signal that this thread is finshed
                }
            });
        }
        startSignal.countDown(); // releases all threads at once
        doneSignal.await(); // block main thread till all finsished
        executor.shutdown();

        System.out.println("Sliding Window Log- Total allowed: " + allowedCount.get() + " (limit was: " + limit + ")");
        assertTrue(allowedCount.get() <= limit,
                "Race condition detected! Allowed " + allowedCount.get() + " requests but limit was " + limit);
    }

    @Test
    void slidingWindowCounter_shouldNotAllowMoreThanLimit_underConcurrentLoad() throws InterruptedException {

        SlidingWindowCounterStrategy strategy = new SlidingWindowCounterStrategy();

        int limit =10;
        int windowSeconds = 60;
        String key = "stress-test-key-counter";

        int numberOfThreads = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startSignal = new CountDownLatch(1); // act as a gate , hold threada til we say to go
        CountDownLatch doneSignal = new CountDownLatch(numberOfThreads); // count down as each thread finishes
        AtomicInteger allowedCount = new AtomicInteger(0); // thread safe - to count no of request is true;

        for(int i =0 ;i<numberOfThreads;i++){
            executor.submit(()->{
                try{
                    startSignal.await(); // block here till all release
                    RateLimitResult result = strategy.checkLimit(key,limit,windowSeconds);
                    if(result.isAllowed()) {
                        allowedCount.incrementAndGet();
                    }
                } catch(InterruptedException e){
                   /*sets the thread's "interrupted status" flag to true
                   It does not immediately kill or stop the thread;
                   it simply signals to the thread that something has requested its termination.
                   * */
                    Thread.currentThread().interrupt();
                }
                finally {
                    doneSignal.countDown(); // signal that this thread is finshed
                }
            });
        }
        startSignal.countDown(); // releases all threads at once
        doneSignal.await(); // block main thread till all finsished
        executor.shutdown();

        System.out.println("Total allowed: " + allowedCount.get() + " (limit was: " + limit + ")");
        assertTrue(allowedCount.get() <= limit,
                "Race condition detected! Allowed " + allowedCount.get() + " requests but limit was " + limit);
    }
}
