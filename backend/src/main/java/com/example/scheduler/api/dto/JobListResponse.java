package com.example.scheduler.api.dto;

import java.util.List;

public record JobListResponse(
        List<JobResponse> items,
        long total,
        int limit,
        int offset
) {
}
