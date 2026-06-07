# Curl Examples

Set a token:

```bash
TOKEN=$(curl -s http://localhost:8080/api/auth/token \
  -H "Content-Type: application/json" \
  -d '{"subject":"operator","roles":["ADMIN","OPERATOR","VIEWER"]}' | jq -r .accessToken)
```

Create a queue:

```bash
curl -X POST http://localhost:8080/api/queues \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"default-dlq","description":"Dead-letter jobs"}'

curl -X POST http://localhost:8080/api/queues \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"email","description":"Outbound email jobs","deadLetterQueueName":"default-dlq"}'
```

List queues:

```bash
curl http://localhost:8080/api/queues -H "Authorization: Bearer $TOKEN"
```

Create a job:

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: email-001" \
  -d '{"name":"send-email","payload":{"to":"user@example.com"},"queueName":"email","priority":10,"maxAttempts":3}'
```

Create a delayed job:

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"delayed-email","payload":{"to":"user@example.com"},"queueName":"email","scheduledAt":"2026-06-07T12:00:00Z"}'
```

Create a recurring job:

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"heartbeat","payload":{},"queueName":"email","cronExpression":"0 */5 * * * *"}'
```

List jobs:

```bash
curl "http://localhost:8080/api/jobs?limit=20" -H "Authorization: Bearer $TOKEN"
```

Get a job:

```bash
curl http://localhost:8080/api/jobs/{jobId} -H "Authorization: Bearer $TOKEN"
```

Delete a job:

```bash
curl -X DELETE http://localhost:8080/api/jobs/{jobId} -H "Authorization: Bearer $TOKEN"
```

Register a worker:

```bash
curl -X POST http://localhost:8080/api/workers/register \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"id":"worker-1","hostname":"local","capacity":25,"queues":["email"]}'
```

Lease jobs:

```bash
curl -X POST http://localhost:8080/api/workers/worker-1/leases \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"limit":25}'
```

Start, complete, or fail a leased job:

```bash
curl -X POST http://localhost:8080/api/workers/worker-1/jobs/{jobId}/start \
  -H "Authorization: Bearer $TOKEN"

curl -X POST http://localhost:8080/api/workers/worker-1/jobs/{jobId}/complete \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"durationMs":120}'

curl -X POST http://localhost:8080/api/workers/worker-1/jobs/{jobId}/fail \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason":"dependency unavailable","durationMs":120}'
```

Workers and metrics:

```bash
curl http://localhost:8080/api/workers -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/workers/worker-1 -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/metrics -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus
```
