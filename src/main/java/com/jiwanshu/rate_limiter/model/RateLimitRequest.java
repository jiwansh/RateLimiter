package com.jiwanshu.rate_limiter.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class RateLimitRequest {
    public RateLimitRequest() {
    }

    @NotBlank(message = "algorithm is required")
    private String algorithm;

    @NotBlank(message = "key is required")
    private String key;

    @NotBlank(message = "endpoint needed")
    private String endpoint;

    @Positive(message = "limit can not be negative")
    private int limit;

    @Positive(message = "time can not be negative")
    private int windowSeconds;

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }


}
