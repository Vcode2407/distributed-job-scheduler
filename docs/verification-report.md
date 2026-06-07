# Verification Report

Date: 2026-06-07

## Evidence Summary

- `mvn clean compile`: passed with Java 21.
- `mvn test`: passed.
- `mvn verify`: passed and produced `backend/target/distributed-job-scheduler-0.1.0-SNAPSHOT.jar`.
- Test count: 46 discovered, 35 executed, 11 skipped because Testcontainers cannot validate Docker Desktop through the Windows named pipe.
- JaCoCo report generated at `backend/target/site/jacoco/index.html`.
- Frontend `npm run build`: passed.
- Frontend `npm run lint`: passed.
- Frontend `npm audit --json`: passed with 0 vulnerabilities.
- `docker compose config --quiet`: passed.
- `docker compose build`: passed.
- `docker compose up -d --force-recreate`: passed.
- `scripts/health-check.sh`: passed.
- `scripts/smoke-test.sh`: passed.
- `node scripts/load-test.mjs`: passed for 10,000 jobs and 100 workers.

## Runtime Results

- All Compose services are healthy: postgres, redis, zookeeper, kafka, backend, frontend.
- Smoke test completed job `d31eac74-fde5-42a6-b6b9-17703e3e4d72`.
- Full load test completed `10000 / 10000` jobs.
- Load throughput: `58.47` jobs/sec.
- Load latency: p50 `284ms`, p95 `3010ms`, p99 `5192ms`.
- Kafka topics exist: `job-events`, `job-events.retry`, `job-events.dlq`.
- Outbox drained to `40524` published events.

## Fixes Applied

- Fixed Surefire/Jacoco `argLine` wiring.
- Removed unused global JPA auditing.
- Added explicit Kafka primary/retry/DLQ topics.
- Added Kafka DLQ publishing after consumer retry exhaustion.
- Disabled Redis repository auto-scanning.
- Added service, schema, Kafka, Redis lock, retry, DLQ, and lease-expiry tests.
- Added Prometheus business metrics and scheduler health indicator.
- Added OpenTelemetry dependencies/configuration.
- Removed hard-coded Compose `container_name` values.
- Added backend/frontend `.dockerignore` files.
- Fixed ZooKeeper and frontend healthchecks.
- Raised local-only rate limits and outbox relay throughput for runtime validation.
- Added startup, health, smoke, and load scripts.

## Remaining Limitation

Docker Compose runtime works on this host, but Testcontainers-backed Maven tests still skip. Docker CLI uses context `desktop-linux` at `npipe:////./pipe/dockerDesktopLinuxEngine`; Testcontainers receives an invalid empty Docker info response from the Windows named pipe. Run those tests in WSL/Linux CI for full container-backed Maven verification.
