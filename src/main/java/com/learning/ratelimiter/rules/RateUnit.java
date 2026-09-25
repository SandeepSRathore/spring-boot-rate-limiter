package com.learning.ratelimiter.rules;

import java.time.Duration;

public enum RateUnit {
    SECOND(Duration.ofSeconds(1)),
    MINUTE(Duration.ofMinutes(1)),
    HOUR(Duration.ofHours(1)),
    DAY(Duration.ofDays(1));

    private final Duration duration;

    RateUnit(Duration duration) {
        this.duration = duration;
    }

    public long millis() {
        return duration.toMillis();
    }
}
