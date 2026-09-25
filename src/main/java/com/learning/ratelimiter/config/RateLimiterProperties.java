package com.learning.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param enabled           turns the rate limiter middleware on or off
 * @param rulesLocation     where the rules live "on disk" (any Spring resource location)
 * @param failOpen          if Redis is unreachable, let requests through (true) or answer 503 (false)
 * @param trustForwardedFor use the first X-Forwarded-For entry as client IP (only safe behind a trusted proxy)
 */
@ConfigurationProperties("ratelimiter")
public record RateLimiterProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("file:config/rate-limit-rules.yaml") String rulesLocation,
        @DefaultValue("true") boolean failOpen,
        @DefaultValue("false") boolean trustForwardedFor) {
}
