package com.example.scheduler.infrastructure.persistence.repository;

import com.example.scheduler.infrastructure.persistence.entity.TaskQueueEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaTaskQueueRepository extends JpaRepository<TaskQueueEntity, UUID> {

    Optional<TaskQueueEntity> findByName(String name);

    boolean existsByName(String name);
}
