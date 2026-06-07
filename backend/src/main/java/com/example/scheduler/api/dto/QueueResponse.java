package com.example.scheduler.api.dto;

import java.time.Instant;
import java.util.UUID;

public record QueueResponse(
        UUID id,
        String name,
        String description,
        boolean paused,
        UUID deadLetterQueueId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
