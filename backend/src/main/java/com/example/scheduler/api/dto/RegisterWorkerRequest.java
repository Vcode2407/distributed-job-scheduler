package com.example.scheduler.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RegisterWorkerRequest(
        @NotBlank @Size(max = 160) String id,
        @NotBlank @Size(max = 255) String hostname,
        @Min(1) int capacity,
        List<String> queues
) {
}
