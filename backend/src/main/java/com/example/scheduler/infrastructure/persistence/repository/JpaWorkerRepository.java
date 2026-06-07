package com.example.scheduler.infrastructure.persistence.repository;

import com.example.scheduler.infrastructure.persistence.entity.WorkerEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaWorkerRepository extends JpaRepository<WorkerEntity, String> {

    List<WorkerEntity> findByLastHeartbeatAtBefore(Instant deadline);
}
