package com.example.scheduler.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SchemaValidationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jobscheduler")
            .withUsername("jobscheduler")
            .withPassword("jobscheduler");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesExpectedTablesForeignKeysIndexesAndVersionColumns() {
        assertThat(tables()).contains(
                "jobs",
                "queues",
                "workers",
                "job_execution_history",
                "dead_letter_jobs",
                "outbox_events"
        );

        assertThat(indexes()).contains(
                "ux_jobs_idempotency_key",
                "ix_jobs_due",
                "ix_jobs_state_updated",
                "ix_jobs_lease_expiry",
                "ix_job_execution_history_job",
                "ix_job_execution_history_worker",
                "ix_dead_letter_jobs_queue",
                "ix_outbox_events_pending"
        );

        assertThat(foreignKeys()).contains(
                "queues_dead_letter_queue_id_fkey",
                "jobs_queue_id_fkey",
                "jobs_leased_by_fkey",
                "job_execution_history_job_id_fkey",
                "dead_letter_jobs_job_id_fkey",
                "dead_letter_jobs_queue_id_fkey"
        );

        assertThat(versionedTables()).contains("jobs", "queues", "workers", "outbox_events");
    }

    private List<String> tables() {
        return jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                """, String.class);
    }

    private List<String> indexes() {
        return jdbcTemplate.queryForList("""
                select indexname
                from pg_indexes
                where schemaname = 'public'
                """, String.class);
    }

    private List<String> foreignKeys() {
        return jdbcTemplate.queryForList("""
                select conname
                from pg_constraint
                where contype = 'f'
                """, String.class);
    }

    private List<String> versionedTables() {
        return jdbcTemplate.queryForList("""
                select table_name
                from information_schema.columns
                where table_schema = 'public'
                  and column_name = 'version'
                """, String.class);
    }
}
