package com.learning.ratelimiter.algorithm;

import com.learning.ratelimiter.rules.Algorithm;
import com.learning.ratelimiter.rules.RateLimitRule;

public interface RateLimiter {

    Algorithm algorithm();

    /**
     * Records one request from {@code clientId} under {@code rule} and decides whether it may go through.
     * Must be atomic across all application instances sharing the same store.
     */
    RateLimitDecision tryAcquire(RateLimitRule rule, String clientId);
}
