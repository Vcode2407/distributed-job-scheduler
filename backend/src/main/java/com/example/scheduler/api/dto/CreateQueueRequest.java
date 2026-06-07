package com.example.scheduler.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateQueueRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @Size(max = 120) String deadLetterQueueName
) {
}
