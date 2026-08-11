package com.jiwanshu.rate_limiter.controller;

import com.jiwanshu.rate_limiter.model.RateLimitRequest;
import com.jiwanshu.rate_limiter.model.RateLimitResponse;
import com.jiwanshu.rate_limiter.service.FixedWindowStrategy;
import com.jiwanshu.rate_limiter.service.RateLimitResult;
import com.jiwanshu.rate_limiter.service.RateLimiterService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/ratelimit")
public class RateLimitController {

    private final RateLimiterService rateLimiterService;

    public RateLimitController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/check")
    public ResponseEntity<RateLimitResponse> checkLimit(@Valid @RequestBody RateLimitRequest request){
        // build composite key here
        String compositeKey = request.getKey()+":"+request.getEndpoint();

        RateLimitResult result = rateLimiterService.checkLimit(
                request.getAlgorithm(),
                compositeKey,
                request.getLimit(),
                request.getWindowSeconds()
        );

        // convert result to DTO respose

        RateLimitResponse response = new RateLimitResponse(
                result.isAllowed(),
                result.getRemaining(),
                result.getResetAt()
        );

        if(!response.isAllowed()){
            return ResponseEntity.status(429).body(response);
        }
        return ResponseEntity.ok(response);
    }
}
