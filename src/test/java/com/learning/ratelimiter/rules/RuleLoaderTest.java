package com.learning.ratelimiter.rules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.learning.ratelimiter.config.RateLimiterProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleLoaderTest {

    @TempDir
    Path dir;

    @Test
    void shippedRulesFileIsValid() {
        List<RateLimitRule> rules = loader("file:config/rate-limit-rules.yaml").load();

        assertThat(rules).extracting(RateLimitRule::name).contains("login-per-ip", "api-per-user");
        RateLimitRule login = rules.getFirst();
        assertThat(login.algorithm()).isEqualTo(Algorithm.SLIDING_WINDOW_LOG);
        assertThat(login.clientKey()).isEqualTo(ClientKey.IP);
        assertThat(login.match().methods()).containsExactly("POST");
        assertThat(login.rateLimit()).isEqualTo(new RateLimitRule.RateLimit(RateUnit.MINUTE, 5, 5L));
    }

    @Test
    void cacheMatchesRulesByPathAndMethod() {
        RuleCache cache = new RuleCache();
        cache.replace(loader("file:config/rate-limit-rules.yaml").load());

        assertThat(cache.rulesFor("POST", "/api/login")).extracting(RateLimitRule::name)
                .containsExactly("login-per-ip", "api-per-user");
        assertThat(cache.rulesFor("GET", "/api/login")).extracting(RateLimitRule::name)
                .containsExactly("api-per-user");
        assertThat(cache.rulesFor("GET", "/actuator/health")).isEmpty();
    }

    @Test
    void workerPicksUpChangesAndKeepsLastGoodRulesOnError() throws IOException {
        Path file = dir.resolve("rules.yaml");
        Files.writeString(file, rulesYaml(5));
        RuleLoader loader = loader("file:" + file);
        RuleCache cache = new RuleCache();
        RulesRefreshWorker worker = new RulesRefreshWorker(loader, cache);
        worker.loadInitialRules();

        Files.writeString(file, rulesYaml(50));
        worker.refresh();
        assertThat(cache.all().getFirst().rateLimit().requestsPerUnit()).isEqualTo(50);

        Files.writeString(file, rulesYaml(-1));
        worker.refresh();
        assertThat(cache.all().getFirst().rateLimit().requestsPerUnit()).isEqualTo(50);
    }

    @Test
    void rejectsDuplicateRuleNames() throws IOException {
        Path file = dir.resolve("rules.yaml");
        Files.writeString(file, rulesYaml(5) + rulesYaml(5).replace("rules:\n", ""));

        assertThatThrownBy(() -> loader("file:" + file).load()).hasMessageContaining("Duplicate rule name 'r1'");
    }

    private static RuleLoader loader(String location) {
        return new RuleLoader(new DefaultResourceLoader(), new RateLimiterProperties(true, location, true, false));
    }

    private static String rulesYaml(int requestsPerUnit) {
        return """
                rules:
                  - name: r1
                    match: { path: /api/** }
                    algorithm: token-bucket
                    rate-limit: { unit: second, requests-per-unit: %d }
                """.formatted(requestsPerUnit);
    }
}
