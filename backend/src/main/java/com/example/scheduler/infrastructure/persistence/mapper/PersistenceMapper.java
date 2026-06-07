package com.example.scheduler.infrastructure.persistence.mapper;

import com.example.scheduler.domain.model.Job;
import com.example.scheduler.domain.model.TaskQueue;
import com.example.scheduler.domain.model.WorkerNode;
import com.example.scheduler.infrastructure.persistence.entity.JobEntity;
import com.example.scheduler.infrastructure.persistence.entity.TaskQueueEntity;
import com.example.scheduler.infrastructure.persistence.entity.WorkerEntity;
import java.util.Arrays;
import java.util.List;

public final class PersistenceMapper {

    private PersistenceMapper() {
    }

    public static TaskQueue toDomain(TaskQueueEntity entity) {
        return new TaskQueue(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.isPaused(),
                entity.getDeadLetterQueue() == null ? null : entity.getDeadLetterQueue().getId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public static void apply(TaskQueue queue, TaskQueueEntity entity, TaskQueueEntity deadLetterQueue) {
        entity.setId(queue.id());
        entity.setName(queue.name());
        entity.setDescription(queue.description());
        entity.setPaused(queue.paused());
        entity.setDeadLetterQueue(deadLetterQueue);
        entity.setCreatedAt(queue.createdAt());
        entity.setUpdatedAt(queue.updatedAt());
    }

    public static WorkerNode toDomain(WorkerEntity entity) {
        return new WorkerNode(
                entity.getId(),
                entity.getHostname(),
                entity.getStatus(),
                entity.getCapacity(),
                entity.getQueues() == null ? List.of() : Arrays.asList(entity.getQueues()),
                entity.getLastHeartbeatAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public static void apply(WorkerNode worker, WorkerEntity entity) {
        entity.setId(worker.id());
        entity.setHostname(worker.hostname());
        entity.setStatus(worker.status());
        entity.setCapacity(worker.capacity());
        entity.setQueues(worker.queues().toArray(String[]::new));
        entity.setLastHeartbeatAt(worker.lastHeartbeatAt());
        entity.setCreatedAt(worker.createdAt());
        entity.setUpdatedAt(worker.updatedAt());
    }

    public static Job toDomain(JobEntity entity) {
        return new Job(
                entity.getId(),
                entity.getQueue().getId(),
                entity.getName(),
                entity.getPayload(),
                entity.getState(),
                entity.getPriority(),
                entity.getScheduledAt(),
                entity.getCronExpression(),
                entity.getAttemptCount(),
                entity.getMaxAttempts(),
                entity.getInitialBackoffSeconds(),
                entity.getMaxBackoffSeconds(),
                entity.getIdempotencyKey(),
                entity.getLeasedBy(),
                entity.getLeaseExpiresAt(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public static void apply(Job job, JobEntity entity, TaskQueueEntity queue) {
        entity.setId(job.id());
        entity.setQueue(queue);
        entity.setName(job.name());
        entity.setPayload(job.payload());
        entity.setState(job.state());
        entity.setPriority(job.priority());
        entity.setScheduledAt(job.scheduledAt());
        entity.setCronExpression(job.cronExpression());
        entity.setAttemptCount(job.attemptCount());
        entity.setMaxAttempts(job.maxAttempts());
        entity.setInitialBackoffSeconds(job.initialBackoffSeconds());
        entity.setMaxBackoffSeconds(job.maxBackoffSeconds());
        entity.setIdempotencyKey(job.idempotencyKey());
        entity.setLeasedBy(job.leasedBy());
        entity.setLeaseExpiresAt(job.leaseExpiresAt());
        entity.setLastError(job.lastError());
        entity.setCreatedAt(job.createdAt());
        entity.setUpdatedAt(job.updatedAt());
    }
}
