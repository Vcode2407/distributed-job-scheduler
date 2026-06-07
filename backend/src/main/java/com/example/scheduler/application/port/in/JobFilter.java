package com.example.scheduler.application.port.in;

import com.example.scheduler.domain.model.JobState;

public record JobFilter(
        JobState state,
        String queueName,
        int limit,
        int offset
) {
}
