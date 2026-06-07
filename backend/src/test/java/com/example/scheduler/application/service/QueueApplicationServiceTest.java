package com.example.scheduler.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.scheduler.application.exception.ConflictException;
import com.example.scheduler.application.exception.NotFoundException;
import com.example.scheduler.application.port.in.CreateQueueCommand;
import com.example.scheduler.application.port.out.QueueRepositoryPort;
import com.example.scheduler.domain.model.TaskQueue;
import java.time.Clock;
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
class QueueApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-07T00:00:00Z");
    private static final UUID DLQ_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Mock
    private QueueRepositoryPort queues;

    private QueueApplicationService service;

    @BeforeEach
    void setUp() {
        service = new QueueApplicationService(queues, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(queues.save(any(TaskQueue.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsQueueWithDeadLetterReference() {
        when(queues.existsByName("critical")).thenReturn(false);
        when(queues.findByName("critical-dlq")).thenReturn(Optional.of(queue(DLQ_ID, "critical-dlq", false, null)));

        TaskQueue created = service.createQueue(new CreateQueueCommand("critical", "Critical jobs", "critical-dlq"));

        assertThat(created.name()).isEqualTo("critical");
        assertThat(created.deadLetterQueueId()).isEqualTo(DLQ_ID);
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(created.updatedAt()).isEqualTo(NOW);
        verify(queues).save(created);
    }

    @Test
    void rejectsDuplicateQueueName() {
        when(queues.existsByName("default")).thenReturn(true);

        assertThatThrownBy(() -> service.createQueue(new CreateQueueCommand("default", "Default", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Queue already exists");
    }

    @Test
    void rejectsUnknownDeadLetterQueue() {
        when(queues.existsByName("critical")).thenReturn(false);
        when(queues.findByName("missing-dlq")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createQueue(new CreateQueueCommand("critical", "Critical", "missing-dlq")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Dead-letter queue not found");
    }

    @Test
    void pausesAndResumesQueue() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(queues.findById(id)).thenReturn(Optional.of(queue(id, "default", false, null)));

        TaskQueue paused = service.pause(id);

        assertThat(paused.paused()).isTrue();

        when(queues.findById(id)).thenReturn(Optional.of(paused));
        TaskQueue resumed = service.resume(id);

        assertThat(resumed.paused()).isFalse();
        ArgumentCaptor<TaskQueue> saved = ArgumentCaptor.forClass(TaskQueue.class);
        verify(queues, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(TaskQueue::updatedAt).containsOnly(NOW);
    }

    @Test
    void listsQueuesFromRepository() {
        TaskQueue queue = queue(UUID.randomUUID(), "default", false, null);
        when(queues.findAll()).thenReturn(List.of(queue));

        assertThat(service.listQueues()).containsExactly(queue);
    }

    private TaskQueue queue(UUID id, String name, boolean paused, UUID deadLetterQueueId) {
        return new TaskQueue(id, name, name + " queue", paused, deadLetterQueueId, NOW.minusSeconds(60), NOW.minusSeconds(30), 0);
    }
}
