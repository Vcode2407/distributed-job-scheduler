package com.example.scheduler.application.service;

import com.example.scheduler.application.exception.ConflictException;
import com.example.scheduler.application.exception.NotFoundException;
import com.example.scheduler.application.port.out.DeadLetterJobRepositoryPort;
import com.example.scheduler.application.port.out.DistributedLockPort;
import com.example.scheduler.application.port.out.JobExecutionHistoryRepositoryPort;
import com.example.scheduler.application.port.out.JobRepositoryPort;
import com.example.scheduler.application.port.out.OutboxRepositoryPort;
import com.example.scheduler.application.port.out.WorkerRepositoryPort;
import com.example.scheduler.domain.event.JobEvent;
import com.example.scheduler.domain.model.Job;
import com.example.scheduler.domain.model.JobState;
import com.example.scheduler.domain.model.WorkerNode;
import com.example.scheduler.infrastructure.config.AppProperties;
import com.example.scheduler.infrastructure.metrics.SchedulerMetrics;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobLeaseService {

    private final JobRepositoryPort jobs;
    private final WorkerRepositoryPort workers;
    private final JobExecutionHistoryRepositoryPort history;
    private final DeadLetterJobRepositoryPort deadLetters;
    private final OutboxRepositoryPort outbox;
    private final DistributedLockPort locks;
    private final JobApplicationService jobApplicationService;
    private final AppProperties properties;
    private final SchedulerMetrics metrics;
    private final Clock clock;

    public JobLeaseService(
            JobRepositoryPort jobs,
            WorkerRepositoryPort workers,
            JobExecutionHistoryRepositoryPort history,
            DeadLetterJobRepositoryPort deadLetters,
            OutboxRepositoryPort outbox,
            DistributedLockPort locks,
            JobApplicationService jobApplicationService,
            AppProperties properties,
            SchedulerMetrics metrics,
            Clock clock
    ) {
        this.jobs = jobs;
        this.workers = workers;
        this.history = history;
        this.deadLetters = deadLetters;
        this.outbox = outbox;
        this.locks = locks;
        this.jobApplicationService = jobApplicationService;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public List<Job> leaseDueJobs(String workerId, int requestedLimit) {
        WorkerNode worker = workers.findById(workerId)
                .orElseThrow(() -> new NotFoundException("Worker not registered: " + workerId));

        int limit = Math.max(1, Math.min(requestedLimit, Math.min(worker.capacity(), properties.scheduler().leaseBatchSize())));
        String lockKey = "leases:" + workerId;
        String owner = UUID.randomUUID().toString();
        if (!locks.acquire(lockKey, owner, Duration.ofSeconds(10))) {
            return List.of();
        }

        try {
            Instant now = clock.instant();
            Instant leaseUntil = now.plus(properties.scheduler().leaseDuration());
            List<Job> due = jobs.findDueJobsForUpdate(now, worker.queues(), limit);
            List<Job> leased = new ArrayList<>(due.size());
            for (Job job : due) {
                Job next = job.leaseTo(workerId, leaseUntil, now);
                Job saved = jobs.save(next);
                history.record(saved.id(), workerId, job.state(), saved.state(), "Job leased", null);
                outbox.append(JobEvent.of("JobLeased", saved.id(), saved.queueId(), now, Map.of("workerId", workerId)));
                metrics.recordLeased();
                leased.add(saved);
            }
            return leased;
        } finally {
            locks.release(lockKey, owner);
        }
    }

    @Transactional
    public Job markRunning(String workerId, UUID jobId) {
        Job job = getWorkerJob(workerId, jobId);
        ensureLeaseActive(job);
        Instant now = clock.instant();
        Job running = jobs.save(job.markRunning(now));
        history.record(job.id(), workerId, job.state(), running.state(), "Job started", null);
        outbox.append(JobEvent.of("JobStarted", running.id(), running.queueId(), now, Map.of("workerId", workerId)));
        return running;
    }

    @Transactional
    public Job markCompleted(String workerId, UUID jobId, Long durationMs) {
        Job job = getWorkerJob(workerId, jobId);
        Instant now = clock.instant();
        Job completed = jobs.save(job.markCompleted(now));
        history.record(job.id(), workerId, job.state(), completed.state(), "Job completed", durationMs);
        outbox.append(JobEvent.of("JobCompleted", completed.id(), completed.queueId(), now, Map.of("workerId", workerId)));
        metrics.recordCompleted(durationMs);
        jobApplicationService.createNextRecurringOccurrence(completed, now);
        return completed;
    }

    @Transactional
    public Job markFailed(String workerId, UUID jobId, String reason, Long durationMs) {
        Job job = getWorkerJob(workerId, jobId);
        return failJob(job, workerId, reason, durationMs);
    }

    @Transactional
    public int recoverExpiredLeases(int limit) {
        Instant now = clock.instant();
        List<Job> expired = jobs.findExpiredLeasesForUpdate(now, limit);
        for (Job job : expired) {
            failJob(job, job.leasedBy(), "Lease expired during worker crash recovery", null);
        }
        return expired.size();
    }

    private Job failJob(Job job, String workerId, String reason, Long durationMs) {
        Instant now = clock.instant();
        if (job.canRetry()) {
            Instant retryAt = now.plus(job.nextBackoff());
            Job retrying = jobs.save(job.retry(reason, retryAt, now));
            history.record(job.id(), workerId, job.state(), retrying.state(), reason, durationMs);
            outbox.append(JobEvent.of("JobRetrying", retrying.id(), retrying.queueId(), now, Map.of("reason", reason)));
            metrics.recordFailure(durationMs);
            metrics.recordRetry();
            return retrying;
        }

        Job failed = job.fail(reason, now);
        history.record(job.id(), workerId, job.state(), failed.state(), reason, durationMs);
        Job dead = jobs.save(failed.deadLetter(reason, now));
        deadLetters.save(dead, reason);
        history.record(job.id(), workerId, JobState.FAILED, dead.state(), reason, durationMs);
        outbox.append(JobEvent.of("JobDeadLettered", dead.id(), dead.queueId(), now, Map.of("reason", reason)));
        metrics.recordFailure(durationMs);
        metrics.recordDeadLettered();
        return dead;
    }

    private Job getWorkerJob(String workerId, UUID jobId) {
        Job job = jobs.findById(jobId).orElseThrow(() -> new NotFoundException("Job not found: " + jobId));
        if (!job.isLeasedBy(workerId)) {
            throw new ConflictException("Job is not leased by worker: " + workerId);
        }
        return job;
    }

    private void ensureLeaseActive(Job job) {
        if (job.leaseExpiresAt() == null || job.leaseExpiresAt().isBefore(clock.instant())) {
            throw new ConflictException("Job lease has expired: " + job.id());
        }
    }
}
