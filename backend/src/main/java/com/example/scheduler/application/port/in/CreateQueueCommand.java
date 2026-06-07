package com.example.scheduler.application.port.in;

public record CreateQueueCommand(
        String name,
        String description,
        String deadLetterQueueName
) {
}
