#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT_DIR/docker-compose.yml}"
API_BASE="${API_BASE:-http://localhost:8080}"
FRONTEND_BASE="${FRONTEND_BASE:-http://localhost:3000}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-180}"
SLEEP_SECONDS="${SLEEP_SECONDS:-5}"

die() {
  echo "ERROR: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "$1 is required but was not found on PATH"
}

docker_ready() {
  timeout "${DOCKER_INFO_TIMEOUT_SECONDS:-15}" docker info >/dev/null 2>&1
}

check_until() {
  local name="$1"
  shift
  local started
  started="$(date +%s)"
  printf 'Checking %-10s' "$name"
  until "$@" >/tmp/scheduler-health-check.out 2>&1; do
    local now
    now="$(date +%s)"
    if (( now - started >= TIMEOUT_SECONDS )); then
      echo " FAILED"
      cat /tmp/scheduler-health-check.out >&2 || true
      die "$name did not become healthy within ${TIMEOUT_SECONDS}s"
    fi
    printf '.'
    sleep "$SLEEP_SECONDS"
  done
  echo " OK"
}

require_cmd docker
require_cmd curl
require_cmd timeout

if ! docker_ready; then
  die "Docker Engine is not reachable. Start Docker Desktop or Docker Engine, then retry."
fi

check_until "postgres" docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U jobscheduler -d jobscheduler
check_until "redis" docker compose -f "$COMPOSE_FILE" exec -T redis redis-cli ping
check_until "kafka" docker compose -f "$COMPOSE_FILE" exec -T kafka kafka-broker-api-versions --bootstrap-server localhost:9092
check_until "backend" curl -fsS "$API_BASE/actuator/health/readiness"
check_until "frontend" curl -fsS "$FRONTEND_BASE/health"

echo "All runtime dependencies are healthy."
