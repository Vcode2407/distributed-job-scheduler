package com.example.scheduler.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.scheduler.application.port.in.CreateJobCommand;
import com.example.scheduler.application.port.in.RegisterWorkerCommand;
import com.example.scheduler.application.service.JobApplicationService;
import com.example.scheduler.application.service.JobLeaseService;
import com.example.scheduler.application.service.WorkerApplicationService;
import com.example.scheduler.domain.model.Job;
import com.example.scheduler.domain.model.JobState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class JobFlowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jobscheduler")
            .withUsername("jobscheduler")
            .withPassword("jobscheduler");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.kafka.job-events-topic", () -> "job-events-integration");
        registry.add("app.kafka.job-events-retry-topic", () -> "job-events-integration.retry");
        registry.add("app.kafka.job-events-dlq-topic", () -> "job-events-integration.dlq");
        registry.add("app.scheduler.lease-duration", () -> "PT1S");
    }

    @Autowired
    private JobApplicationService jobs;

    @Autowired
    private WorkerApplicationService workers;

    @Autowired
    private JobLeaseService leases;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void leasesRunsAndCompletesJob() {
        String workerId = "worker-success-" + UUID.randomUUID();
        workers.register(new RegisterWorkerCommand(workerId, "localhost", 10, List.of()));
        var created = jobs.createJob(new CreateJobCommand(
                "invoice",
                "{}",
                "default",
                5,
                Instant.now().minusSeconds(1),
                null,
                3,
                1,
                30,
                "integration-" + UUID.randomUUID()
        ));

        var leased = leases.leaseDueJobs(workerId, 5);
        var running = leases.markRunning(workerId, leased.getFirst().id());
        var completed = leases.markCompleted(workerId, running.id(), 42L);

        assertThat(created.state()).isEqualTo(JobState.QUEUED);
        assertThat(leased).hasSize(1);
        assertThat(running.state()).isEqualTo(JobState.RUNNING);
        assertThat(completed.state()).isEqualTo(JobState.COMPLETED);
    }

    @Test
    void failedProcessingMovesJobToRetryingThenCompletesOnNextLease() throws Exception {
        String workerId = "worker-retry-" + UUID.randomUUID();
        workers.register(new RegisterWorkerCommand(workerId, "localhost", 10, List.of()));
        Job created = submitImmediateJob("retry-job", 3);
        Job leased = leases.leaseDueJobs(workerId, 1).getFirst();
        leases.markRunning(workerId, leased.id());

        Job retrying = leases.markFailed(workerId, created.id(), "dependency timeout", 100L);

        assertThat(retrying.state()).isEqualTo(JobState.RETRYING);
        assertThat(retrying.attemptCount()).isEqualTo(1);

        Thread.sleep(1_200L);
        Job retryLease = leases.leaseDueJobs(workerId, 1).getFirst();
        Job running = leases.markRunning(workerId, retryLease.id());
        Job completed = leases.markCompleted(workerId, running.id(), 80L);

        assertThat(completed.state()).isEqualTo(JobState.COMPLETED);
    }

    @Test
    void failedProcessingAfterMaxAttemptsMovesToDeadLetter() {
        String workerId = "worker-dlq-" + UUID.randomUUID();
        workers.register(new RegisterWorkerCommand(workerId, "localhost", 10, List.of()));
        Job created = submitImmediateJob("dlq-job", 1);
        Job leased = leases.leaseDueJobs(workerId, 1).getFirst();
        leases.markRunning(workerId, leased.id());

        Job dead = leases.markFailed(workerId, created.id(), "permanent failure", 100L);

        Integer deadLetterRows = jdbcTemplate.queryForObject(
                "select count(*) from dead_letter_jobs where job_id = ?",
                Integer.class,
                dead.id()
        );
        assertThat(dead.state()).isEqualTo(JobState.DEAD_LETTERED);
        assertThat(deadLetterRows).isEqualTo(1);
    }

    @Test
    void concurrentWorkersDoNotLeaseDuplicateJobs() throws Exception {
        String firstWorker = "worker-a-" + UUID.randomUUID();
        String secondWorker = "worker-b-" + UUID.randomUUID();
        workers.register(new RegisterWorkerCommand(firstWorker, "localhost", 100, List.of()));
        workers.register(new RegisterWorkerCommand(secondWorker, "localhost", 100, List.of()));
        for (int index = 0; index < 50; index++) {
            submitImmediateJob("concurrent-" + index, 3);
        }

        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<List<Job>>> tasks = List.of(
                    () -> leases.leaseDueJobs(firstWorker, 50),
                    () -> leases.leaseDueJobs(secondWorker, 50)
            );
            List<Job> leased = new ArrayList<>();
            for (var future : executor.invokeAll(tasks)) {
                leased.addAll(future.get());
            }

            HashSet<UUID> uniqueIds = new HashSet<>();
            leased.forEach(job -> uniqueIds.add(job.id()));

            assertThat(leased).hasSize(uniqueIds.size());
            assertThat(leased).hasSizeGreaterThanOrEqualTo(50);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expiredLeaseIsRecoveredForRetry() throws Exception {
        String workerId = "worker-expiry-" + UUID.randomUUID();
        workers.register(new RegisterWorkerCommand(workerId, "localhost", 10, List.of()));
        Job created = submitImmediateJob("lease-expiry", 3);
        leases.leaseDueJobs(workerId, 1);

        Thread.sleep(1_200L);
        int recovered = leases.recoverExpiredLeases(10);
        Job recoveredJob = jobs.getJob(created.id());

        assertThat(recovered).isGreaterThanOrEqualTo(1);
        assertThat(recoveredJob.state()).isEqualTo(JobState.RETRYING);
    }

    private Job submitImmediateJob(String name, int maxAttempts) {
        return jobs.createJob(new CreateJobCommand(
                name,
                "{}",
                "default",
                5,
                Instant.now().minusSeconds(1),
                null,
                maxAttempts,
                1,
                30,
                name + "-" + UUID.randomUUID()
        ));
    }
}
