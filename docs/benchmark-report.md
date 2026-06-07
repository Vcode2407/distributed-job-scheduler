# Benchmark Report

Date: 2026-06-07

## Load Test Harness

Script: `scripts/load-test.mjs`

Default target:

- 100 workers
- 10,000 jobs
- submit concurrency 100
- lease batch size 25

Run:

```bash
docker compose up -d --build
scripts/health-check.sh
node scripts/load-test.mjs
```

## Verified Results

Sanity run:

```json
{
  "jobCount": 100,
  "workerCount": 10,
  "completed": 100,
  "failures": 0,
  "retries": 0,
  "throughputJobsPerSecond": 21.44,
  "latencyMs": {
    "p50": 63,
    "p95": 237,
    "p99": 357
  }
}
```

Full run:

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
  },
  "schedulerMetrics": {
    "failureRate": 0,
    "retryRate": 0,
    "averageProcessingTimeMillis": 1.004059004059004
  }
}
```

Notes:

- Initial load sanity failed with `429 rate_limit_exceeded`; Compose local rate limits were raised for validation.
- The outbox relay published all `40524` accumulated events after relay tuning.
- p95/p99 latency is high under 100 concurrent local workers and should be improved before production SLO commitments.
