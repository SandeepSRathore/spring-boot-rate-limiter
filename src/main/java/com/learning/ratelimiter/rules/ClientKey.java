package com.learning.ratelimiter.rules;

/** What a rule counts requests against, e.g. "5 logins per minute per IP" or "100 calls per minute per user". */
public enum ClientKey {
    /** Client IP address. */
    IP,
    /** The {@code X-User-Id} header (in a real system: the user id from the auth token). Falls back to IP. */
    USER,
    /** The {@code X-API-Key} header. Falls back to IP. */
    API_KEY
}
