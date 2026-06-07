package com.example.scheduler.application.port.out;

import com.example.scheduler.domain.model.WorkerNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WorkerRepositoryPort {

    WorkerNode save(WorkerNode worker);

    Optional<WorkerNode> findById(String id);

    List<WorkerNode> findAll();

    List<WorkerNode> findHeartbeatBefore(Instant deadline);
}
