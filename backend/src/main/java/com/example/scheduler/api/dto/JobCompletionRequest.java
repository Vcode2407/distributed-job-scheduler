package com.example.scheduler.api.dto;

import jakarta.validation.constraints.Min;

public record JobCompletionRequest(
        @Min(0) Long durationMs
) {
}
