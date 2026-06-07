package com.example.scheduler.infrastructure.persistence.adapter;

import com.example.scheduler.application.port.out.QueueRepositoryPort;
import com.example.scheduler.domain.model.TaskQueue;
import com.example.scheduler.infrastructure.persistence.entity.TaskQueueEntity;
import com.example.scheduler.infrastructure.persistence.mapper.PersistenceMapper;
import com.example.scheduler.infrastructure.persistence.repository.JpaTaskQueueRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class QueuePersistenceAdapter implements QueueRepositoryPort {

    private final JpaTaskQueueRepository repository;
    private final EntityManager entityManager;

    public QueuePersistenceAdapter(JpaTaskQueueRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public TaskQueue save(TaskQueue queue) {
        TaskQueueEntity entity = repository.findById(queue.id()).orElseGet(TaskQueueEntity::new);
        TaskQueueEntity dlq = queue.deadLetterQueueId() == null
                ? null
                : entityManager.getReference(TaskQueueEntity.class, queue.deadLetterQueueId());
        PersistenceMapper.apply(queue, entity, dlq);
        return PersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<TaskQueue> findById(UUID id) {
        return repository.findById(id).map(PersistenceMapper::toDomain);
    }

    @Override
    public Optional<TaskQueue> findByName(String name) {
        return repository.findByName(name).map(PersistenceMapper::toDomain);
    }

    @Override
    public List<TaskQueue> findAll() {
        return repository.findAll().stream().map(PersistenceMapper::toDomain).toList();
    }

    @Override
    public boolean existsByName(String name) {
        return repository.existsByName(name);
    }
}
