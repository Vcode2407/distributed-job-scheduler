# Runtime Readiness

Date: 2026-06-07

This document covers local runtime validation, deployment readiness, and end-to-end execution for the Distributed Job Scheduling Platform.

## Inspected Runtime Configuration

- `docker-compose.yml`
- `backend/Dockerfile`
- `frontend/Dockerfile`
- `frontend/nginx.conf`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-test.yml`
- `backend/src/main/resources/db/migration/V1__init.sql`
- Kafka configuration in `backend/src/main/java/com/example/scheduler/infrastructure/kafka`
- Redis lock configuration in `backend/src/main/java/com/example/scheduler/infrastructure/redis`

## Dependency Readiness

| Dependency | Local Status | Evidence |
| --- | --- | --- |
| Docker Desktop | Present and running | Docker context `desktop-linux` |
| Docker Engine | Reachable for Compose | Docker Engine `29.5.2`; Compose `v5.1.4` |
| PostgreSQL | Running in Compose | `projectfullstack-postgres-1` healthy |
| Kafka | Running in Compose | `projectfullstack-kafka-1` healthy; topics exist |
| Redis | Running in Compose | `projectfullstack-redis-1` healthy |
| Java 21 | Present | Temurin `21.0.11` under `.tools/jdk-21` |
| Bash | Present via Git Bash | `C:\Program Files\Git\bin\bash.exe`; not on PowerShell PATH |
| Node.js | Present | `v20.20.2` |
| curl | Present | Windows curl `8.19.0` |
| Testcontainers | Blocked on this host | Docker CLI works, but Testcontainers' Java client gets an invalid empty Docker info response from the Windows named pipe |

## Docker Fixes Applied

- Removed hard-coded `container_name` values to avoid host-level name collisions.
- Added backend/frontend `.dockerignore` files; build contexts dropped to about `13 KB` and under `1 KB`.
- Added Kafka and ZooKeeper named volumes.
- Added ZooKeeper healthcheck and made Kafka wait for ZooKeeper health.
- Fixed ZooKeeper healthcheck syntax for Confluent `cub zk-ready localhost 2181`.
- Fixed frontend healthcheck to use `http://127.0.0.1/health`.
- Raised local-only API rate limits for load validation.
- Increased local outbox relay throughput with `APP_OUTBOX_BATCH_SIZE=1000` and `APP_OUTBOX_RELAY_DELAY_MS=200`.
- Disabled local OTLP exporting unless a collector is configured.

## Runtime Evidence

Executed successfully:

```bash
docker compose config --quiet
docker compose build
docker compose up -d --force-recreate
scripts/health-check.sh
scripts/smoke-test.sh
node scripts/load-test.mjs
```

Health check:

```text
Checking postgres   OK
Checking redis      OK
Checking kafka      OK
Checking backend    OK
Checking frontend   OK
All runtime dependencies are healthy.
```

Smoke test:

```text
Smoke test passed.
Created queue:  smoke-1780817660
Worker:         smoke-worker-1780817660
Job:            d31eac74-fde5-42a6-b6b9-17703e3e4d72
Final state:    COMPLETED
```

Full load test:

```json
{
  "jobCount": 10000,
  "workerCount": 100,
  "completed": 10000,
  "failures": 0,
  "retries": 0,
  "throughputJobsPerSecond": 58.47,
  "latencyMs": {
    "p50": 284,
    "p95": 3010,
    "p99": 5192
  }
}
```

Kafka topics:

```text
__consumer_offsets
job-events
job-events.dlq
job-events.retry
```

Kafka `job-events` offsets:

```text
job-events:0:6752
job-events:1:6816
job-events:2:6589
job-events:3:7026
job-events:4:6632
job-events:5:6709
```

Outbox status after relay drain:

```text
PUBLISHED | 40524
```

Prometheus counters:

```text
scheduler_jobs_leased_total 10100.0
scheduler_jobs_completed_total 10100.0
scheduler_jobs_failed_total 10.0
scheduler_jobs_retried_total 10.0
scheduler_jobs_dead_lettered_total 0.0
```

The failed/retried counters are from an earlier rate-limit validation failure before the Compose rate limit was raised. The later 100-job sanity run and 10,000-job full run both completed with zero script-level failures and retries.

## Startup Instructions

From Git Bash, Linux, or macOS:

```bash
scripts/start-local.sh
```

Manual equivalent:

```bash
docker compose up -d --build
scripts/health-check.sh
```

Useful URLs:

- Frontend: http://localhost:3000
- Backend: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Prometheus metrics: http://localhost:8080/actuator/prometheus

## Smoke Test Instructions

Run:

```bash
scripts/smoke-test.sh
```

Expected responses:

- `POST /api/auth/token`: HTTP `201`, JSON with `accessToken`, `tokenType`, `expiresAt`.
- `POST /api/queues`: HTTP `201`, queue `id`, `paused=false`.
- `POST /api/workers/register`: HTTP `200`, `status=HEALTHY`.
- `POST /api/jobs`: HTTP `201`, `state=QUEUED`.
- `POST /api/workers/{worker}/leases`: HTTP `200`, one job in `state=LEASED`.
- `POST /api/workers/{worker}/jobs/{job}/start`: HTTP `200`, `state=RUNNING`.
- `POST /api/workers/{worker}/jobs/{job}/complete`: HTTP `200`, `state=COMPLETED`.
- `GET /api/metrics`: HTTP `200`, completed jobs and created queue visible.

## Load Test Instructions

Run:

```bash
node scripts/load-test.mjs
```

Override defaults:

```bash
API_BASE=http://localhost:8080 JOB_COUNT=10000 WORKER_COUNT=100 SUBMIT_CONCURRENCY=100 LEASE_BATCH=25 node scripts/load-test.mjs
```

## Troubleshooting

Docker unreachable:

```bash
docker info
docker context ls
```

Backend readiness down:

```bash
docker compose logs app
curl -v http://localhost:8080/actuator/health/readiness
```

Kafka issues:

```bash
docker compose logs zookeeper kafka
docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

Frontend health issues:

```bash
docker compose exec frontend wget -qO- http://127.0.0.1/health
curl http://localhost:3000/health
```

Reset local data:

```bash
docker compose down -v
docker compose up -d --build
```

## Production Deployment Checklist

- Replace Compose secrets with a secrets manager.
- Disable `APP_DEV_TOKEN_ENABLED`.
- Use a production identity provider for JWT issuance.
- Use managed PostgreSQL with backups, PITR, and migration gates.
- Use a multi-broker Kafka cluster with replication factor at least `3`.
- Use managed Redis or Redis Sentinel/Cluster.
- Configure an OpenTelemetry collector before enabling trace export.
- Scrape `/actuator/prometheus` through controlled authenticated access.
- Ship logs to centralized logging.
- Configure TLS, private networking, CPU/memory limits, rolling updates, and graceful termination.
- Add backup/restore drills and operational runbooks for Kafka lag, Redis outages, and stuck leases.
