package com.example.scheduler.api.mapper;

import com.example.scheduler.api.dto.JobResponse;
import com.example.scheduler.api.dto.MetricsResponse;
import com.example.scheduler.api.dto.QueueResponse;
import com.example.scheduler.api.dto.WorkerResponse;
import com.example.scheduler.application.service.MetricsApplicationService;
import com.example.scheduler.domain.model.Job;
import com.example.scheduler.domain.model.TaskQueue;
import com.example.scheduler.domain.model.WorkerNode;

public final class ApiMapper {

    private ApiMapper() {
    }

    public static JobResponse toResponse(Job job) {
        return new JobResponse(
                job.id(),
                job.queueId(),
                job.name(),
                job.payload(),
                job.state(),
                job.priority(),
                job.scheduledAt(),
                job.cronExpression(),
                job.attemptCount(),
                job.maxAttempts(),
                job.idempotencyKey(),
                job.leasedBy(),
                job.leaseExpiresAt(),
                job.lastError(),
                job.createdAt(),
                job.updatedAt(),
                job.version()
        );
    }

    public static QueueResponse toResponse(TaskQueue queue) {
        return new QueueResponse(
                queue.id(),
                queue.name(),
                queue.description(),
                queue.paused(),
                queue.deadLetterQueueId(),
                queue.createdAt(),
                queue.updatedAt(),
                queue.version()
        );
    }

    public static WorkerResponse toResponse(WorkerNode worker) {
        return new WorkerResponse(
                worker.id(),
                worker.hostname(),
                worker.status(),
                worker.capacity(),
                worker.queues(),
                worker.lastHeartbeatAt(),
                worker.createdAt(),
                worker.updatedAt(),
                worker.version()
        );
    }

    public static MetricsResponse toResponse(MetricsApplicationService.MetricsSnapshot snapshot) {
        return new MetricsResponse(
                snapshot.jobsByState(),
                snapshot.queues(),
                snapshot.workersByStatus(),
                snapshot.throughput(),
                snapshot.averageProcessingTimeMillis(),
                snapshot.failureRate(),
                snapshot.retryRate()
        );
    }
}
