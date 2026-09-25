package com.learning.ratelimiter.algorithm;

import com.learning.ratelimiter.rules.Algorithm;
import com.learning.ratelimiter.rules.RateLimitRule;
import com.learning.ratelimiter.rules.RateUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowLogRateLimiterTest extends RedisAlgorithmTestSupport {

    // the book's example: 2 requests per minute
    private final RateLimitRule rule = rule(Algorithm.SLIDING_WINDOW_LOG, RateUnit.MINUTE, 2, null);
    private final SlidingWindowLogRateLimiter limiter = new SlidingWindowLogRateLimiter(redis, clock);

    @Test
    void walkThroughTheBookExample() {
        assertThat(requestAt(1)).as("1:00:01").isTrue();
        assertThat(requestAt(30)).as("1:00:30").isTrue();
        assertThat(requestAt(50)).as("1:00:50, log size 3 > 2").isFalse();
        // 1:00:01 and 1:00:30 are outdated; the rejected 1:00:50 stays in the log -> log size 2
        assertThat(requestAt(100)).as("1:01:40").isTrue();
    }

    @Test
    void noEdgeBurstLikeTheFixedWindow() {
        assertThat(requestAt(50)).isTrue();
        assertThat(requestAt(55)).isTrue();
        assertThat(requestAt(61)).as("still within 60 s of the last two").isFalse();
    }

    @Test
    void retryAfterPointsToWhenTheOldestRelevantRequestLeavesTheWindow() {
        requestAt(10);
        requestAt(20);

        clock.set(ONE_O_CLOCK.plusSeconds(30));
        RateLimitDecision rejected = limiter.tryAcquire(rule, CLIENT);
        assertThat(rejected.allowed()).isFalse();
        // log is now 1:00:20, 1:00:30 -> 1:00:20 leaves the window at 1:01:20, 50 s from now
        assertThat(rejected.retryAfterMillis()).isEqualTo(50_000);

        assertThat(requestAt(80)).isTrue();
    }

    private boolean requestAt(int secondsAfterOne) {
        clock.set(ONE_O_CLOCK.plusSeconds(secondsAfterOne));
        return limiter.tryAcquire(rule, CLIENT).allowed();
    }
}
