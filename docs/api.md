# REST API Reference

All endpoints except health, Swagger, and `POST /api/auth/token` require JWT authentication.

Roles:

- `ADMIN`: full access
- `OPERATOR`: operational write access
- `VIEWER`: read-only dashboard access

## Auth

### POST `/api/auth/token`

Development-only token endpoint controlled by `APP_DEV_TOKEN_ENABLED`.

Request:

```json
{
  "subject": "operator",
  "roles": ["ADMIN", "OPERATOR", "VIEWER"]
}
```

Response:

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresAt": "2026-06-07T10:00:00Z"
}
```

## Jobs

### POST `/api/jobs`

Creates immediate, delayed, or recurring jobs. Supports the `Idempotency-Key` header.

Request:

```json
{
  "name": "send-email",
  "payload": {"to":"user@example.com"},
  "queueName": "default",
  "priority": 20,
  "scheduledAt": "2026-06-07T12:00:00Z",
  "cronExpression": "0 */5 * * * *",
  "maxAttempts": 5,
  "initialBackoffSeconds": 30,
  "maxBackoffSeconds": 3600
}
```

Behavior:

- No `scheduledAt` and no cron expression creates a `QUEUED` job.
- Future `scheduledAt` creates a `SCHEDULED` job.
- `cronExpression` schedules the next occurrence and creates future occurrences after completion.

### GET `/api/jobs/{id}`

Returns one job.

### GET `/api/jobs`

Query parameters:

- `state`
- `queueName`
- `limit`
- `offset`

### DELETE `/api/jobs/{id}`

Deletes a non-leased, non-running job.

## Queues

### POST `/api/queues`

```json
{
  "name": "email",
  "description": "Outbound email work",
  "deadLetterQueueName": "default-dlq"
}
```

### GET `/api/queues`

Lists queues.

### PUT `/api/queues/{id}/pause`

Pauses future leasing from the queue.

### PUT `/api/queues/{id}/resume`

Resumes future leasing.

## Workers

### POST `/api/workers/register`

```json
{
  "id": "worker-1",
  "hostname": "worker-1.internal",
  "capacity": 50,
  "queues": ["default", "email"]
}
```

### PUT `/api/workers/{id}/heartbeat`

Refreshes worker health.

### POST `/api/workers/{id}/leases`

```json
{
  "limit": 100
}
```

Returns leased jobs ordered by priority then FIFO creation time.

### POST `/api/workers/{workerId}/jobs/{jobId}/start`

Transitions a leased job to `RUNNING`.

### POST `/api/workers/{workerId}/jobs/{jobId}/complete`

```json
{
  "durationMs": 1250
}
```

Transitions a running job to `COMPLETED`.

### POST `/api/workers/{workerId}/jobs/{jobId}/fail`

```json
{
  "reason": "HTTP 503 from dependency",
  "durationMs": 500
}
```

Transitions to `RETRYING` or `DEAD_LETTERED` depending on remaining attempts.

### GET `/api/workers`

Lists workers.

### GET `/api/workers/{id}`

Returns one worker.

## Metrics

### GET `/api/metrics`

Returns:

- job counts by state
- queue pause status
- worker counts by status
- throughput buckets
- average processing time
- failure rate
- retry rate
