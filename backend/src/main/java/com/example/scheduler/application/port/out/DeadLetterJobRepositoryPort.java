package com.example.scheduler.application.port.out;

import com.example.scheduler.domain.model.Job;

public interface DeadLetterJobRepositoryPort {

    void save(Job job, String reason);
}
