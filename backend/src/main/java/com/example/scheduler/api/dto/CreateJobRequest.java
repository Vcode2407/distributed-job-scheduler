package com.example.scheduler.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;

public record CreateJobRequest(
        @NotBlank String name,
        JsonNode payload,
        String queueName,
        @Min(0) @Max(1000) Integer priority,
        Instant scheduledAt,
        String cronExpression,
        @Min(1) @Max(100) Integer maxAttempts,
        @Min(1) Integer initialBackoffSeconds,
        @Min(1) Integer maxBackoffSeconds,
        String idempotencyKey
) {
}
