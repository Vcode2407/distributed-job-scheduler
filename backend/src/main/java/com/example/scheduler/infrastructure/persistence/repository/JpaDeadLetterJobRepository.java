package com.example.scheduler.infrastructure.persistence.repository;

import com.example.scheduler.infrastructure.persistence.entity.DeadLetterJobEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaDeadLetterJobRepository extends JpaRepository<DeadLetterJobEntity, UUID> {
}
