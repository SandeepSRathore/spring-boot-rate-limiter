package com.learning.ratelimiter.algorithm;

import java.time.Clock;
import java.util.List;

import com.learning.ratelimiter.rules.Algorithm;
import com.learning.ratelimiter.rules.RateLimitRule;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Processes requests at a fixed outflow rate. Bursts are queued (delayed) up to the bucket size and
 * dropped beyond it, so the downstream service sees a steady stream.
 */
@Component
public class LeakingBucketRateLimiter extends RedisRateLimiter {

    private static final RedisScript<List<Long>> SCRIPT = script("leaking_bucket.lua");

    public LeakingBucketRateLimiter(StringRedisTemplate redis, Clock clock) {
        super(redis, clock);
    }

    @Override
    public Algorithm algorithm() {
        return Algorithm.LEAKING_BUCKET;
    }

    @Override
    public RateLimitDecision tryAcquire(RateLimitRule rule, String clientId) {
        RateLimitRule.RateLimit limit = rule.rateLimit();
        // whole milliseconds keep the script's arithmetic exact (3/second leaks every 333 ms)
        long intervalMillis = Math.max(1, limit.windowMillis() / limit.requestsPerUnit());
        return execute(SCRIPT, List.of(key(rule, clientId, ":lb")), limit.bucketSize(),
                limit.bucketSize(), intervalMillis, clock.millis());
    }
}
