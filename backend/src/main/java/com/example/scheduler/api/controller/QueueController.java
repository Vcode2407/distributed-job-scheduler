package com.example.scheduler.api.controller;

import com.example.scheduler.api.dto.CreateQueueRequest;
import com.example.scheduler.api.dto.QueueResponse;
import com.example.scheduler.api.mapper.ApiMapper;
import com.example.scheduler.application.port.in.CreateQueueCommand;
import com.example.scheduler.application.service.QueueApplicationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queues")
public class QueueController {

    private final QueueApplicationService queues;

    public QueueController(QueueApplicationService queues) {
        this.queues = queues;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<QueueResponse> createQueue(@Valid @RequestBody CreateQueueRequest request) {
        QueueResponse response = ApiMapper.toResponse(queues.createQueue(new CreateQueueCommand(
                request.name(),
                request.description(),
                request.deadLetterQueueName()
        )));
        return ResponseEntity.created(URI.create("/api/queues/" + response.id())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public List<QueueResponse> listQueues() {
        return queues.listQueues().stream().map(ApiMapper::toResponse).toList();
    }

    @PutMapping("/{id}/pause")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public QueueResponse pause(@PathVariable UUID id) {
        return ApiMapper.toResponse(queues.pause(id));
    }

    @PutMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public QueueResponse resume(@PathVariable UUID id) {
        return ApiMapper.toResponse(queues.resume(id));
    }
}
