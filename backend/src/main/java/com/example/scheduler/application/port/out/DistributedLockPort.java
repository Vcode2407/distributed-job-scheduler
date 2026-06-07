package com.example.scheduler.application.port.out;

import java.time.Duration;

public interface DistributedLockPort {

    boolean acquire(String key, String owner, Duration ttl);

    void release(String key, String owner);
}
