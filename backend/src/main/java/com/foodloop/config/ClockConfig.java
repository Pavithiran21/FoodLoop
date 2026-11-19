package com.foodloop.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One UTC clock for the whole app, so "now" is always taken the same way. It is an {@link AppClock}:
 * the system clock, unless a test request set its own "now" (only with TIME_OVERRIDE_ENABLED=true).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return AppClock.utc();
    }
}
