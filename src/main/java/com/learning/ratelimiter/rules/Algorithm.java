package com.learning.ratelimiter.rules;

/** The five algorithms discussed in the book. */
public enum Algorithm {
    TOKEN_BUCKET,
    LEAKING_BUCKET,
    FIXED_WINDOW_COUNTER,
    SLIDING_WINDOW_LOG,
    SLIDING_WINDOW_COUNTER
}
