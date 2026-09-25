package com.learning.ratelimiter.algorithm;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.learning.ratelimiter.rules.Algorithm;
import com.learning.ratelimiter.rules.RateLimitRule;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** Exact limit in any rolling window, at the cost of storing a timestamp per request. */
@Component
public class SlidingWindowLogRateLimiter extends RedisRateLimiter {

    private static final RedisScript<List<Long>> SCRIPT = script("sliding_window_log.lua");

    public SlidingWindowLogRateLimiter(StringRedisTemplate redis, Clock clock) {
        super(redis, clock);
    }

    @Override
    public Algorithm algorithm() {
        return Algorithm.SLIDING_WINDOW_LOG;
    }

    @Override
    public RateLimitDecision tryAcquire(RateLimitRule rule, String clientId) {
        RateLimitRule.RateLimit limit = rule.rateLimit();
        // sorted set members must be unique, and two requests can share a millisecond
        String requestId = UUID.randomUUID().toString();
        return execute(SCRIPT, List.of(key(rule, clientId, ":swl")), limit.requestsPerUnit(),
                limit.requestsPerUnit(), limit.windowMillis(), clock.millis(), requestId);
    }
}
