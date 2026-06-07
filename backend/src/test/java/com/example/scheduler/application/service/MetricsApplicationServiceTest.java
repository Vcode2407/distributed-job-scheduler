package com.example.scheduler.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.scheduler.application.port.out.JobExecutionHistoryRepositoryPort;
import com.example.scheduler.application.port.out.JobRepositoryPort;
import com.example.scheduler.application.port.out.QueueRepositoryPort;
import com.example.scheduler.application.port.out.WorkerRepositoryPort;
import com.example.scheduler.domain.model.JobState;
import com.example.scheduler.domain.model.TaskQueue;
import com.example.scheduler.domain.model.WorkerNode;
import com.example.scheduler.domain.model.WorkerStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricsApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-07T00:00:00Z");
    private static final Instant SINCE = NOW.minusSeconds(86_400);

    @Mock
    private JobRepositoryPort jobs;

    @Mock
    private QueueRepositoryPort queues;

    @Mock
    private WorkerRepositoryPort workers;

    @Mock
    private JobExecutionHistoryRepositoryPort history;

    private MetricsApplicationService service;

    @BeforeEach
    void setUp() {
        service = new MetricsApplicationService(jobs, queues, workers, history, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void buildsMetricsSnapshotFromRepositories() {
        when(jobs.countByState()).thenReturn(Map.of(JobState.QUEUED, 7L, JobState.FAILED, 2L));
        when(history.countAllSince(SINCE)).thenReturn(20L);
        when(history.countTransitionsTo(JobState.FAILED, SINCE)).thenReturn(2L);
        when(history.countTransitionsTo(JobState.DEAD_LETTERED, SINCE)).thenReturn(1L);
        when(history.countTransitionsTo(JobState.RETRYING, SINCE)).thenReturn(4L);
        when(history.averageDurationMillis(SINCE)).thenReturn(125.5);
        when(history.throughput(SINCE)).thenReturn(List.of(new JobExecutionHistoryRepositoryPort.ThroughputPoint(NOW.minusSeconds(60), 10, 1)));
        when(queues.findAll()).thenReturn(List.of(queue("default", false), queue("critical", true)));
        when(workers.findAll()).thenReturn(List.of(
                worker("worker-1", WorkerStatus.HEALTHY),
                worker("worker-2", WorkerStatus.HEALTHY),
                worker("worker-3", WorkerStatus.OFFLINE)
        ));

        MetricsApplicationService.MetricsSnapshot snapshot = service.snapshot();

        assertThat(snapshot.jobsByState()).containsEntry(JobState.QUEUED, 7L);
        assertThat(snapshot.queues()).extracting(MetricsApplicationService.QueueMetric::name).containsExactly("default", "critical");
        assertThat(snapshot.queues()).extracting(MetricsApplicationService.QueueMetric::paused).containsExactly(false, true);
        assertThat(snapshot.workersByStatus()).containsEntry(WorkerStatus.HEALTHY, 2L).containsEntry(WorkerStatus.OFFLINE, 1L);
        assertThat(snapshot.throughput()).hasSize(1);
        assertThat(snapshot.averageProcessingTimeMillis()).isEqualTo(125.5);
        assertThat(snapshot.failureRate()).isEqualTo(0.15);
        assertThat(snapshot.retryRate()).isEqualTo(0.2);
    }

    @Test
    void avoidsDivideByZeroWhenThereAreNoTransitions() {
        when(jobs.countByState()).thenReturn(Map.of());
        when(history.countAllSince(SINCE)).thenReturn(0L);
        when(history.countTransitionsTo(JobState.FAILED, SINCE)).thenReturn(0L);
        when(history.countTransitionsTo(JobState.DEAD_LETTERED, SINCE)).thenReturn(0L);
        when(history.countTransitionsTo(JobState.RETRYING, SINCE)).thenReturn(0L);
        when(history.throughput(SINCE)).thenReturn(List.of());
        when(queues.findAll()).thenReturn(List.of());
        when(workers.findAll()).thenReturn(List.of());

        MetricsApplicationService.MetricsSnapshot snapshot = service.snapshot();

        assertThat(snapshot.failureRate()).isZero();
        assertThat(snapshot.retryRate()).isZero();
    }

    private TaskQueue queue(String name, boolean paused) {
        return new TaskQueue(UUID.randomUUID(), name, name + " queue", paused, null, NOW, NOW, 0);
    }

    private WorkerNode worker(String id, WorkerStatus status) {
        return new WorkerNode(id, id + ".local", status, 10, List.of("default"), NOW, NOW, NOW, 0);
    }
}
