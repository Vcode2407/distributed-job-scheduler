package com.example.scheduler.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record LeaseRequest(
        @Min(1) @Max(500) int limit
) {
}
