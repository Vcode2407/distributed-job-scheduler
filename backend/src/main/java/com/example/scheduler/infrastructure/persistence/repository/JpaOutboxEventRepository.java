package com.example.scheduler.infrastructure.persistence.repository;

import com.example.scheduler.infrastructure.persistence.entity.OutboxEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaOutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}
