package com.example.scheduler.domain.model;

import com.example.scheduler.domain.service.JobStateMachine;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Job(
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
        int initialBackoffSeconds,
        int maxBackoffSeconds,
        String idempotencyKey,
        String leasedBy,
        Instant leaseExpiresAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public Job {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(queueId, "queueId is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(payload, "payload is required");
        Objects.requireNonNull(state, "state is required");
        Objects.requireNonNull(scheduledAt, "scheduledAt is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (initialBackoffSeconds < 1) {
            throw new IllegalArgumentException("initialBackoffSeconds must be positive");
        }
        if (maxBackoffSeconds < initialBackoffSeconds) {
            throw new IllegalArgumentException("maxBackoffSeconds must be >= initialBackoffSeconds");
        }
    }

    public static Job create(
            UUID queueId,
            String name,
            String payload,
            int priority,
            Instant scheduledAt,
            String cronExpression,
            int maxAttempts,
            int initialBackoffSeconds,
            int maxBackoffSeconds,
            String idempotencyKey,
            Instant now
    ) {
        return new Job(
                UUID.randomUUID(),
                queueId,
                name,
                payload,
                JobState.CREATED,
                priority,
                scheduledAt,
                cronExpression,
                0,
                maxAttempts,
                initialBackoffSeconds,
                maxBackoffSeconds,
                idempotencyKey,
                null,
                null,
                null,
                now,
                now,
                0
        );
    }

    public Job transitionTo(JobState target, Instant now) {
        JobStateMachine.assertTransition(state, target);
        return copy(target, priority, scheduledAt, cronExpression, attemptCount, leasedBy, leaseExpiresAt, lastError, now);
    }

    public Job schedule(Instant nextScheduledAt, Instant now) {
        JobState target = nextScheduledAt.isAfter(now) ? JobState.SCHEDULED : JobState.QUEUED;
        JobStateMachine.assertTransition(state, target);
        return copy(target, priority, nextScheduledAt, cronExpression, attemptCount, null, null, null, now);
    }

    public Job leaseTo(String workerId, Instant leaseExpiresAt, Instant now) {
        JobStateMachine.assertTransition(state, JobState.LEASED);
        return copy(JobState.LEASED, priority, scheduledAt, cronExpression, attemptCount, workerId, leaseExpiresAt, lastError, now);
    }

    public Job markRunning(Instant now) {
        JobStateMachine.assertTransition(state, JobState.RUNNING);
        return copy(JobState.RUNNING, priority, scheduledAt, cronExpression, attemptCount, leasedBy, leaseExpiresAt, lastError, now);
    }

    public Job markCompleted(Instant now) {
        JobStateMachine.assertTransition(state, JobState.COMPLETED);
        return copy(JobState.COMPLETED, priority, scheduledAt, cronExpression, attemptCount, null, null, null, now);
    }

    public Job retry(String reason, Instant retryAt, Instant now) {
        JobStateMachine.assertTransition(state, JobState.RETRYING);
        return copy(JobState.RETRYING, priority, retryAt, cronExpression, attemptCount + 1, null, null, reason, now);
    }

    public Job fail(String reason, Instant now) {
        JobStateMachine.assertTransition(state, JobState.FAILED);
        return copy(JobState.FAILED, priority, scheduledAt, cronExpression, attemptCount + 1, leasedBy, leaseExpiresAt, reason, now);
    }

    public Job deadLetter(String reason, Instant now) {
        JobStateMachine.assertTransition(state, JobState.DEAD_LETTERED);
        return copy(JobState.DEAD_LETTERED, priority, scheduledAt, cronExpression, attemptCount, null, null, reason, now);
    }

    public boolean canRetry() {
        return attemptCount + 1 < maxAttempts;
    }

    public Duration nextBackoff() {
        long multiplier = 1L << Math.min(attemptCount, 30);
        long seconds = Math.min((long) initialBackoffSeconds * multiplier, maxBackoffSeconds);
        return Duration.ofSeconds(seconds);
    }

    public boolean isLeasedBy(String workerId) {
        return workerId != null && workerId.equals(leasedBy);
    }

    private Job copy(
            JobState nextState,
            int nextPriority,
            Instant nextScheduledAt,
            String nextCronExpression,
            int nextAttemptCount,
            String nextLeasedBy,
            Instant nextLeaseExpiresAt,
            String nextLastError,
            Instant now
    ) {
        return new Job(
                id,
                queueId,
                name,
                payload,
                nextState,
                nextPriority,
                nextScheduledAt,
                nextCronExpression,
                nextAttemptCount,
                maxAttempts,
                initialBackoffSeconds,
                maxBackoffSeconds,
                idempotencyKey,
                nextLeasedBy,
                nextLeaseExpiresAt,
                nextLastError,
                createdAt,
                now,
                version
        );
    }
}
