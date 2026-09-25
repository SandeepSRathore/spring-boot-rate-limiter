package com.learning.ratelimiter.algorithm;

import java.time.Clock;
import java.util.List;

import com.learning.ratelimiter.rules.Algorithm;
import com.learning.ratelimiter.rules.RateLimitRule;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Cheapest algorithm (one counter per window), but a burst at the edge of two windows can let through
 * up to twice the limit within one window length.
 */
@Component
public class FixedWindowCounterRateLimiter extends RedisRateLimiter {

    private static final RedisScript<List<Long>> SCRIPT = script("fixed_window_counter.lua");

    public FixedWindowCounterRateLimiter(StringRedisTemplate redis, Clock clock) {
        super(redis, clock);
    }

    @Override
    public Algorithm algorithm() {
        return Algorithm.FIXED_WINDOW_COUNTER;
    }

    @Override
    public RateLimitDecision tryAcquire(RateLimitRule rule, String clientId) {
        RateLimitRule.RateLimit limit = rule.rateLimit();
        long now = clock.millis();
        long window = limit.windowMillis();
        long windowStart = now - now % window;
        return execute(SCRIPT, List.of(key(rule, clientId, ":fw:" + windowStart)), limit.requestsPerUnit(),
                limit.requestsPerUnit(), windowStart + window - now);
    }
}
