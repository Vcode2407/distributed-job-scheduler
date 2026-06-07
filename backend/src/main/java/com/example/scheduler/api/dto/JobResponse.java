package com.example.scheduler.api.dto;

import com.example.scheduler.domain.model.JobState;
import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        UUID queueId,
        String name,
        String payload,
        JobState state,
        int priority,
        Instant scheduledAt,
        String cronExpression,
        int attemptCount,
        int maxAttempts,
        String idempotencyKey,
        String leasedBy,
        Instant leaseExpiresAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
