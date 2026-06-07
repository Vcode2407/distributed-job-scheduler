package com.example.scheduler.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.scheduler.application.exception.NotFoundException;
import com.example.scheduler.application.port.in.RegisterWorkerCommand;
import com.example.scheduler.application.port.out.WorkerRepositoryPort;
import com.example.scheduler.domain.model.WorkerNode;
import com.example.scheduler.domain.model.WorkerStatus;
import com.example.scheduler.infrastructure.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkerApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-07T00:00:00Z");

    @Mock
    private WorkerRepositoryPort workers;

    private WorkerApplicationService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                null,
                new AppProperties.Scheduler(Duration.ofSeconds(30), 100, 100, Duration.ofSeconds(45)),
                null
        );
        service = new WorkerApplicationService(workers, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(workers.save(any(WorkerNode.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registersNewWorkerAsHealthy() {
        when(workers.findById("worker-1")).thenReturn(Optional.empty());

        WorkerNode worker = service.register(new RegisterWorkerCommand("worker-1", "host-a", 25, List.of("default")));

        assertThat(worker.status()).isEqualTo(WorkerStatus.HEALTHY);
        assertThat(worker.createdAt()).isEqualTo(NOW);
        assertThat(worker.lastHeartbeatAt()).isEqualTo(NOW);
        assertThat(worker.queues()).containsExactly("default");
        verify(workers).save(worker);
    }

    @Test
    void reRegisterPreservesCreatedAtAndVersion() {
        Instant createdAt = NOW.minusSeconds(3600);
        WorkerNode existing = new WorkerNode("worker-1", "old-host", WorkerStatus.OFFLINE, 1, List.of("default"), NOW.minusSeconds(60), createdAt, NOW.minusSeconds(60), 7);
        when(workers.findById("worker-1")).thenReturn(Optional.of(existing));

        WorkerNode worker = service.register(new RegisterWorkerCommand("worker-1", "new-host", 50, List.of("critical")));

        assertThat(worker.hostname()).isEqualTo("new-host");
        assertThat(worker.capacity()).isEqualTo(50);
        assertThat(worker.createdAt()).isEqualTo(createdAt);
        assertThat(worker.version()).isEqualTo(7);
        assertThat(worker.status()).isEqualTo(WorkerStatus.HEALTHY);
    }

    @Test
    void heartbeatUpdatesTimestampAndStatus() {
        WorkerNode offline = new WorkerNode("worker-1", "host-a", WorkerStatus.OFFLINE, 10, List.of("default"), NOW.minusSeconds(120), NOW.minusSeconds(3600), NOW.minusSeconds(120), 0);
        when(workers.findById("worker-1")).thenReturn(Optional.of(offline));

        WorkerNode heartbeat = service.heartbeat("worker-1");

        assertThat(heartbeat.status()).isEqualTo(WorkerStatus.HEALTHY);
        assertThat(heartbeat.lastHeartbeatAt()).isEqualTo(NOW);
        assertThat(heartbeat.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void missingWorkerThrowsNotFound() {
        when(workers.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getWorker("missing"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Worker not found");
    }

    @Test
    void marksOnlyTimedOutHealthyWorkersOffline() {
        WorkerNode staleHealthy = new WorkerNode("worker-1", "host-a", WorkerStatus.HEALTHY, 10, List.of("default"), NOW.minusSeconds(90), NOW.minusSeconds(3600), NOW.minusSeconds(90), 0);
        WorkerNode alreadyOffline = staleHealthy.markOffline(NOW.minusSeconds(80));
        when(workers.findHeartbeatBefore(NOW.minusSeconds(45))).thenReturn(List.of(staleHealthy, alreadyOffline));

        int count = service.markTimedOutWorkersOffline();

        assertThat(count).isEqualTo(2);
        ArgumentCaptor<WorkerNode> saved = ArgumentCaptor.forClass(WorkerNode.class);
        verify(workers).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo("worker-1");
        assertThat(saved.getValue().status()).isEqualTo(WorkerStatus.OFFLINE);
    }

    @Test
    void listWorkersDelegatesToRepository() {
        WorkerNode worker = new WorkerNode("worker-1", "host-a", WorkerStatus.HEALTHY, 10, List.of("default"), NOW, NOW, NOW, 0);
        when(workers.findAll()).thenReturn(List.of(worker));

        assertThat(service.listWorkers()).containsExactly(worker);
        verify(workers, never()).findHeartbeatBefore(any());
    }
}
