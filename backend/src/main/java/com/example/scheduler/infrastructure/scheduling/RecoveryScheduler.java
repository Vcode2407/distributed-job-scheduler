package com.example.scheduler.infrastructure.scheduling;

import com.example.scheduler.application.service.JobLeaseService;
import com.example.scheduler.application.service.WorkerApplicationService;
import com.example.scheduler.infrastructure.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecoveryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecoveryScheduler.class);

    private final JobLeaseService jobLeaseService;
    private final WorkerApplicationService workerApplicationService;
    private final AppProperties properties;

    public RecoveryScheduler(JobLeaseService jobLeaseService, WorkerApplicationService workerApplicationService, AppProperties properties) {
        this.jobLeaseService = jobLeaseService;
        this.workerApplicationService = workerApplicationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${APP_RECOVERY_DELAY_MS:15000}")
    public void recoverExpiredLeases() {
        int recovered = jobLeaseService.recoverExpiredLeases(properties.scheduler().leaseBatchSize());
        if (recovered > 0) {
            LOGGER.info("Recovered {} expired job leases", recovered);
        }
    }

    @Scheduled(fixedDelayString = "${APP_WORKER_HEALTH_DELAY_MS:10000}")
    public void markUnhealthyWorkers() {
        int offline = workerApplicationService.markTimedOutWorkersOffline();
        if (offline > 0) {
            LOGGER.info("Marked {} timed-out workers offline", offline);
        }
    }
}
