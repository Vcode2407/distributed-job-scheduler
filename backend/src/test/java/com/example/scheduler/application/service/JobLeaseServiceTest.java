package com.example.scheduler.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.scheduler.application.exception.ConflictException;
import com.example.scheduler.application.exception.NotFoundException;
import com.example.scheduler.application.port.out.DeadLetterJobRepositoryPort;
import com.example.scheduler.application.port.out.DistributedLockPort;
import com.example.scheduler.application.port.out.JobExecutionHistoryRepositoryPort;
import com.example.scheduler.application.port.out.JobRepositoryPort;
import com.example.scheduler.application.port.out.OutboxRepositoryPort;
import com.example.scheduler.application.port.out.WorkerRepositoryPort;
import com.example.scheduler.domain.model.Job;
import com.example.scheduler.domain.model.JobState;
import com.example.scheduler.domain.model.WorkerNode;
import com.example.scheduler.domain.model.WorkerStatus;
import com.example.scheduler.infrastructure.config.AppProperties;
import com.example.scheduler.infrastructure.metrics.SchedulerMetrics;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobLeaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-07T00:00:00Z");
    private static final UUID QUEUE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private JobRepositoryPort jobs;

    @Mock
    private WorkerRepositoryPort workers;

    @Mock
    private JobExecutionHistoryRepositoryPort history;

    @Mock
    private DeadLetterJobRepositoryPort deadLetters;

    @Mock
    private OutboxRepositoryPort outbox;

    @Mock
    private DistributedLockPort locks;

    @Mock
    private JobApplicationService jobApplicationService;

    @Mock
    private SchedulerMetrics metrics;

    private JobLeaseService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                null,
                new AppProperties.Scheduler(Duration.ofSeconds(30), 100, 100, Duration.ofSeconds(60)),
                null
        );
        service = new JobLeaseService(
                jobs,
                workers,
                history,
                deadLetters,
                outbox,
                locks,
                jobApplicationService,
                properties,
                metrics,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        lenient().when(jobs.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsUnknownWorkerWhenLeasing() {
        when(workers.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.leaseDueJobs("missing", 10))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Worker not registered");
    }

    @Test
    void returnsNoLeasesWhenWorkerLockIsHeldElsewhere() {
        when(workers.findById("worker-1")).thenReturn(Optional.of(worker(2)));
        when(locks.acquire(eq("leases:worker-1"), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(service.leaseDueJobs("worker-1", 10)).isEmpty();
    }

    @Test
    void leasesDueJobsUsingCapacityAndBatchCaps() {
        Job due = job(JobState.QUEUED, null, null, 0, 3);
        when(workers.findById("worker-1")).thenReturn(Optional.of(worker(2)));
        when(locks.acquire(eq("leases:worker-1"), anyString(), any(Duration.class))).thenReturn(true);
        when(jobs.findDueJobsForUpdate(NOW, List.of("default", "critical"), 2)).thenReturn(List.of(due));

        List<Job> leased = service.leaseDueJobs("worker-1", 500);

        assertThat(leased).hasSize(1);
        assertThat(leased.getFirst().state()).isEqualTo(JobState.LEASED);
        assertThat(leased.getFirst().leasedBy()).isEqualTo("worker-1");
        assertThat(leased.getFirst().leaseExpiresAt()).isEqualTo(NOW.plusSeconds(30));
        verify(history).record(due.id(), "worker-1", JobState.QUEUED, JobState.LEASED, "Job leased", null);
        verify(outbox).append(any());
        verify(metrics).recordLeased();
        verify(locks).release(eq("leases:worker-1"), anyString());
    }

    @Test
    void refusesToStartExpiredLease() {
        Job expired = job(JobState.LEASED, "worker-1", NOW.minusSeconds(1), 0, 3);
        when(jobs.findById(expired.id())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.markRunning("worker-1", expired.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("lease has expired");
    }

    @Test
    void marksRunningJobCompletedAndRecordsDuration() {
        Job running = job(JobState.RUNNING, "worker-1", NOW.plusSeconds(30), 0, 3);
        when(jobs.findById(running.id())).thenReturn(Optional.of(running));

        Job completed = service.markCompleted("worker-1", running.id(), 42L);

        assertThat(completed.state()).isEqualTo(JobState.COMPLETED);
        assertThat(completed.leasedBy()).isNull();
        verify(history).record(running.id(), "worker-1", JobState.RUNNING, JobState.COMPLETED, "Job completed", 42L);
        verify(metrics).recordCompleted(42L);
        verify(jobApplicationService).createNextRecurringOccurrence(completed, NOW);
    }

    @Test
    void retryableFailureSchedulesRetryWithBackoff() {
        Job running = job(JobState.RUNNING, "worker-1", NOW.plusSeconds(30), 0, 3);
        when(jobs.findById(running.id())).thenReturn(Optional.of(running));

        Job retrying = service.markFailed("worker-1", running.id(), "transient", 100L);

        assertThat(retrying.state()).isEqualTo(JobState.RETRYING);
        assertThat(retrying.attemptCount()).isEqualTo(1);
        assertThat(retrying.scheduledAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(retrying.lastError()).isEqualTo("transient");
        verify(history).record(running.id(), "worker-1", JobState.RUNNING, JobState.RETRYING, "transient", 100L);
        verify(metrics).recordFailure(100L);
        verify(metrics).recordRetry();
    }

    @Test
    void finalFailureMovesJobToDeadLetterQueue() {
        Job running = job(JobState.RUNNING, "worker-1", NOW.plusSeconds(30), 2, 3);
        when(jobs.findById(running.id())).thenReturn(Optional.of(running));

        Job dead = service.markFailed("worker-1", running.id(), "permanent", 250L);

        assertThat(dead.state()).isEqualTo(JobState.DEAD_LETTERED);
        assertThat(dead.attemptCount()).isEqualTo(3);
        verify(deadLetters).save(dead, "permanent");
        verify(history).record(running.id(), "worker-1", JobState.RUNNING, JobState.FAILED, "permanent", 250L);
        verify(history).record(running.id(), "worker-1", JobState.FAILED, JobState.DEAD_LETTERED, "permanent", 250L);
        verify(metrics).recordFailure(250L);
        verify(metrics).recordDeadLettered();
    }

    @Test
    void recoversExpiredLeasesByFailingThem() {
        Job expired = job(JobState.LEASED, "worker-1", NOW.minusSeconds(1), 0, 3);
        when(jobs.findExpiredLeasesForUpdate(NOW, 50)).thenReturn(List.of(expired));

        int recovered = service.recoverExpiredLeases(50);

        assertThat(recovered).isEqualTo(1);
        ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
        verify(jobs).save(saved.capture());
        assertThat(saved.getValue().state()).isEqualTo(JobState.RETRYING);
        assertThat(saved.getValue().lastError()).contains("Lease expired");
    }

    @Test
    void rejectsCompletionByDifferentWorker() {
        Job running = job(JobState.RUNNING, "worker-1", NOW.plusSeconds(30), 0, 3);
        when(jobs.findById(running.id())).thenReturn(Optional.of(running));

        assertThatThrownBy(() -> service.markCompleted("worker-2", running.id(), 10L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not leased by worker");
    }

    private WorkerNode worker(int capacity) {
        return new WorkerNode("worker-1", "worker-1.local", WorkerStatus.HEALTHY, capacity, List.of("default", "critical"), NOW, NOW, NOW, 0);
    }

    private Job job(JobState state, String leasedBy, Instant leaseExpiresAt, int attemptCount, int maxAttempts) {
        return new Job(
                UUID.randomUUID(),
                QUEUE_ID,
                "billing",
                "{}",
                state,
                10,
                NOW,
                null,
                attemptCount,
                maxAttempts,
                30,
                3600,
                null,
                leasedBy,
                leaseExpiresAt,
                null,
                NOW.minusSeconds(60),
                NOW.minusSeconds(30),
                0
        );
    }
}
