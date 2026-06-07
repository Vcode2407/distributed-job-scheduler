package com.example.scheduler.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RedisDistributedLockAdapterTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Test
    void lockAllowsSingleOwnerAndHonorsReleaseOwnership() {
        RedisDistributedLockAdapter locks = adapter();

        assertThat(locks.acquire("job:1", "owner-a", Duration.ofSeconds(5))).isTrue();
        assertThat(locks.acquire("job:1", "owner-b", Duration.ofSeconds(5))).isFalse();

        locks.release("job:1", "owner-b");
        assertThat(locks.acquire("job:1", "owner-b", Duration.ofSeconds(5))).isFalse();

        locks.release("job:1", "owner-a");
        assertThat(locks.acquire("job:1", "owner-b", Duration.ofSeconds(5))).isTrue();
    }

    @Test
    void concurrentAcquisitionPreventsDuplicateExecution() throws Exception {
        RedisDistributedLockAdapter locks = adapter();
        String lockKey = "job:" + UUID.randomUUID();
        var executor = Executors.newFixedThreadPool(20);
        try {
            List<Callable<Boolean>> attempts = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> (Callable<Boolean>) () -> locks.acquire(lockKey, "owner-" + index, Duration.ofSeconds(5)))
                    .toList();

            long acquired = executor.invokeAll(attempts).stream()
                    .filter(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .count();

            assertThat(acquired).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void lockExpiresAfterTtl() throws Exception {
        RedisDistributedLockAdapter locks = adapter();
        assertThat(locks.acquire("ttl-job", "owner-a", Duration.ofMillis(300))).isTrue();

        Thread.sleep(500L);

        assertThat(locks.acquire("ttl-job", "owner-b", Duration.ofSeconds(5))).isTrue();
    }

    private RedisDistributedLockAdapter adapter() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        return new RedisDistributedLockAdapter(new StringRedisTemplate(connectionFactory));
    }
}
