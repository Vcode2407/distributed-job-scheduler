package com.example.scheduler.application.service;

import com.example.scheduler.application.exception.ConflictException;
import com.example.scheduler.application.exception.NotFoundException;
import com.example.scheduler.application.port.in.CreateQueueCommand;
import com.example.scheduler.application.port.out.QueueRepositoryPort;
import com.example.scheduler.domain.model.TaskQueue;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueueApplicationService {

    private final QueueRepositoryPort queues;
    private final Clock clock;

    public QueueApplicationService(QueueRepositoryPort queues, Clock clock) {
        this.queues = queues;
        this.clock = clock;
    }

    @Transactional
    public TaskQueue createQueue(CreateQueueCommand command) {
        if (queues.existsByName(command.name())) {
            throw new ConflictException("Queue already exists: " + command.name());
        }

        UUID deadLetterQueueId = null;
        if (command.deadLetterQueueName() != null && !command.deadLetterQueueName().isBlank()) {
            deadLetterQueueId = queues.findByName(command.deadLetterQueueName())
                    .orElseThrow(() -> new NotFoundException("Dead-letter queue not found: " + command.deadLetterQueueName()))
                    .id();
        }

        Instant now = clock.instant();
        TaskQueue queue = new TaskQueue(
                UUID.randomUUID(),
                command.name(),
                command.description(),
                false,
                deadLetterQueueId,
                now,
                now,
                0
        );
        return queues.save(queue);
    }

    @Transactional(readOnly = true)
    public List<TaskQueue> listQueues() {
        return queues.findAll();
    }

    @Transactional
    public TaskQueue pause(UUID queueId) {
        TaskQueue queue = queues.findById(queueId)
                .orElseThrow(() -> new NotFoundException("Queue not found: " + queueId));
        return queues.save(queue.pause(clock.instant()));
    }

    @Transactional
    public TaskQueue resume(UUID queueId) {
        TaskQueue queue = queues.findById(queueId)
                .orElseThrow(() -> new NotFoundException("Queue not found: " + queueId));
        return queues.save(queue.resume(clock.instant()));
    }
}
