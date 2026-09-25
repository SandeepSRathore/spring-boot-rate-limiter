package com.learning.ratelimiter.rules;

import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import com.learning.ratelimiter.config.RateLimiterProperties;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Reads the rules file from disk ("Rules are stored on the disk" in the book's detailed design). */
@Component
public class RuleLoader {

    private final ResourceLoader resourceLoader;
    private final String location;

    public RuleLoader(ResourceLoader resourceLoader, RateLimiterProperties properties) {
        this.resourceLoader = resourceLoader;
        this.location = properties.rulesLocation();
    }

    public List<RateLimitRule> load() {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("Rate limit rules not found at " + location);
        }
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(resource);
        Properties flattened = yaml.getObject();

        // Reuse Spring Boot's binder so the file gets relaxed names (requests-per-unit) and enum conversion
        // (sliding-window-log -> SLIDING_WINDOW_LOG) just like application.yml.
        List<RateLimitRule> rules = new Binder(new MapConfigurationPropertySource(flattened))
                .bind("rules", Bindable.listOf(RateLimitRule.class))
                .orElse(List.of());

        Set<String> names = new HashSet<>();
        for (RateLimitRule rule : rules) {
            if (!names.add(rule.name())) {
                throw new IllegalStateException("Duplicate rule name '" + rule.name() + "' in " + location);
            }
        }
        return List.copyOf(rules);
    }

    public String location() {
        return location;
    }
}
