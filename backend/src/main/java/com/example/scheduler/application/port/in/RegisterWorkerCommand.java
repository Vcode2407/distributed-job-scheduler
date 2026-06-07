package com.example.scheduler.application.port.in;

import java.util.List;

public record RegisterWorkerCommand(
        String id,
        String hostname,
        int capacity,
        List<String> queues
) {
}
