package com.learning.ratelimiter.algorithm;

/**
 * @param allowed          whether the request may go through
 * @param limit            value for {@code X-Ratelimit-Limit}
 * @param remaining        value for {@code X-Ratelimit-Remaining}
 * @param retryAfterMillis when rejected: how long until a retry can succeed ({@code X-Ratelimit-Retry-After})
 * @param delayMillis      when allowed: how long the request must wait before being processed (leaking bucket only)
 */
public record RateLimitDecision(boolean allowed, long limit, long remaining, long retryAfterMillis, long delayMillis) {

    public long retryAfterSeconds() {
        return Math.max(1, (retryAfterMillis + 999) / 1000);
    }
}
