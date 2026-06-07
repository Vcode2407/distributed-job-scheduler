package com.example.scheduler.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.scheduler.domain.exception.InvalidStateTransitionException;
import com.example.scheduler.domain.model.JobState;
import org.junit.jupiter.api.Test;

class JobStateMachineTest {

    @Test
    void allowsValidLeasingAndCompletionPath() {
        assertThat(JobStateMachine.canTransition(JobState.CREATED, JobState.QUEUED)).isTrue();
        assertThat(JobStateMachine.canTransition(JobState.QUEUED, JobState.LEASED)).isTrue();
        assertThat(JobStateMachine.canTransition(JobState.LEASED, JobState.RUNNING)).isTrue();
        assertThat(JobStateMachine.canTransition(JobState.RUNNING, JobState.COMPLETED)).isTrue();
    }

    @Test
    void rejectsTerminalStateMutation() {
        assertThatThrownBy(() -> JobStateMachine.assertTransition(JobState.COMPLETED, JobState.RETRYING))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("COMPLETED")
                .hasMessageContaining("RETRYING");
    }

    @Test
    void allowsRetryAndDeadLetterPath() {
        assertThat(JobStateMachine.canTransition(JobState.RUNNING, JobState.RETRYING)).isTrue();
        assertThat(JobStateMachine.canTransition(JobState.RUNNING, JobState.FAILED)).isTrue();
        assertThat(JobStateMachine.canTransition(JobState.FAILED, JobState.DEAD_LETTERED)).isTrue();
    }
}
