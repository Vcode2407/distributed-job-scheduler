package com.example.scheduler.api.controller;

import com.example.scheduler.api.dto.JobCompletionRequest;
import com.example.scheduler.api.dto.JobFailureRequest;
import com.example.scheduler.api.dto.JobResponse;
import com.example.scheduler.api.dto.LeaseRequest;
import com.example.scheduler.api.dto.RegisterWorkerRequest;
import com.example.scheduler.api.dto.WorkerResponse;
import com.example.scheduler.api.mapper.ApiMapper;
import com.example.scheduler.application.port.in.RegisterWorkerCommand;
import com.example.scheduler.application.service.JobLeaseService;
import com.example.scheduler.application.service.WorkerApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workers")
public class WorkerController {

    private final WorkerApplicationService workers;
    private final JobLeaseService leases;

    public WorkerController(WorkerApplicationService workers, JobLeaseService leases) {
        this.workers = workers;
        this.leases = leases;
    }

    @PostMapping("/register")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public WorkerResponse register(@Valid @RequestBody RegisterWorkerRequest request) {
        return ApiMapper.toResponse(workers.register(new RegisterWorkerCommand(
                request.id(),
                request.hostname(),
                request.capacity(),
                request.queues() == null ? List.of("default") : request.queues()
        )));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public List<WorkerResponse> listWorkers() {
        return workers.listWorkers().stream().map(ApiMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public WorkerResponse getWorker(@PathVariable String id) {
        return ApiMapper.toResponse(workers.getWorker(id));
    }

    @PutMapping("/{id}/heartbeat")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public WorkerResponse heartbeat(@PathVariable String id) {
        return ApiMapper.toResponse(workers.heartbeat(id));
    }

    @PostMapping("/{id}/leases")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public List<JobResponse> leaseJobs(@PathVariable String id, @Valid @RequestBody LeaseRequest request) {
        return leases.leaseDueJobs(id, request.limit()).stream().map(ApiMapper::toResponse).toList();
    }

    @PostMapping("/{workerId}/jobs/{jobId}/start")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public JobResponse startJob(@PathVariable String workerId, @PathVariable UUID jobId) {
        return ApiMapper.toResponse(leases.markRunning(workerId, jobId));
    }

    @PostMapping("/{workerId}/jobs/{jobId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public JobResponse completeJob(
            @PathVariable String workerId,
            @PathVariable UUID jobId,
            @Valid @RequestBody JobCompletionRequest request
    ) {
        return ApiMapper.toResponse(leases.markCompleted(workerId, jobId, request.durationMs()));
    }

    @PostMapping("/{workerId}/jobs/{jobId}/fail")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public JobResponse failJob(
            @PathVariable String workerId,
            @PathVariable UUID jobId,
            @Valid @RequestBody JobFailureRequest request
    ) {
        return ApiMapper.toResponse(leases.markFailed(workerId, jobId, request.reason(), request.durationMs()));
    }
}
