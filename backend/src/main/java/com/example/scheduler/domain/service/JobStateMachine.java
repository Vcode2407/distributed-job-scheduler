package com.example.scheduler.domain.service;

import com.example.scheduler.domain.exception.InvalidStateTransitionException;
import com.example.scheduler.domain.model.JobState;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

public final class JobStateMachine {

    private static final EnumMap<JobState, Set<JobState>> ALLOWED_TRANSITIONS = new EnumMap<>(JobState.class);

    static {
        ALLOWED_TRANSITIONS.put(JobState.CREATED, EnumSet.of(JobState.QUEUED, JobState.SCHEDULED, JobState.FAILED));
        ALLOWED_TRANSITIONS.put(JobState.QUEUED, EnumSet.of(JobState.LEASED, JobState.FAILED));
        ALLOWED_TRANSITIONS.put(JobState.SCHEDULED, EnumSet.of(JobState.QUEUED, JobState.LEASED, JobState.FAILED));
        ALLOWED_TRANSITIONS.put(JobState.LEASED, EnumSet.of(JobState.RUNNING, JobState.RETRYING, JobState.FAILED));
        ALLOWED_TRANSITIONS.put(JobState.RUNNING, EnumSet.of(JobState.COMPLETED, JobState.RETRYING, JobState.FAILED));
        ALLOWED_TRANSITIONS.put(JobState.FAILED, EnumSet.of(JobState.DEAD_LETTERED, JobState.RETRYING));
        ALLOWED_TRANSITIONS.put(JobState.RETRYING, EnumSet.of(JobState.LEASED, JobState.FAILED));
        ALLOWED_TRANSITIONS.put(JobState.COMPLETED, EnumSet.noneOf(JobState.class));
        ALLOWED_TRANSITIONS.put(JobState.DEAD_LETTERED, EnumSet.noneOf(JobState.class));
    }

    private JobStateMachine() {
    }

    public static boolean canTransition(JobState from, JobState to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void assertTransition(JobState from, JobState to) {
        if (!canTransition(from, to)) {
            throw new InvalidStateTransitionException(from, to);
        }
    }
}
