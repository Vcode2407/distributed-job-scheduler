package com.example.scheduler.infrastructure.persistence.adapter;

import com.example.scheduler.application.port.out.OutboxRepositoryPort;
import com.example.scheduler.domain.event.JobEvent;
import com.example.scheduler.infrastructure.persistence.entity.OutboxEventEntity;
import com.example.scheduler.infrastructure.persistence.repository.JpaOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxPersistenceAdapter implements OutboxRepositoryPort {

    private final JpaOutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxPersistenceAdapter(JpaOutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(JobEvent event) {
        try {
            repository.save(new OutboxEventEntity(
                    event.eventId(),
                    event.jobId(),
                    "Job",
                    event.eventType(),
                    objectMapper.writeValueAsString(event),
                    "PENDING",
                    0,
                    event.occurredAt()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize outbox event", exception);
        }
    }

    @Override
    public List<OutboxMessage> findPending(int limit) {
        return repository.findByStatusOrderByCreatedAtAsc("PENDING", PageRequest.of(0, limit)).stream()
                .map(event -> new OutboxMessage(event.getId(), event.getAggregateId(), event.getEventType(), event.getPayload(), event.getAttempts()))
                .toList();
    }

    @Override
    public void markPublished(UUID id, Instant publishedAt) {
        repository.findById(id).ifPresent(event -> {
            event.markPublished(publishedAt);
            repository.save(event);
        });
    }

    @Override
    public void markFailed(UUID id, String error) {
        repository.findById(id).ifPresent(event -> {
            event.markFailed(error);
            repository.save(event);
        });
    }
}
