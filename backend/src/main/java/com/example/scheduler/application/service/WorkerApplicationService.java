package com.example.scheduler.application.service;

import com.example.scheduler.application.exception.NotFoundException;
import com.example.scheduler.application.port.in.RegisterWorkerCommand;
import com.example.scheduler.application.port.out.WorkerRepositoryPort;
import com.example.scheduler.domain.model.WorkerNode;
import com.example.scheduler.domain.model.WorkerStatus;
import com.example.scheduler.infrastructure.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerApplicationService {

    private final WorkerRepositoryPort workers;
    private final AppProperties properties;
    private final Clock clock;

    public WorkerApplicationService(WorkerRepositoryPort workers, AppProperties properties, Clock clock) {
        this.workers = workers;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public WorkerNode register(RegisterWorkerCommand command) {
        Instant now = clock.instant();
        WorkerNode worker = new WorkerNode(
                command.id(),
                command.hostname(),
                WorkerStatus.HEALTHY,
                command.capacity(),
                List.copyOf(command.queues()),
                now,
                workers.findById(command.id()).map(WorkerNode::createdAt).orElse(now),
                now,
                workers.findById(command.id()).map(WorkerNode::version).orElse(0L)
        );
        return workers.save(worker);
    }

    @Transactional
    public WorkerNode heartbeat(String workerId) {
        WorkerNode worker = getWorker(workerId);
        return workers.save(worker.heartbeat(clock.instant()));
    }

    @Transactional(readOnly = true)
    public WorkerNode getWorker(String workerId) {
        return workers.findById(workerId).orElseThrow(() -> new NotFoundException("Worker not found: " + workerId));
    }

    @Transactional(readOnly = true)
    public List<WorkerNode> listWorkers() {
        return workers.findAll();
    }

    @Transactional
    public int markTimedOutWorkersOffline() {
        Instant deadline = clock.instant().minus(properties.scheduler().heartbeatTimeout());
        List<WorkerNode> timedOut = workers.findHeartbeatBefore(deadline);
        for (WorkerNode worker : timedOut) {
            if (worker.status() != WorkerStatus.OFFLINE) {
                workers.save(worker.markOffline(clock.instant()));
            }
        }
        return timedOut.size();
    }
}
