package com.example.scheduler.api.dto;

import com.example.scheduler.domain.model.WorkerStatus;
import java.time.Instant;
import java.util.List;

public record WorkerResponse(
        String id,
        String hostname,
        WorkerStatus status,
        int capacity,
        List<String> queues,
        Instant lastHeartbeatAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
