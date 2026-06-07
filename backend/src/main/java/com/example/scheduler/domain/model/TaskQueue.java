package com.example.scheduler.domain.model;

import java.time.Instant;
import java.util.UUID;

public record TaskQueue(
        UUID id,
        String name,
        String description,
        boolean paused,
        UUID deadLetterQueueId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public TaskQueue pause(Instant now) {
        return new TaskQueue(id, name, description, true, deadLetterQueueId, createdAt, now, version);
    }

    public TaskQueue resume(Instant now) {
        return new TaskQueue(id, name, description, false, deadLetterQueueId, createdAt, now, version);
    }
}
