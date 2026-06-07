package com.example.scheduler.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "dead_letter_jobs")
public class DeadLetterJobEntity {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private JobEntity job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "queue_id", nullable = false)
    private TaskQueueEntity queue;

    @Column(nullable = false)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false)
    private int failedAttempts;

    @Column(nullable = false)
    private Instant createdAt;

    protected DeadLetterJobEntity() {
    }

    public DeadLetterJobEntity(UUID id, JobEntity job, TaskQueueEntity queue, String reason, String payload, int failedAttempts, Instant createdAt) {
        this.id = id;
        this.job = job;
        this.queue = queue;
        this.reason = reason;
        this.payload = payload;
        this.failedAttempts = failedAttempts;
        this.createdAt = createdAt;
    }
}
