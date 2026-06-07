package com.example.scheduler.api.controller;

import com.example.scheduler.api.dto.AuthTokenRequest;
import com.example.scheduler.api.dto.AuthTokenResponse;
import com.example.scheduler.infrastructure.config.AppProperties;
import com.example.scheduler.infrastructure.security.JwtTokenService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtTokenService tokenService;
    private final AppProperties properties;
    private final Clock clock;

    public AuthController(JwtTokenService tokenService, AppProperties properties, Clock clock) {
        this.tokenService = tokenService;
        this.properties = properties;
        this.clock = clock;
    }

    @PostMapping("/token")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthTokenResponse token(@Valid @RequestBody AuthTokenRequest request) {
        if (!properties.security().devTokenEnabled()) {
            throw new AccessDeniedException("Development token endpoint is disabled");
        }
        List<String> roles = request.roles() == null || request.roles().isEmpty() ? List.of("ADMIN", "OPERATOR", "VIEWER") : request.roles();
        return new AuthTokenResponse(
                tokenService.issueToken(request.subject(), roles),
                "Bearer",
                clock.instant().plus(properties.security().tokenTtl())
        );
    }
}
