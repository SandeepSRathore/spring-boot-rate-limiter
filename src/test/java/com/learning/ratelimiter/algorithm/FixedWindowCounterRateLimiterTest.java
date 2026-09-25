package com.learning.ratelimiter.algorithm;

import java.time.Duration;

import com.learning.ratelimiter.rules.Algorithm;
import com.learning.ratelimiter.rules.RateLimitRule;
import com.learning.ratelimiter.rules.RateUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowCounterRateLimiterTest extends RedisAlgorithmTestSupport {

    // the book's example: 5 requests per minute
    private final RateLimitRule rule = rule(Algorithm.FIXED_WINDOW_COUNTER, RateUnit.MINUTE, 5, null);
    private final FixedWindowCounterRateLimiter limiter = new FixedWindowCounterRateLimiter(redis, clock);

    @Test
    void rejectsOnceTheWindowCounterReachesTheLimit() {
        clock.advance(Duration.ofSeconds(20));
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(rule, CLIENT).allowed()).isTrue();
        }

        RateLimitDecision rejected = limiter.tryAcquire(rule, CLIENT);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterMillis()).isEqualTo(40_000);

        clock.advance(Duration.ofSeconds(40));
        assertThat(limiter.tryAcquire(rule, CLIENT).allowed()).isTrue();
    }

    @Test
    void burstAtTheWindowEdgeLetsTwiceTheLimitThrough() {
        // the weakness the book points out: 5 requests between 1:00:30-1:01:00 and 5 more between 1:01:00-1:01:30
        clock.set(ONE_O_CLOCK.plusSeconds(50));
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(rule, CLIENT).allowed()).isTrue();
        }
        clock.set(ONE_O_CLOCK.plusSeconds(70));
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(rule, CLIENT).allowed()).isTrue();
        }
    }
}
