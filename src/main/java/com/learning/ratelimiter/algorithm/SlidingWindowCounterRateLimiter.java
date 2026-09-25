package com.learning.ratelimiter.algorithm;

import java.time.Clock;
import java.util.List;

import com.learning.ratelimiter.rules.Algorithm;
import com.learning.ratelimiter.rules.RateLimitRule;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Smooths the fixed window's edge bursts using only two counters, by assuming requests in the
 * previous window were evenly spread.
 */
@Component
public class SlidingWindowCounterRateLimiter extends RedisRateLimiter {

    private static final RedisScript<List<Long>> SCRIPT = script("sliding_window_counter.lua");

    public SlidingWindowCounterRateLimiter(StringRedisTemplate redis, Clock clock) {
        super(redis, clock);
    }

    @Override
    public Algorithm algorithm() {
        return Algorithm.SLIDING_WINDOW_COUNTER;
    }

    @Override
    public RateLimitDecision tryAcquire(RateLimitRule rule, String clientId) {
        RateLimitRule.RateLimit limit = rule.rateLimit();
        long now = clock.millis();
        long window = limit.windowMillis();
        long windowStart = now - now % window;
        List<String> keys = List.of(
                key(rule, clientId, ":swc:" + windowStart),
                key(rule, clientId, ":swc:" + (windowStart - window)));
        return execute(SCRIPT, keys, limit.requestsPerUnit(),
                limit.requestsPerUnit(), window, now - windowStart);
    }
}
