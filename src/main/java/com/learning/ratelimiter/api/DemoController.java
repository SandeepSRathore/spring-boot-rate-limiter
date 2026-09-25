package com.learning.ratelimiter.api;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The "API servers" behind the rate limiter. Each endpoint is covered by a different rule. */
@RestController
@RequestMapping("/api")
public class DemoController {

    @PostMapping("/login")
    public Map<String, Object> login() {
        return response("login");
    }

    @GetMapping("/messages")
    public Map<String, Object> messages() {
        return response("messages");
    }

    @GetMapping("/search")
    public Map<String, Object> search() {
        return response("search");
    }

    @GetMapping("/feed")
    public Map<String, Object> feed() {
        return response("feed");
    }

    @PostMapping("/orders")
    public Map<String, Object> orders() {
        return response("orders");
    }

    private static Map<String, Object> response(String endpoint) {
        return Map.of("endpoint", endpoint, "servedAt", Instant.now().toString());
    }
}
