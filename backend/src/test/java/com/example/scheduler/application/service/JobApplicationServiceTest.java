package com.example.scheduler.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.scheduler.application.exception.ConflictException;
import com.example.scheduler.application.port.in.CreateJobCommand;
import com.example.scheduler.application.port.out.JobExecutionHistoryRepositoryPort;
import com.example.scheduler.application.port.out.JobRepositoryPort;
import com.example.scheduler.application.port.out.OutboxRepositoryPort;
import com.example.scheduler.application.port.out.QueueRepositoryPort;
import com.example.scheduler.domain.model.Job;
import com.example.scheduler.domain.model.JobState;
import com.example.scheduler.domain.model.TaskQueue;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-07T00:00:00Z");
    private static final UUID QUEUE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private JobRepositoryPort jobs;

    @Mock
    private QueueRepositoryPort queues;

    @Mock
    private JobExecutionHistoryRepositoryPort history;

    @Mock
    private OutboxRepositoryPort outbox;

    private JobApplicationService service;

    @BeforeEach
    void setUp() {
        service = new JobApplicationService(jobs, queues, history, outbox, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(queues.findByName("default")).thenReturn(Optional.of(new TaskQueue(
                QUEUE_ID,
                "default",
                "Default queue",
                false,
                null,
                NOW,
                NOW,
                0
        )));
        lenient().when(jobs.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsImmediateJobInQueuedState() {
        Job job = service.createJob(new CreateJobCommand(
                "email",
                "{\"to\":\"user@example.com\"}",
                "default",
                10,
                NOW,
                null,
                3,
                30,
                3600,
                "idem-1"
        ));

        assertThat(job.state()).isEqualTo(JobState.QUEUED);
        assertThat(job.priority()).isEqualTo(10);
        assertThat(job.idempotencyKey()).isEqualTo("idem-1");
        verify(history).record(job.id(), null, JobState.CREATED, JobState.QUEUED, "Job submitted", null);
        verify(outbox).append(any());
    }

    @Test
    void createsDelayedJobInScheduledState() {
        Instant future = NOW.plusSeconds(600);

        Job job = service.createJob(new CreateJobCommand(
                "delayed",
                "{}",
                "default",
                0,
                future,
                null,
                3,
                30,
                3600,
                null
        ));

        assertThat(job.state()).isEqualTo(JobState.SCHEDULED);
        assertThat(job.scheduledAt()).isEqualTo(future);
    }

    @Test
    void returnsExistingJobForRepeatedIdempotencyKey() {
        Job existing = sampleJob(JobState.QUEUED, "idem-1");
        when(jobs.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

        Job job = service.createJob(new CreateJobCommand(
                "email",
                "{}",
                "default",
                10,
                NOW,
                null,
                3,
                30,
                3600,
                "idem-1"
        ));

        assertThat(job).isSameAs(existing);
        verify(jobs, never()).save(any(Job.class));
    }

    @Test
    void rejectsSubmissionToPausedQueue() {
        when(queues.findByName("default")).thenReturn(Optional.of(new TaskQueue(
                QUEUE_ID,
                "default",
                "Default queue",
                true,
                null,
                NOW,
                NOW,
                0
        )));

        assertThatThrownBy(() -> service.createJob(new CreateJobCommand(
                "email",
                "{}",
                "default",
                10,
                NOW,
                null,
                3,
                30,
                3600,
                null
        ))).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Queue is paused");
    }

    @Test
    void deletesCompletedJob() {
        Job completed = sampleJob(JobState.COMPLETED, null);
        when(jobs.findById(completed.id())).thenReturn(Optional.of(completed));

        service.deleteJob(completed.id());

        verify(jobs).deleteById(completed.id());
    }

    @Test
    void refusesToDeleteActiveJob() {
        Job running = sampleJob(JobState.RUNNING, null);
        when(jobs.findById(running.id())).thenReturn(Optional.of(running));

        assertThatThrownBy(() -> service.deleteJob(running.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot delete an active");
        verify(jobs, never()).deleteById(running.id());
    }

    @Test
    void schedulesNextRecurringOccurrenceAfterCompletion() {
        Job completed = new Job(
                UUID.randomUUID(),
                QUEUE_ID,
                "recurring",
                "{}",
                JobState.COMPLETED,
                5,
                NOW,
                "0 * * * * *",
                0,
                3,
                30,
                3600,
                null,
                null,
                null,
                null,
                NOW,
                NOW,
                0
        );

        Job next = service.createNextRecurringOccurrence(completed, NOW);

        assertThat(next).isNotNull();
        assertThat(next.state()).isEqualTo(JobState.SCHEDULED);
        assertThat(next.scheduledAt()).isEqualTo(Instant.parse("2026-06-07T00:01:00Z"));
        assertThat(next.cronExpression()).isEqualTo("0 * * * * *");
        verify(history).record(next.id(), null, JobState.CREATED, JobState.SCHEDULED, "Recurring occurrence scheduled", null);
    }

    @Test
    void ignoresNonRecurringCompletion() {
        assertThat(service.createNextRecurringOccurrence(sampleJob(JobState.COMPLETED, null), NOW)).isNull();
    }

    private Job sampleJob(JobState state, String idempotencyKey) {
        return new Job(
                UUID.randomUUID(),
                QUEUE_ID,
                "billing",
                "{}",
                state,
                20,
                NOW,
                null,
                0,
                3,
                30,
                3600,
                idempotencyKey,
                null,
                null,
                null,
                NOW,
                NOW,
                0
        );
    }
}
