package com.learning.ratelimiter.algorithm;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;

import com.learning.ratelimiter.rules.RateLimitRule;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Base class for the Redis backed algorithms.
 *
 * <p>Each algorithm is a Lua script: Redis runs a script atomically, so the "read counter, check, write counter"
 * sequence cannot interleave between two app servers. That solves the race condition described in the book
 * without locks. Because every server talks to the same Redis, the counters are also in sync across servers.
 */
public abstract class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "rate_limit:";

    protected final StringRedisTemplate redis;
    protected final Clock clock;

    protected RedisRateLimiter(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected static RedisScript<List<Long>> script(String fileName) {
        return (RedisScript) RedisScript.of(new ClassPathResource("scripts/" + fileName), List.class);
    }

    /**
     * The {...} hash tag makes every key of one rule + client land on the same Redis Cluster slot,
     * which multi-key scripts need.
     */
    protected static String key(RateLimitRule rule, String clientId, String suffix) {
        return KEY_PREFIX + "{" + rule.name() + ":" + clientId + "}" + suffix;
    }

    /** Runs a script returning {@code {allowed, remaining, retry_after_ms, delay_ms}}. */
    protected RateLimitDecision execute(RedisScript<List<Long>> script, List<String> keys, long limit, Object... args) {
        Object[] argv = Arrays.stream(args).map(String::valueOf).toArray();
        List<Long> result = redis.execute(script, keys, argv);
        return new RateLimitDecision(result.get(0) == 1L, limit, result.get(1), result.get(2), result.get(3));
    }
}
