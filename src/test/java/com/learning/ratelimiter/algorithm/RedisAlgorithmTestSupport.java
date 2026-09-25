package com.learning.ratelimiter.algorithm;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.github.fppt.jedismock.RedisServer;
import com.learning.ratelimiter.rules.Algorithm;
import com.learning.ratelimiter.rules.ClientKey;
import com.learning.ratelimiter.rules.RateLimitRule;
import com.learning.ratelimiter.rules.RateUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Runs the real Lua scripts against an in-JVM Redis server, with a clock the test controls. */
abstract class RedisAlgorithmTestSupport {

    /** 01:00:00, so the book's timestamps (1:00:01, 1:00:30, ...) read naturally in the tests. */
    static final Instant ONE_O_CLOCK = Instant.parse("2026-01-01T01:00:00Z");
    static final String CLIENT = "ip:10.0.0.1";

    private static RedisServer server;
    private static LettuceConnectionFactory connectionFactory;
    static StringRedisTemplate redis;

    final MutableClock clock = new MutableClock(ONE_O_CLOCK);

    @BeforeAll
    static void startRedis() throws IOException {
        server = RedisServer.newRedisServer();
        server.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(server.getHost(), server.getBindPort()));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void stopRedis() throws IOException {
        connectionFactory.destroy();
        server.stop();
    }

    @BeforeEach
    void flushRedis() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    static RateLimitRule rule(Algorithm algorithm, RateUnit unit, long requestsPerUnit, Long bucketSize) {
        return new RateLimitRule("test", new RateLimitRule.Match("/test", null), ClientKey.IP, algorithm,
                new RateLimitRule.RateLimit(unit, requestsPerUnit, bucketSize));
    }

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
