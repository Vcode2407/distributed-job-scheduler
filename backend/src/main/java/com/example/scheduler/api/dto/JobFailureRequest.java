package com.example.scheduler.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record JobFailureRequest(
        @NotBlank String reason,
        @Min(0) Long durationMs
) {
}
