package com.example.scheduler.application.port.out;

import com.example.scheduler.application.port.in.JobFilter;
import com.example.scheduler.domain.model.Job;
import com.example.scheduler.domain.model.JobState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface JobRepositoryPort {

    Job save(Job job);

    Optional<Job> findById(UUID id);

    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    List<Job> find(JobFilter filter);

    long count(JobFilter filter);

    List<Job> findDueJobsForUpdate(Instant now, List<String> queueNames, int limit);

    List<Job> findExpiredLeasesForUpdate(Instant now, int limit);

    void deleteById(UUID id);

    Map<JobState, Long> countByState();
}
