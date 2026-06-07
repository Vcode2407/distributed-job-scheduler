package com.example.scheduler.infrastructure.persistence.adapter;

import com.example.scheduler.application.port.out.JobExecutionHistoryRepositoryPort;
import com.example.scheduler.domain.model.JobState;
import com.example.scheduler.infrastructure.persistence.entity.JobEntity;
import com.example.scheduler.infrastructure.persistence.entity.JobExecutionHistoryEntity;
import com.example.scheduler.infrastructure.persistence.repository.JpaJobExecutionHistoryRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JobExecutionHistoryPersistenceAdapter implements JobExecutionHistoryRepositoryPort {

    private final JpaJobExecutionHistoryRepository repository;
    private final EntityManager entityManager;

    public JobExecutionHistoryPersistenceAdapter(JpaJobExecutionHistoryRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public void record(UUID jobId, String workerId, JobState from, JobState to, String message, Long durationMs) {
        repository.save(new JobExecutionHistoryEntity(
                UUID.randomUUID(),
                entityManager.getReference(JobEntity.class, jobId),
                workerId,
                from,
                to,
                message,
                durationMs,
                Instant.now()
        ));
    }

    @Override
    public long countTransitionsTo(JobState state, Instant since) {
        return repository.countByToStateAndCreatedAtAfter(state, since);
    }

    @Override
    public long countAllSince(Instant since) {
        return repository.countByCreatedAtAfter(since);
    }

    @Override
    public double averageDurationMillis(Instant since) {
        return repository.averageDurationMillis(since);
    }

    @Override
    public List<ThroughputPoint> throughput(Instant since) {
        return repository.throughput(since).stream()
                .map(row -> new ThroughputPoint(toInstant(row[0]), ((Number) row[1]).longValue(), ((Number) row[2]).longValue()))
                .toList();
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return Instant.parse(value.toString());
    }
}
