package com.learning.ratelimiter.middleware;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.learning.ratelimiter.algorithm.RateLimitDecision;
import com.learning.ratelimiter.algorithm.RateLimiterRegistry;
import com.learning.ratelimiter.config.RateLimiterProperties;
import com.learning.ratelimiter.rules.RateLimitRule;
import com.learning.ratelimiter.rules.RuleCache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The rate limiter middleware: it sits in front of the API controllers and runs before any of them.
 *
 * <ol>
 *   <li>Loads the rules matching the request from the {@link RuleCache}.</li>
 *   <li>Asks Redis (through the rule's algorithm) whether the client is still under the limit.</li>
 *   <li>Not limited: forwards the request, adding the X-Ratelimit-* headers.</li>
 *   <li>Limited: answers HTTP 429 Too Many Requests with {@code X-Ratelimit-Retry-After}; the request is dropped.</li>
 * </ol>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(prefix = "ratelimiter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String HEADER_LIMIT = "X-Ratelimit-Limit";
    public static final String HEADER_REMAINING = "X-Ratelimit-Remaining";
    public static final String HEADER_RETRY_AFTER = "X-Ratelimit-Retry-After";
    public static final String HEADER_RULE = "X-Ratelimit-Rule";

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RuleCache ruleCache;
    private final RateLimiterRegistry limiters;
    private final ClientIdentifierResolver clientIdentifierResolver;
    private final MeterRegistry meterRegistry;
    private final boolean failOpen;

    public RateLimitFilter(RuleCache ruleCache, RateLimiterRegistry limiters,
                           ClientIdentifierResolver clientIdentifierResolver, MeterRegistry meterRegistry,
                           RateLimiterProperties properties) {
        this.ruleCache = ruleCache;
        this.limiters = limiters;
        this.clientIdentifierResolver = clientIdentifierResolver;
        this.meterRegistry = meterRegistry;
        this.failOpen = properties.failOpen();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        List<RateLimitRule> rules = ruleCache.rulesFor(request.getMethod(), path);

        RateLimitRule tightestRule = null;
        RateLimitDecision tightest = null;
        long delayMillis = 0;

        // Every matching rule must allow the request (e.g. a per-endpoint limit AND a global per-user limit).
        for (RateLimitRule rule : rules) {
            RateLimitDecision decision;
            try {
                String clientId = clientIdentifierResolver.resolve(request, rule.clientKey());
                decision = limiters.get(rule.algorithm()).tryAcquire(rule, clientId);
            } catch (RuntimeException e) {
                count(rule, "error");
                if (failOpen) {
                    // the rate limiter being down must not take the API down with it
                    log.warn("Rate limiter unavailable for rule '{}', letting request through: {}", rule.name(), e.toString());
                    continue;
                }
                log.error("Rate limiter unavailable for rule '{}', rejecting request", rule.name(), e);
                response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value());
                return;
            }

            if (!decision.allowed()) {
                count(rule, "throttled");
                reject(response, rule, decision);
                return;
            }
            count(rule, "allowed");
            delayMillis = Math.max(delayMillis, decision.delayMillis());
            if (tightest == null || decision.remaining() < tightest.remaining()) {
                tightest = decision;
                tightestRule = rule;
            }
        }

        if (tightest != null) {
            writeHeaders(response, tightestRule, tightest);
        }
        if (delayMillis > 0) {
            waitInQueue(delayMillis);
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, RateLimitRule rule, RateLimitDecision decision) throws IOException {
        long retryAfter = decision.retryAfterSeconds();
        writeHeaders(response, rule, decision);
        response.setHeader(HEADER_RETRY_AFTER, String.valueOf(retryAfter));
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"status":429,"error":"Too Many Requests","rule":"%s","retryAfterSeconds":%d}"""
                .formatted(rule.name(), retryAfter));
    }

    private static void writeHeaders(HttpServletResponse response, RateLimitRule rule, RateLimitDecision decision) {
        response.setHeader(HEADER_LIMIT, String.valueOf(decision.limit()));
        response.setHeader(HEADER_REMAINING, String.valueOf(decision.remaining()));
        response.setHeader(HEADER_RULE, rule.name());
    }

    /**
     * Leaking bucket: the request was queued rather than rejected, so hold it until its turn.
     * Cheap with virtual threads (spring.threads.virtual.enabled).
     */
    private static void waitInQueue(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void count(RateLimitRule rule, String outcome) {
        meterRegistry.counter("ratelimiter.requests",
                "rule", rule.name(), "algorithm", rule.algorithm().name(), "outcome", outcome).increment();
    }
}
