package com.example.scheduler.infrastructure.persistence.repository;

import com.example.scheduler.infrastructure.persistence.entity.JobEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JpaJobRepository extends JpaRepository<JobEntity, UUID> {

    Optional<JobEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("select j.state, count(j) from JobEntity j group by j.state")
    List<Object[]> countByStateGrouped();
}
