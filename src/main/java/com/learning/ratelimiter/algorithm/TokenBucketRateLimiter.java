package com.learning.ratelimiter.algorithm;

import java.time.Clock;
import java.util.List;

import com.learning.ratelimiter.rules.Algorithm;
import com.learning.ratelimiter.rules.RateLimitRule;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** Allows bursts up to the bucket size, then {@code requests-per-unit} on average. */
@Component
public class TokenBucketRateLimiter extends RedisRateLimiter {

    private static final RedisScript<List<Long>> SCRIPT = script("token_bucket.lua");

    public TokenBucketRateLimiter(StringRedisTemplate redis, Clock clock) {
        super(redis, clock);
    }

    @Override
    public Algorithm algorithm() {
        return Algorithm.TOKEN_BUCKET;
    }

    @Override
    public RateLimitDecision tryAcquire(RateLimitRule rule, String clientId) {
        RateLimitRule.RateLimit limit = rule.rateLimit();
        return execute(SCRIPT, List.of(key(rule, clientId, ":tb")), limit.bucketSize(),
                limit.bucketSize(), limit.requestsPerUnit(), limit.windowMillis(), clock.millis());
    }
}
