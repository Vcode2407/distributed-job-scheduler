package com.example.scheduler.application.port.out;

import com.example.scheduler.domain.model.JobState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JobExecutionHistoryRepositoryPort {

    void record(UUID jobId, String workerId, JobState from, JobState to, String message, Long durationMs);

    long countTransitionsTo(JobState state, Instant since);

    long countAllSince(Instant since);

    double averageDurationMillis(Instant since);

    List<ThroughputPoint> throughput(Instant since);

    record ThroughputPoint(Instant bucket, long completed, long failed) {
    }
}
