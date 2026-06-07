package com.example.scheduler.application.service;

import com.example.scheduler.application.port.out.JobExecutionHistoryRepositoryPort;
import com.example.scheduler.application.port.out.JobRepositoryPort;
import com.example.scheduler.application.port.out.QueueRepositoryPort;
import com.example.scheduler.application.port.out.WorkerRepositoryPort;
import com.example.scheduler.domain.model.JobState;
import com.example.scheduler.domain.model.WorkerNode;
import com.example.scheduler.domain.model.WorkerStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricsApplicationService {

    private final JobRepositoryPort jobs;
    private final QueueRepositoryPort queues;
    private final WorkerRepositoryPort workers;
    private final JobExecutionHistoryRepositoryPort history;
    private final Clock clock;

    public MetricsApplicationService(
            JobRepositoryPort jobs,
            QueueRepositoryPort queues,
            WorkerRepositoryPort workers,
            JobExecutionHistoryRepositoryPort history,
            Clock clock
    ) {
        this.jobs = jobs;
        this.queues = queues;
        this.workers = workers;
        this.history = history;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MetricsSnapshot snapshot() {
        Instant since = clock.instant().minus(Duration.ofHours(24));
        Map<JobState, Long> counts = jobs.countByState();
        long totalTransitions = Math.max(1, history.countAllSince(since));
        long failures = history.countTransitionsTo(JobState.FAILED, since) + history.countTransitionsTo(JobState.DEAD_LETTERED, since);
        long retries = history.countTransitionsTo(JobState.RETRYING, since);

        return new MetricsSnapshot(
                counts,
                queues.findAll().stream()
                        .map(queue -> new QueueMetric(queue.id().toString(), queue.name(), queue.paused()))
                        .toList(),
                workers.findAll().stream()
                        .collect(Collectors.groupingBy(WorkerNode::status, Collectors.counting())),
                history.throughput(since),
                history.averageDurationMillis(since),
                failures / (double) totalTransitions,
                retries / (double) totalTransitions
        );
    }

    public record MetricsSnapshot(
            Map<JobState, Long> jobsByState,
            List<QueueMetric> queues,
            Map<WorkerStatus, Long> workersByStatus,
            List<JobExecutionHistoryRepositoryPort.ThroughputPoint> throughput,
            double averageProcessingTimeMillis,
            double failureRate,
            double retryRate
    ) {
    }

    public record QueueMetric(String id, String name, boolean paused) {
    }
}
