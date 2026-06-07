package com.example.scheduler.infrastructure.health;

import com.example.scheduler.application.port.out.QueueRepositoryPort;
import com.example.scheduler.application.port.out.WorkerRepositoryPort;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class SchedulerHealthIndicator implements HealthIndicator {

    private final QueueRepositoryPort queues;
    private final WorkerRepositoryPort workers;

    public SchedulerHealthIndicator(QueueRepositoryPort queues, WorkerRepositoryPort workers) {
        this.queues = queues;
        this.workers = workers;
    }

    @Override
    public Health health() {
        try {
            return Health.up()
                    .withDetail("queues", queues.findAll().size())
                    .withDetail("workers", workers.findAll().size())
                    .build();
        } catch (RuntimeException exception) {
            return Health.down(exception).build();
        }
    }
}
