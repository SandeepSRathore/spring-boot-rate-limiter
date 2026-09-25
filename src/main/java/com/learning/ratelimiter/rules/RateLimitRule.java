package com.learning.ratelimiter.rules;

import java.util.List;
import java.util.Locale;

/**
 * One rate limiting rule, modelled after the Lyft rule format used in the book:
 *
 * <pre>
 * - name: login-per-ip
 *   match: { path: /api/login, methods: [POST] }
 *   client-key: ip
 *   algorithm: sliding-window-log
 *   rate-limit: { unit: minute, requests-per-unit: 5 }
 * </pre>
 */
public record RateLimitRule(
        String name,
        Match match,
        ClientKey clientKey,
        Algorithm algorithm,
        RateLimit rateLimit) {

    public RateLimitRule {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("every rule needs a name");
        }
        if (match == null) {
            throw new IllegalArgumentException("rule '" + name + "': 'match' is required");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("rule '" + name + "': 'algorithm' is required");
        }
        if (rateLimit == null) {
            throw new IllegalArgumentException("rule '" + name + "': 'rate-limit' is required");
        }
        clientKey = clientKey == null ? ClientKey.IP : clientKey;
    }

    /** Which requests the rule applies to. {@code path} is a Spring path pattern such as {@code /api/**}. */
    public record Match(String path, List<String> methods) {

        public Match {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("'match.path' is required");
            }
            methods = methods == null ? List.of() : methods.stream().map(m -> m.toUpperCase(Locale.ROOT)).toList();
        }

        public boolean matchesMethod(String method) {
            return methods.isEmpty() || methods.contains(method);
        }
    }

    /**
     * @param unit            the time window
     * @param requestsPerUnit requests allowed per unit (the refill rate for token bucket, the outflow rate for leaking bucket)
     * @param bucketSize      token bucket capacity / leaking bucket queue size; defaults to {@code requestsPerUnit}
     */
    public record RateLimit(RateUnit unit, long requestsPerUnit, Long bucketSize) {

        public RateLimit {
            if (unit == null) {
                throw new IllegalArgumentException("'rate-limit.unit' is required");
            }
            if (requestsPerUnit <= 0) {
                throw new IllegalArgumentException("'rate-limit.requests-per-unit' must be > 0");
            }
            bucketSize = bucketSize == null ? requestsPerUnit : bucketSize;
            if (bucketSize <= 0) {
                throw new IllegalArgumentException("'rate-limit.bucket-size' must be > 0");
            }
        }

        public long windowMillis() {
            return unit.millis();
        }
    }
}
