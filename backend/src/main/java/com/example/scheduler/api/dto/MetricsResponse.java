package com.example.scheduler.api.dto;

import com.example.scheduler.application.port.out.JobExecutionHistoryRepositoryPort;
import com.example.scheduler.application.service.MetricsApplicationService;
import com.example.scheduler.domain.model.JobState;
import com.example.scheduler.domain.model.WorkerStatus;
import java.util.List;
import java.util.Map;

public record MetricsResponse(
        Map<JobState, Long> jobsByState,
        List<MetricsApplicationService.QueueMetric> queues,
        Map<WorkerStatus, Long> workersByStatus,
        List<JobExecutionHistoryRepositoryPort.ThroughputPoint> throughput,
        double averageProcessingTimeMillis,
        double failureRate,
        double retryRate
) {
}
