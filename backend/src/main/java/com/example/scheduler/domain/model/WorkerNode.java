package com.example.scheduler.domain.model;

import java.time.Instant;
import java.util.List;

public record WorkerNode(
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
    public WorkerNode heartbeat(Instant now) {
        return new WorkerNode(id, hostname, WorkerStatus.HEALTHY, capacity, List.copyOf(queues), now, createdAt, now, version);
    }

    public WorkerNode markOffline(Instant now) {
        return new WorkerNode(id, hostname, WorkerStatus.OFFLINE, capacity, List.copyOf(queues), lastHeartbeatAt, createdAt, now, version);
    }
}
