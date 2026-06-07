package com.example.scheduler.api.dto;

import java.time.Instant;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt
) {
}
