package com.edusync.analytics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * WHY a Clock bean instead of AnalyticsService calling Instant.now()/
 * LocalDate.now() directly: matches the convention already established by
 * course-service/enrollment-service's ClockConfig — injecting Clock lets
 * AnalyticsServiceTest assert exact, deterministic timestamps and exact
 * "days until due" arithmetic in the study-plan scheduler using
 * Clock.fixed(...), instead of fragile "within N seconds/days" comparisons.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
