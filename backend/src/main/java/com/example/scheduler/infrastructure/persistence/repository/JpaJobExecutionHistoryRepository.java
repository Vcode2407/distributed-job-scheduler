package com.example.scheduler.infrastructure.persistence.repository;

import com.example.scheduler.domain.model.JobState;
import com.example.scheduler.infrastructure.persistence.entity.JobExecutionHistoryEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaJobExecutionHistoryRepository extends JpaRepository<JobExecutionHistoryEntity, UUID> {

    long countByToStateAndCreatedAtAfter(JobState toState, Instant createdAt);

    long countByCreatedAtAfter(Instant createdAt);

    @Query("select coalesce(avg(h.durationMs), 0) from JobExecutionHistoryEntity h where h.durationMs is not null and h.createdAt > :since")
    double averageDurationMillis(@Param("since") Instant since);

    @Query(value = """
            select date_trunc('hour', created_at) as bucket,
                   count(*) filter (where to_state = 'COMPLETED') as completed,
                   count(*) filter (where to_state in ('FAILED', 'DEAD_LETTERED')) as failed
            from job_execution_history
            where created_at > :since
            group by bucket
            order by bucket
            """, nativeQuery = true)
    List<Object[]> throughput(@Param("since") Instant since);
}
