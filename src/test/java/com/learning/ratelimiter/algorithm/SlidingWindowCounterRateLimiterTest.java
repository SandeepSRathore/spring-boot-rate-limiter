package com.learning.ratelimiter.algorithm;

import java.time.Duration;

import com.learning.ratelimiter.rules.Algorithm;
import com.learning.ratelimiter.rules.RateLimitRule;
import com.learning.ratelimiter.rules.RateUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowCounterRateLimiterTest extends RedisAlgorithmTestSupport {

    // the book's example: 7 requests per minute
    private final RateLimitRule rule = rule(Algorithm.SLIDING_WINDOW_COUNTER, RateUnit.MINUTE, 7, null);
    private final SlidingWindowCounterRateLimiter limiter = new SlidingWindowCounterRateLimiter(redis, clock);

    @Test
    void walkThroughTheBookExample() {
        // 5 requests in the previous minute
        clock.set(ONE_O_CLOCK.plusSeconds(30));
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(rule, CLIENT).allowed()).isTrue();
        }
        // 3 requests in the current minute
        clock.set(ONE_O_CLOCK.plusSeconds(60 + 10));
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire(rule, CLIENT).allowed()).isTrue();
        }

        // at the 30% position: 3 + 5 * 0.7 = 6.5, rounded down to 6 < 7 -> allowed
        clock.set(ONE_O_CLOCK.plusSeconds(60 + 18));
        assertThat(limiter.tryAcquire(rule, CLIENT).allowed()).isTrue();

        // 4 + 5 * 0.7 = 7.5 -> 7, not below 7 -> rejected
        RateLimitDecision rejected = limiter.tryAcquire(rule, CLIENT);
        assertThat(rejected.allowed()).isFalse();

        // the previous minute's weight drops over time: 4 + 5 * overlap < 7 once overlap < 0.6
        clock.advance(Duration.ofMillis(rejected.retryAfterMillis()));
        assertThat(limiter.tryAcquire(rule, CLIENT).allowed()).isTrue();
    }

    @Test
    void retryAfterIsTheEarliestMomentARequestCanSucceed() {
        clock.set(ONE_O_CLOCK.plusSeconds(30));
        for (int i = 0; i < 7; i++) {
            assertThat(limiter.tryAcquire(rule, CLIENT).allowed()).isTrue();
        }
        RateLimitDecision rejected = limiter.tryAcquire(rule, CLIENT);
        assertThat(rejected.allowed()).isFalse();

        clock.advance(Duration.ofMillis(rejected.retryAfterMillis() - 1));
        assertThat(limiter.tryAcquire(rule, CLIENT).allowed()).isFalse();
        clock.advance(Duration.ofMillis(1));
        assertThat(limiter.tryAcquire(rule, CLIENT).allowed()).isTrue();
    }
}
