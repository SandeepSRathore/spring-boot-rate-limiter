package com.learning.ratelimiter.middleware;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.github.fppt.jedismock.RedisServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.learning.ratelimiter.middleware.RateLimitFilter.HEADER_LIMIT;
import static com.learning.ratelimiter.middleware.RateLimitFilter.HEADER_REMAINING;
import static com.learning.ratelimiter.middleware.RateLimitFilter.HEADER_RETRY_AFTER;
import static com.learning.ratelimiter.middleware.RateLimitFilter.HEADER_RULE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "ratelimiter.rules-location=classpath:test-rate-limit-rules.yaml")
@AutoConfigureMockMvc
class RateLimitFilterIntegrationTest {

    private static final RedisServer REDIS = startRedis();

    @Autowired
    MockMvc mvc;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getBindPort);
    }

    @AfterAll
    static void stopRedis() throws IOException {
        REDIS.stop();
    }

    @Test
    void throttledRequestGets429WithRetryAfter() throws Exception {
        for (int remaining = 2; remaining >= 0; remaining--) {
            mvc.perform(post("/api/login").with(ip("192.168.0.1")))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HEADER_LIMIT, "3"))
                    .andExpect(header().string(HEADER_REMAINING, String.valueOf(remaining)))
                    .andExpect(header().string(HEADER_RULE, "login-per-ip"));
        }

        mvc.perform(post("/api/login").with(ip("192.168.0.1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HEADER_REMAINING, "0"))
                .andExpect(header().exists(HEADER_RETRY_AFTER))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.rule").value("login-per-ip"));

        // another IP has its own limit
        mvc.perform(post("/api/login").with(ip("192.168.0.2"))).andExpect(status().isOk());
    }

    @Test
    void usersAreLimitedIndependently() throws Exception {
        mvc.perform(get("/api/messages").header("X-User-Id", "alice")).andExpect(status().isOk());
        mvc.perform(get("/api/messages").header("X-User-Id", "alice")).andExpect(status().isOk());
        mvc.perform(get("/api/messages").header("X-User-Id", "alice")).andExpect(status().isTooManyRequests());

        mvc.perform(get("/api/messages").header("X-User-Id", "bob")).andExpect(status().isOk());
    }

    @Test
    void headersReportTheMostRestrictiveRule() throws Exception {
        mvc.perform(get("/api/feed").header("X-User-Id", "carol"))
                .andExpect(status().isOk())
                .andExpect(header().string(HEADER_RULE, "api-per-user"))
                .andExpect(header().string(HEADER_REMAINING, "999"));

        mvc.perform(get("/api/messages").header("X-User-Id", "carol"))
                .andExpect(header().string(HEADER_RULE, "messages-per-user"))
                .andExpect(header().string(HEADER_REMAINING, "1"));
    }

    @Test
    void leakingBucketQueuesABurstThenDrops() throws Exception {
        // outflow 1/second, queue of 2: of 3 simultaneous orders one runs now, one waits ~1 s, one is dropped
        List<Callable<Integer>> burst = Collections.nCopies(3, () -> mvc
                .perform(post("/api/orders").header("X-API-Key", "burst"))
                .andReturn().getResponse().getStatus());

        long start = System.nanoTime();
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Integer> result : executor.invokeAll(burst)) {
                statuses.add(result.get());
            }
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 200, 429);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(900));
    }

    @Test
    void pathsWithoutRulesAreNotLimited() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HEADER_LIMIT));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor ip(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private static RedisServer startRedis() {
        try {
            RedisServer server = RedisServer.newRedisServer();
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
