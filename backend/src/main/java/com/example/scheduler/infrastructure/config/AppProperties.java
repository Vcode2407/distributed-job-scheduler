package com.example.scheduler.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Kafka kafka,
        Scheduler scheduler,
        Security security
) {
    public record Kafka(String jobEventsTopic, String jobEventsRetryTopic, String jobEventsDlqTopic) {
    }

    public record Scheduler(
            Duration leaseDuration,
            int leaseBatchSize,
            int outboxBatchSize,
            Duration heartbeatTimeout
    ) {
    }

    public record Security(
            String jwtSecret,
            Duration tokenTtl,
            boolean devTokenEnabled,
            RateLimit rateLimit
    ) {
    }

    public record RateLimit(int capacity, int refillPerMinute) {
    }
}
