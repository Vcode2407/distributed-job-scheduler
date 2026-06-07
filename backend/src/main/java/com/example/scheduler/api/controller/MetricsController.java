package com.example.scheduler.api.controller;

import com.example.scheduler.api.dto.MetricsResponse;
import com.example.scheduler.api.mapper.ApiMapper;
import com.example.scheduler.application.service.MetricsApplicationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsApplicationService metrics;

    public MetricsController(MetricsApplicationService metrics) {
        this.metrics = metrics;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public MetricsResponse metrics() {
        return ApiMapper.toResponse(metrics.snapshot());
    }
}
