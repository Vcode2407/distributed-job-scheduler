package com.example.scheduler.domain.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record JobEvent(
        UUID eventId,
        String eventType,
        UUID jobId,
        UUID queueId,
        Instant occurredAt,
        Map<String, Object> attributes
) {
    public static JobEvent of(String eventType, UUID jobId, UUID queueId, Instant occurredAt, Map<String, Object> attributes) {
        return new JobEvent(UUID.randomUUID(), eventType, jobId, queueId, occurredAt, Map.copyOf(attributes));
    }
}
