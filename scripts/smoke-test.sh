#!/usr/bin/env bash
set -Eeuo pipefail

API_BASE="${API_BASE:-http://localhost:8080}"
RUN_ID="${RUN_ID:-$(date +%s)}"
QUEUE_NAME="${QUEUE_NAME:-smoke-$RUN_ID}"
WORKER_ID="${WORKER_ID:-smoke-worker-$RUN_ID}"

die() {
  echo "ERROR: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "$1 is required but was not found on PATH"
}

json_get() {
  local expression="$1"
  node -e "
    const fs = require('fs');
    const input = fs.readFileSync(0, 'utf8');
    const data = JSON.parse(input);
    const value = (function(data) { return $expression; })(data);
    if (value === undefined || value === null) process.exit(2);
    if (typeof value === 'object') console.log(JSON.stringify(value));
    else console.log(value);
  "
}

api() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local response
  local status
  local tmp
  tmp="$(mktemp)"

  if [[ -n "$body" ]]; then
    status="$(curl -sS -o "$tmp" -w '%{http_code}' \
      -X "$method" "$API_BASE$path" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -H "Idempotency-Key: smoke-$RUN_ID" \
      -d "$body")"
  else
    status="$(curl -sS -o "$tmp" -w '%{http_code}' \
      -X "$method" "$API_BASE$path" \
      -H "Authorization: Bearer $TOKEN")"
  fi

  response="$(cat "$tmp")"
  rm -f "$tmp"
  if [[ "$status" -lt 200 || "$status" -ge 300 ]]; then
    echo "$response" >&2
    die "$method $path returned HTTP $status"
  fi
  printf '%s' "$response"
}

expect_json_value() {
  local body="$1"
  local expression="$2"
  local expected="$3"
  local actual
  actual="$(printf '%s' "$body" | json_get "$expression")"
  [[ "$actual" == "$expected" ]] || die "Expected $expression to be '$expected' but got '$actual'"
}

require_cmd curl
require_cmd node

echo "Issuing development JWT..."
TOKEN_RESPONSE="$(curl -sS -X POST "$API_BASE/api/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"subject":"smoke-test","roles":["ADMIN","OPERATOR","VIEWER"]}')"
TOKEN="$(printf '%s' "$TOKEN_RESPONSE" | json_get 'data.accessToken')"
[[ -n "$TOKEN" ]] || die "Token response did not contain accessToken"
echo "Expected token response: HTTP 201 with accessToken, tokenType=Bearer, expiresAt"

echo "Creating queue $QUEUE_NAME..."
QUEUE_RESPONSE="$(api POST /api/queues "{\"name\":\"$QUEUE_NAME\",\"description\":\"Smoke test queue\",\"deadLetterQueueName\":\"default-dlq\"}")"
expect_json_value "$QUEUE_RESPONSE" 'data.name' "$QUEUE_NAME"
expect_json_value "$QUEUE_RESPONSE" 'String(data.paused)' "false"
echo "Expected queue response: HTTP 201 with name=$QUEUE_NAME and paused=false"

echo "Registering worker $WORKER_ID..."
WORKER_RESPONSE="$(api POST /api/workers/register "{\"id\":\"$WORKER_ID\",\"hostname\":\"local-smoke\",\"capacity\":5,\"queues\":[\"$QUEUE_NAME\"]}")"
expect_json_value "$WORKER_RESPONSE" 'data.id' "$WORKER_ID"
expect_json_value "$WORKER_RESPONSE" 'data.status' "HEALTHY"
echo "Expected worker response: HTTP 200 with id=$WORKER_ID and status=HEALTHY"

echo "Creating job..."
JOB_RESPONSE="$(api POST /api/jobs "{\"name\":\"smoke-job\",\"payload\":{\"runId\":\"$RUN_ID\"},\"queueName\":\"$QUEUE_NAME\",\"priority\":10,\"maxAttempts\":3,\"initialBackoffSeconds\":1,\"maxBackoffSeconds\":30}")"
JOB_ID="$(printf '%s' "$JOB_RESPONSE" | json_get 'data.id')"
expect_json_value "$JOB_RESPONSE" 'data.state' "QUEUED"
echo "Expected job response: HTTP 201 with state=QUEUED and id=$JOB_ID"

echo "Leasing job..."
LEASE_RESPONSE="$(api POST "/api/workers/$WORKER_ID/leases" '{"limit":1}')"
LEASED_JOB_ID="$(printf '%s' "$LEASE_RESPONSE" | json_get 'data[0].id')"
LEASED_STATE="$(printf '%s' "$LEASE_RESPONSE" | json_get 'data[0].state')"
[[ "$LEASED_JOB_ID" == "$JOB_ID" ]] || die "Expected leased job $JOB_ID but got $LEASED_JOB_ID"
[[ "$LEASED_STATE" == "LEASED" ]] || die "Expected leased job state LEASED but got $LEASED_STATE"
echo "Expected lease response: HTTP 200 with one job in state=LEASED"

echo "Starting job..."
RUNNING_RESPONSE="$(api POST "/api/workers/$WORKER_ID/jobs/$JOB_ID/start")"
expect_json_value "$RUNNING_RESPONSE" 'data.state' "RUNNING"
echo "Expected start response: HTTP 200 with state=RUNNING"

echo "Completing job..."
COMPLETED_RESPONSE="$(api POST "/api/workers/$WORKER_ID/jobs/$JOB_ID/complete" '{"durationMs":42}')"
expect_json_value "$COMPLETED_RESPONSE" 'data.state' "COMPLETED"
echo "Expected complete response: HTTP 200 with state=COMPLETED"

echo "Verifying metrics..."
METRICS_RESPONSE="$(api GET /api/metrics)"
printf '%s' "$METRICS_RESPONSE" | node -e "
  const fs = require('fs');
  const metrics = JSON.parse(fs.readFileSync(0, 'utf8'));
  const completed = metrics.jobsByState?.COMPLETED ?? 0;
  const queues = metrics.queues ?? [];
  if (completed < 1) {
    console.error('Expected jobsByState.COMPLETED >= 1, got', completed);
    process.exit(1);
  }
  if (!queues.some((queue) => queue.name === '$QUEUE_NAME')) {
    console.error('Expected metrics.queues to contain $QUEUE_NAME');
    process.exit(1);
  }
"
echo "Expected metrics response: HTTP 200 with jobsByState.COMPLETED >= 1 and queue $QUEUE_NAME present"

cat <<MSG

Smoke test passed.

Created queue:  $QUEUE_NAME
Worker:         $WORKER_ID
Job:            $JOB_ID
Final state:    COMPLETED
MSG
