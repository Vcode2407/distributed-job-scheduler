package com.example.scheduler.domain.model;

public enum JobState {
    CREATED,
    QUEUED,
    SCHEDULED,
    LEASED,
    RUNNING,
    COMPLETED,
    FAILED,
    RETRYING,
    DEAD_LETTERED;

    public boolean isTerminal() {
        return this == COMPLETED || this == DEAD_LETTERED;
    }
}
