package com.learning.ratelimiter.middleware;

import jakarta.servlet.http.HttpServletRequest;

import com.learning.ratelimiter.config.RateLimiterProperties;
import com.learning.ratelimiter.rules.ClientKey;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Turns a request into the identity a rule counts against ("ip:10.0.0.1", "user:alice", ...). */
@Component
public class ClientIdentifierResolver {

    public static final String USER_HEADER = "X-User-Id";
    public static final String API_KEY_HEADER = "X-API-Key";

    private final boolean trustForwardedFor;

    public ClientIdentifierResolver(RateLimiterProperties properties) {
        this.trustForwardedFor = properties.trustForwardedFor();
    }

    public String resolve(HttpServletRequest request, ClientKey clientKey) {
        return switch (clientKey) {
            case IP -> "ip:" + clientIp(request);
            case USER -> headerOrIp(request, USER_HEADER, "user:");
            case API_KEY -> headerOrIp(request, API_KEY_HEADER, "key:");
        };
    }

    private String headerOrIp(HttpServletRequest request, String header, String prefix) {
        String value = request.getHeader(header);
        return StringUtils.hasText(value) ? prefix + value.trim() : "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
