package com.learning.ratelimiter.algorithm;

import java.time.Duration;

import com.learning.ratelimiter.rules.Algorithm;
import com.learning.ratelimiter.rules.RateLimitRule;
import com.learning.ratelimiter.rules.RateUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketRateLimiterTest extends RedisAlgorithmTestSupport {

    // the book's example: bucket size 4, refill rate 4 per minute
    private final RateLimitRule rule = rule(Algorithm.TOKEN_BUCKET, RateUnit.MINUTE, 4, 4L);
    private final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(redis, clock);

    @Test
    void fullBucketAllowsABurstUpToItsSizeThenRejects() {
        for (int remaining = 3; remaining >= 0; remaining--) {
            RateLimitDecision decision = limiter.tryAcquire(rule, CLIENT);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.remaining()).isEqualTo(remaining);
        }

        RateLimitDecision rejected = limiter.tryAcquire(rule, CLIENT);
        assertThat(rejected.allowed()).isFalse();
        // 4 tokens per minute -> the next token arrives after 15 s
        assertThat(rejected.retryAfterMillis()).isEqualTo(15_000);
    }

    @Test
    void tokensAreRefilledOverTime() {
        drain();

        clock.advance(Duration.ofSeconds(14));
        assertThat(limiter.tryAcquire(rule, CLIENT).allowed()).isFalse();

        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire(rule, CLIENT).allowed()).isTrue();
        assertThat(limiter.tryAcquire(rule, CLIENT).allowed()).isFalse();
    }

    @Test
    void refillNeverExceedsTheBucketSize() {
        drain();
        clock.advance(Duration.ofHours(1));

        for (int i = 0; i < 4; i++) {
            assertThat(limiter.tryAcquire(rule, CLIENT).allowed()).isTrue();
        }
        assertThat(limiter.tryAcquire(rule, CLIENT).allowed()).isFalse();
    }

    @Test
    void eachClientHasItsOwnBucket() {
        drain();
        assertThat(limiter.tryAcquire(rule, "ip:10.0.0.2").allowed()).isTrue();
    }

    private void drain() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire(rule, CLIENT);
        }
    }
}
