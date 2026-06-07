package com.example.scheduler.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class SchedulerMetrics {

    private final Counter leasedJobs;
    private final Counter completedJobs;
    private final Counter failedJobs;
    private final Counter retriedJobs;
    private final Counter deadLetteredJobs;
    private final Timer processingDuration;

    public SchedulerMetrics(MeterRegistry registry) {
        this.leasedJobs = Counter.builder("scheduler_jobs_leased_total")
                .description("Total jobs leased to workers")
                .register(registry);
        this.completedJobs = Counter.builder("scheduler_jobs_completed_total")
                .description("Total jobs completed")
                .register(registry);
        this.failedJobs = Counter.builder("scheduler_jobs_failed_total")
                .description("Total job failures observed")
                .register(registry);
        this.retriedJobs = Counter.builder("scheduler_jobs_retried_total")
                .description("Total jobs scheduled for retry")
                .register(registry);
        this.deadLetteredJobs = Counter.builder("scheduler_jobs_dead_lettered_total")
                .description("Total jobs moved to dead letter")
                .register(registry);
        this.processingDuration = Timer.builder("scheduler_job_processing_duration")
                .description("Worker-reported job processing duration")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void recordLeased() {
        leasedJobs.increment();
    }

    public void recordCompleted(Long durationMs) {
        completedJobs.increment();
        recordDuration(durationMs);
    }

    public void recordFailure(Long durationMs) {
        failedJobs.increment();
        recordDuration(durationMs);
    }

    public void recordRetry() {
        retriedJobs.increment();
    }

    public void recordDeadLettered() {
        deadLetteredJobs.increment();
    }

    private void recordDuration(Long durationMs) {
        if (durationMs != null && durationMs >= 0) {
            processingDuration.record(Duration.ofMillis(durationMs));
        }
    }
}
