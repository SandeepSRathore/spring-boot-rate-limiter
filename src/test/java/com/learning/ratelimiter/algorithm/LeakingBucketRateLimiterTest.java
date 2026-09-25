package com.learning.ratelimiter.algorithm;

import java.time.Duration;

import com.learning.ratelimiter.rules.Algorithm;
import com.learning.ratelimiter.rules.RateLimitRule;
import com.learning.ratelimiter.rules.RateUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeakingBucketRateLimiterTest extends RedisAlgorithmTestSupport {

    // outflow 1 request per second, queue size 3
    private final RateLimitRule rule = rule(Algorithm.LEAKING_BUCKET, RateUnit.SECOND, 1, 3L);
    private final LeakingBucketRateLimiter limiter = new LeakingBucketRateLimiter(redis, clock);

    @Test
    void burstIsQueuedAndReleasedAtTheOutflowRate() {
        RateLimitDecision first = limiter.tryAcquire(rule, CLIENT);
        RateLimitDecision second = limiter.tryAcquire(rule, CLIENT);
        RateLimitDecision third = limiter.tryAcquire(rule, CLIENT);

        assertThat(first.allowed()).isTrue();
        assertThat(first.delayMillis()).isZero();
        assertThat(second.delayMillis()).isEqualTo(1_000);
        assertThat(third.delayMillis()).isEqualTo(2_000);
        assertThat(third.remaining()).isZero();
    }

    @Test
    void requestIsDroppedWhenTheQueueIsFull() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(rule, CLIENT);
        }

        RateLimitDecision rejected = limiter.tryAcquire(rule, CLIENT);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterMillis()).isEqualTo(1_000);

        clock.advance(Duration.ofSeconds(1));
        RateLimitDecision afterOneLeak = limiter.tryAcquire(rule, CLIENT);
        assertThat(afterOneLeak.allowed()).isTrue();
        assertThat(afterOneLeak.delayMillis()).isEqualTo(2_000);
    }

    @Test
    void emptiedBucketProcessesImmediatelyAgain() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(rule, CLIENT);
        }
        clock.advance(Duration.ofSeconds(10));

        assertThat(limiter.tryAcquire(rule, CLIENT).delayMillis()).isZero();
    }
}
