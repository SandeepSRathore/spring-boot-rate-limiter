package com.learning.ratelimiter.rules;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * In-memory copy of the rules, so the middleware never touches the disk on the request path.
 * Filled and refreshed by {@link RulesRefreshWorker}.
 */
@Component
public class RuleCache {

    private record CompiledRule(RateLimitRule rule, PathPattern pattern) {
    }

    private final AtomicReference<List<CompiledRule>> rules = new AtomicReference<>(List.of());

    public void replace(List<RateLimitRule> newRules) {
        rules.set(newRules.stream()
                .map(rule -> new CompiledRule(rule, PathPatternParser.defaultInstance.parse(rule.match().path())))
                .toList());
    }

    public List<RateLimitRule> all() {
        return rules.get().stream().map(CompiledRule::rule).toList();
    }

    /** Rules that apply to the request, in file order. */
    public List<RateLimitRule> rulesFor(String method, String path) {
        PathContainer pathContainer = PathContainer.parsePath(path);
        return rules.get().stream()
                .filter(c -> c.rule().match().matchesMethod(method) && c.pattern().matches(pathContainer))
                .map(CompiledRule::rule)
                .toList();
    }
}
