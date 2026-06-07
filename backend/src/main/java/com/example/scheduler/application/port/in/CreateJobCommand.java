package com.example.scheduler.application.port.in;

import java.time.Instant;

public record CreateJobCommand(
        String name,
        String payload,
        String queueName,
        int priority,
        Instant scheduledAt,
        String cronExpression,
        int maxAttempts,
        int initialBackoffSeconds,
        int maxBackoffSeconds,
        String idempotencyKey
) {
}
