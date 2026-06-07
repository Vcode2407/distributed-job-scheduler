# Schema Validation Report

Validation is implemented in `SchemaValidationTest` using PostgreSQL Testcontainers and Flyway. It verifies:

- Tables: `jobs`, `queues`, `workers`, `job_execution_history`, `dead_letter_jobs`, `outbox_events`
- Foreign keys for queues, jobs, workers, history, and dead-letter rows
- Indexes for idempotency, due jobs, state scans, lease expiry, history lookups, DLQ lookups, and pending outbox events
- Optimistic locking columns on `jobs`, `queues`, `workers`, and `outbox_events`

Current local status:

- Live Compose PostgreSQL starts successfully.
- Flyway applies `V1__init.sql` during backend startup.
- `mvn verify` passes: 46 tests discovered, 35 executed, 11 skipped.
- Testcontainers-backed schema validation still skips on this Windows host because Testcontainers' Java Docker client cannot validate Docker Desktop through the Windows named pipe, even though Docker Compose works.

Run the Testcontainers schema validation in WSL/Linux CI or another Docker environment where Testcontainers can access the Docker daemon directly:

```bash
cd backend
mvn -Dtest=SchemaValidationTest test
```
