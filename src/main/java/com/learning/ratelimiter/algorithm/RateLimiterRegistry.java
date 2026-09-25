package com.learning.ratelimiter.algorithm;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.learning.ratelimiter.rules.Algorithm;

import org.springframework.stereotype.Component;

@Component
public class RateLimiterRegistry {

    private final Map<Algorithm, RateLimiter> limiters = new EnumMap<>(Algorithm.class);

    public RateLimiterRegistry(List<RateLimiter> limiters) {
        limiters.forEach(limiter -> this.limiters.put(limiter.algorithm(), limiter));
    }

    public RateLimiter get(Algorithm algorithm) {
        RateLimiter limiter = limiters.get(algorithm);
        if (limiter == null) {
            throw new IllegalStateException("No rate limiter implementation for " + algorithm);
        }
        return limiter;
    }
}
