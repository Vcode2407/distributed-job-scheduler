package com.example.scheduler.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record AuthTokenRequest(
        @NotBlank String subject,
        List<String> roles
) {
}
