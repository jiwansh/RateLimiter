package com.jiwanshu.rate_limiter.controller;

import com.jiwanshu.rate_limiter.model.RateLimitRequest;
import com.jiwanshu.rate_limiter.model.RateLimitResponse;
import com.jiwanshu.rate_limiter.service.FixedWindowRateLimiterService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/ratelimit")
public class RateLimitController {

    private final FixedWindowRateLimiterService rateLimiterService;

    public RateLimitController(FixedWindowRateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/check")
    public ResponseEntity<RateLimitResponse> checkLimit(@Valid @RequestBody RateLimitRequest request){
        RateLimitResponse response = rateLimiterService.checkLimit(request);
        if(!response.isAllowed()){
            return ResponseEntity.status(429).body(response);
        }
        return ResponseEntity.ok(response);
    }
}
