package com.example.scheduler.infrastructure.persistence.entity;

import com.example.scheduler.domain.model.JobState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_execution_history")
public class JobExecutionHistoryEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private JobEntity job;

    private String workerId;

    @Enumerated(EnumType.STRING)
    private JobState fromState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobState toState;

    private String message;

    private Long durationMs;

    @Column(nullable = false)
    private Instant createdAt;

    protected JobExecutionHistoryEntity() {
    }

    public JobExecutionHistoryEntity(
            UUID id,
            JobEntity job,
            String workerId,
            JobState fromState,
            JobState toState,
            String message,
            Long durationMs,
            Instant createdAt
    ) {
        this.id = id;
        this.job = job;
        this.workerId = workerId;
        this.fromState = fromState;
        this.toState = toState;
        this.message = message;
        this.durationMs = durationMs;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public JobState getToState() {
        return toState;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }
}
