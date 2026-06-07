# Distributed Job Scheduling Platform

Production-oriented distributed job scheduler inspired by Google Cloud Tasks. It supports immediate, delayed, and cron recurring jobs; multiple queues; priority ordering; worker leasing; retries with exponential backoff; dead-lettering; JWT/RBAC; Redis locks; Kafka events; PostgreSQL state; Prometheus/Actuator/OpenTelemetry observability; and a React operations dashboard.

## Stack

- Java 21, Spring Boot 3, Spring Security, Spring Data JPA
- PostgreSQL, Flyway, optimistic locking, `FOR UPDATE SKIP LOCKED`
- Apache Kafka with transactional outbox relay
- Redis distributed locks
- React, TypeScript, Vite, Recharts, lucide-react
- Docker and Docker Compose
- JUnit 5 and Testcontainers

## Project Structure

```text
backend/src/main/java/com/example/scheduler
+-- domain          # Job state machine and core models
+-- application     # Use cases and ports
+-- infrastructure  # JPA, Kafka, Redis, security, scheduling adapters
+-- api             # REST controllers, DTOs, validation, exception mapping

frontend/src
+-- api
+-- components
+-- App.tsx
```

## Quick Start

```bash
docker compose up --build
```

Open:

- Dashboard: http://localhost:3000
- Backend: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Prometheus metrics: http://localhost:8080/actuator/prometheus

Create a development JWT:

```bash
curl -s http://localhost:8080/api/auth/token \
  -H "Content-Type: application/json" \
  -d '{"subject":"operator","roles":["ADMIN","OPERATOR","VIEWER"]}'
```

Use the returned token as `Authorization: Bearer <token>`.

## Example Job Flow

```bash
TOKEN="<token>"

curl -X POST http://localhost:8080/api/jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-1" \
  -d '{
    "name": "send-email",
    "payload": {"to":"user@example.com"},
    "queueName": "default",
    "priority": 20,
    "maxAttempts": 5
  }'

curl -X POST http://localhost:8080/api/workers/register \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"id":"worker-1","hostname":"local","capacity":10,"queues":["default"]}'

curl -X POST http://localhost:8080/api/workers/worker-1/leases \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"limit":10}'
```

Full curl coverage is in [docs/curl-examples.md](docs/curl-examples.md).

## Reliability Model

- The database is the source of truth for job state.
- Job transitions are validated by `JobStateMachine` before persistence.
- Workers lease jobs in batches using Postgres row locks and Redis coordination.
- Kafka publication uses a transactional outbox table so job writes and event records commit together.
- Kafka consumers acknowledge after processing for at-least-once delivery.
- Expired leases are recovered by a scheduled crash-recovery loop.
- Retries use exponential backoff and move to `dead_letter_jobs` after max attempts.
- Queue pause/resume is enforced at lease time.

## Performance Targets

The platform is designed for 100,000+ jobs/day by combining:

- Batch leasing (`APP_LEASE_BATCH_SIZE`, default `100`)
- Hikari connection pooling
- Indexed due-job scans by queue, state, schedule time, priority, and creation time
- Horizontal app and worker scaling
- Kafka partitioning for event fan-out
- Redis locks for cross-node coordination

## Tests

```bash
cd backend
mvn test

cd ../frontend
npm install
npm run build
```

Backend tests include domain, application, API, repository/Flyway, Kafka, Redis locking, concurrency, retry, DLQ, and Testcontainers integration coverage. JaCoCo reports are generated under `backend/target/site/jacoco`.

## Documentation

- Architecture diagrams: [docs/architecture.md](docs/architecture.md)
- REST API reference: [docs/api.md](docs/api.md)
- Verification report: [docs/verification-report.md](docs/verification-report.md)
- Runtime readiness: [docs/runtime-readiness.md](docs/runtime-readiness.md)
- Curl examples: [docs/curl-examples.md](docs/curl-examples.md)
- Schema validation report: [docs/schema-validation-report.md](docs/schema-validation-report.md)
- Benchmark/load test report: [docs/benchmark-report.md](docs/benchmark-report.md)

## Future Improvements

- Add tenant isolation and per-tenant quotas.
- Add first-class worker SDKs.
- Extend OpenTelemetry trace correlation across downstream worker SDKs.
- Add queue-level rate limits and concurrency caps.
- Add Kafka exactly-once producer transactions for downstream consumers that can participate.
- Add archive partitioning for long-lived job history.
- Replace development token endpoint with a production identity provider.
