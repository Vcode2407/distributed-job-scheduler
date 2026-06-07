package com.example.scheduler.application.port.out;

import com.example.scheduler.domain.event.JobEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepositoryPort {

    void append(JobEvent event);

    List<OutboxMessage> findPending(int limit);

    void markPublished(UUID id, Instant publishedAt);

    void markFailed(UUID id, String error);

    record OutboxMessage(UUID id, UUID aggregateId, String eventType, String payload, int attempts) {
    }
}
