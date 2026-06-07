package com.example.scheduler.infrastructure.persistence.adapter;

import com.example.scheduler.application.port.out.WorkerRepositoryPort;
import com.example.scheduler.domain.model.WorkerNode;
import com.example.scheduler.infrastructure.persistence.entity.WorkerEntity;
import com.example.scheduler.infrastructure.persistence.mapper.PersistenceMapper;
import com.example.scheduler.infrastructure.persistence.repository.JpaWorkerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class WorkerPersistenceAdapter implements WorkerRepositoryPort {

    private final JpaWorkerRepository repository;

    public WorkerPersistenceAdapter(JpaWorkerRepository repository) {
        this.repository = repository;
    }

    @Override
    public WorkerNode save(WorkerNode worker) {
        WorkerEntity entity = repository.findById(worker.id()).orElseGet(WorkerEntity::new);
        PersistenceMapper.apply(worker, entity);
        return PersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<WorkerNode> findById(String id) {
        return repository.findById(id).map(PersistenceMapper::toDomain);
    }

    @Override
    public List<WorkerNode> findAll() {
        return repository.findAll().stream().map(PersistenceMapper::toDomain).toList();
    }

    @Override
    public List<WorkerNode> findHeartbeatBefore(Instant deadline) {
        return repository.findByLastHeartbeatAtBefore(deadline).stream().map(PersistenceMapper::toDomain).toList();
    }
}
