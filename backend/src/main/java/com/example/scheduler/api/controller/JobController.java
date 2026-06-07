package com.example.scheduler.api.controller;

import com.example.scheduler.api.dto.CreateJobRequest;
import com.example.scheduler.api.dto.JobListResponse;
import com.example.scheduler.api.dto.JobResponse;
import com.example.scheduler.api.mapper.ApiMapper;
import com.example.scheduler.application.port.in.CreateJobCommand;
import com.example.scheduler.application.port.in.JobFilter;
import com.example.scheduler.application.service.JobApplicationService;
import com.example.scheduler.domain.model.JobState;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobApplicationService jobs;

    public JobController(JobApplicationService jobs) {
        this.jobs = jobs;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<JobResponse> createJob(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateJobRequest request
    ) {
        String effectiveIdempotencyKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? request.idempotencyKey()
                : idempotencyKey;
        var command = new CreateJobCommand(
                request.name(),
                payloadAsJson(request.payload()),
                request.queueName() == null || request.queueName().isBlank() ? "default" : request.queueName(),
                request.priority() == null ? 0 : request.priority(),
                request.scheduledAt(),
                request.cronExpression(),
                request.maxAttempts() == null ? 3 : request.maxAttempts(),
                request.initialBackoffSeconds() == null ? 30 : request.initialBackoffSeconds(),
                request.maxBackoffSeconds() == null ? 3600 : request.maxBackoffSeconds(),
                effectiveIdempotencyKey
        );
        JobResponse response = ApiMapper.toResponse(jobs.createJob(command));
        return ResponseEntity.created(URI.create("/api/jobs/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public JobResponse getJob(@PathVariable UUID id) {
        return ApiMapper.toResponse(jobs.getJob(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public JobListResponse listJobs(
            @RequestParam(required = false) JobState state,
            @RequestParam(required = false) String queueName,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        JobFilter filter = new JobFilter(state, queueName, Math.max(1, Math.min(limit, 500)), Math.max(0, offset));
        return new JobListResponse(
                jobs.listJobs(filter).stream().map(ApiMapper::toResponse).toList(),
                jobs.countJobs(filter),
                filter.limit(),
                filter.offset()
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<Void> deleteJob(@PathVariable UUID id) {
        jobs.deleteJob(id);
        return ResponseEntity.noContent().build();
    }

    private String payloadAsJson(JsonNode payload) {
        return payload == null || payload.isNull() ? "{}" : payload.toString();
    }
}
