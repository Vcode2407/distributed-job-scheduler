package com.example.scheduler.application.service;

import com.example.scheduler.application.exception.ConflictException;
import com.example.scheduler.application.exception.NotFoundException;
import com.example.scheduler.application.port.in.CreateJobCommand;
import com.example.scheduler.application.port.in.JobFilter;
import com.example.scheduler.application.port.out.JobExecutionHistoryRepositoryPort;
import com.example.scheduler.application.port.out.JobRepositoryPort;
import com.example.scheduler.application.port.out.OutboxRepositoryPort;
import com.example.scheduler.application.port.out.QueueRepositoryPort;
import com.example.scheduler.domain.event.JobEvent;
import com.example.scheduler.domain.model.Job;
import com.example.scheduler.domain.model.TaskQueue;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobApplicationService {

    private final JobRepositoryPort jobs;
    private final QueueRepositoryPort queues;
    private final JobExecutionHistoryRepositoryPort history;
    private final OutboxRepositoryPort outbox;
    private final Clock clock;

    public JobApplicationService(
            JobRepositoryPort jobs,
            QueueRepositoryPort queues,
            JobExecutionHistoryRepositoryPort history,
            OutboxRepositoryPort outbox,
            Clock clock
    ) {
        this.jobs = jobs;
        this.queues = queues;
        this.history = history;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public Job createJob(CreateJobCommand command) {
        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            var existing = jobs.findByIdempotencyKey(command.idempotencyKey());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        TaskQueue queue = queues.findByName(command.queueName())
                .orElseThrow(() -> new NotFoundException("Queue not found: " + command.queueName()));
        if (queue.paused()) {
            throw new ConflictException("Queue is paused: " + command.queueName());
        }

        Instant now = clock.instant();
        Instant scheduledAt = resolveScheduledAt(command, now);
        Job created = Job.create(
                queue.id(),
                command.name(),
                command.payload(),
                command.priority(),
                scheduledAt,
                blankToNull(command.cronExpression()),
                command.maxAttempts(),
                command.initialBackoffSeconds(),
                command.maxBackoffSeconds(),
                blankToNull(command.idempotencyKey()),
                now
        );
        Job scheduled = created.schedule(scheduledAt, now);
        Job saved = jobs.save(scheduled);
        history.record(saved.id(), null, created.state(), saved.state(), "Job submitted", null);
        outbox.append(JobEvent.of("JobSubmitted", saved.id(), saved.queueId(), now, Map.of("state", saved.state().name())));
        return saved;
    }

    @Transactional(readOnly = true)
    public Job getJob(UUID id) {
        return jobs.findById(id).orElseThrow(() -> new NotFoundException("Job not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Job> listJobs(JobFilter filter) {
        return jobs.find(filter);
    }

    @Transactional(readOnly = true)
    public long countJobs(JobFilter filter) {
        return jobs.count(filter);
    }

    @Transactional
    public void deleteJob(UUID id) {
        Job job = getJob(id);
        if (job.state() == com.example.scheduler.domain.model.JobState.RUNNING
                || job.state() == com.example.scheduler.domain.model.JobState.LEASED) {
            throw new ConflictException("Cannot delete an active leased/running job: " + id);
        }
        jobs.deleteById(id);
    }

    Job createNextRecurringOccurrence(Job completedJob, Instant now) {
        if (completedJob.cronExpression() == null || completedJob.cronExpression().isBlank()) {
            return null;
        }
        Instant next = nextCronInstant(completedJob.cronExpression(), now);
        Job recurrence = Job.create(
                completedJob.queueId(),
                completedJob.name(),
                completedJob.payload(),
                completedJob.priority(),
                next,
                completedJob.cronExpression(),
                completedJob.maxAttempts(),
                completedJob.initialBackoffSeconds(),
                completedJob.maxBackoffSeconds(),
                null,
                now
        ).schedule(next, now);
        Job saved = jobs.save(recurrence);
        history.record(saved.id(), null, com.example.scheduler.domain.model.JobState.CREATED, saved.state(), "Recurring occurrence scheduled", null);
        outbox.append(JobEvent.of("JobSubmitted", saved.id(), saved.queueId(), now, Map.of("recurring", true)));
        return saved;
    }

    private Instant resolveScheduledAt(CreateJobCommand command, Instant now) {
        if (command.cronExpression() != null && !command.cronExpression().isBlank()) {
            return nextCronInstant(command.cronExpression(), command.scheduledAt() == null ? now : command.scheduledAt());
        }
        return command.scheduledAt() == null ? now : command.scheduledAt();
    }

    private Instant nextCronInstant(String cronExpression, Instant from) {
        ZonedDateTime next = CronExpression.parse(cronExpression).next(ZonedDateTime.ofInstant(from, ZoneOffset.UTC));
        if (next == null) {
            throw new ConflictException("Cron expression has no next execution: " + cronExpression);
        }
        return next.toInstant();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
