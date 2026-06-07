package com.example.scheduler.infrastructure.persistence.adapter;

import com.example.scheduler.application.port.out.DeadLetterJobRepositoryPort;
import com.example.scheduler.domain.model.Job;
import com.example.scheduler.infrastructure.persistence.entity.DeadLetterJobEntity;
import com.example.scheduler.infrastructure.persistence.entity.JobEntity;
import com.example.scheduler.infrastructure.persistence.entity.TaskQueueEntity;
import com.example.scheduler.infrastructure.persistence.repository.JpaDeadLetterJobRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class DeadLetterJobPersistenceAdapter implements DeadLetterJobRepositoryPort {

    private final JpaDeadLetterJobRepository repository;
    private final EntityManager entityManager;

    public DeadLetterJobPersistenceAdapter(JpaDeadLetterJobRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public void save(Job job, String reason) {
        repository.save(new DeadLetterJobEntity(
                UUID.randomUUID(),
                entityManager.getReference(JobEntity.class, job.id()),
                entityManager.getReference(TaskQueueEntity.class, job.queueId()),
                reason,
                job.payload(),
                job.attemptCount(),
                Instant.now()
        ));
    }
}
