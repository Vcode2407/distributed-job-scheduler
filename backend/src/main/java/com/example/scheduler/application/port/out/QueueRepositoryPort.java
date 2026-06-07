package com.example.scheduler.application.port.out;

import com.example.scheduler.domain.model.TaskQueue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueRepositoryPort {

    TaskQueue save(TaskQueue queue);

    Optional<TaskQueue> findById(UUID id);

    Optional<TaskQueue> findByName(String name);

    List<TaskQueue> findAll();

    boolean existsByName(String name);
}
