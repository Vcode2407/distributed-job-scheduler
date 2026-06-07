package com.example.scheduler.domain.exception;

import com.example.scheduler.domain.model.JobState;

public class InvalidStateTransitionException extends DomainException {

    public InvalidStateTransitionException(JobState from, JobState to) {
        super("Invalid job state transition from " + from + " to " + to);
    }
}
