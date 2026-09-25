package com.learning.ratelimiter.rules;

import java.util.List;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * "Workers frequently pull rules from the disk and store them in the cache."
 * Editing the rules file therefore takes effect without restarting the service.
 */
@Component
public class RulesRefreshWorker {

    private static final Logger log = LoggerFactory.getLogger(RulesRefreshWorker.class);

    private final RuleLoader loader;
    private final RuleCache cache;

    public RulesRefreshWorker(RuleLoader loader, RuleCache cache) {
        this.loader = loader;
        this.cache = cache;
    }

    /** Invalid rules at startup fail the application, rather than running without limits. */
    @PostConstruct
    void loadInitialRules() {
        List<RateLimitRule> rules = loader.load();
        cache.replace(rules);
        log.info("Loaded {} rate limit rules from {}", rules.size(), loader.location());
    }

    /** Invalid rules at runtime are logged and ignored; the last good rules stay active. */
    @Scheduled(initialDelayString = "${ratelimiter.rules-refresh-interval:10s}",
            fixedDelayString = "${ratelimiter.rules-refresh-interval:10s}")
    public void refresh() {
        try {
            List<RateLimitRule> rules = loader.load();
            if (!rules.equals(cache.all())) {
                cache.replace(rules);
                log.info("Rate limit rules changed, now {} rules active", rules.size());
            }
        } catch (RuntimeException e) {
            log.error("Could not reload rate limit rules from {}, keeping the previous ones", loader.location(), e);
        }
    }
}
