package com.learning.ratelimiter.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /**
     * Time source for all algorithms. The app server's time is passed into the Redis scripts, so servers
     * sharing one Redis must keep their clocks in sync (NTP). Injected so tests can control time.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
