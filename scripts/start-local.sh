#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT_DIR/docker-compose.yml}"

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

require_cmd docker
require_cmd curl
require_cmd timeout

if ! docker_ready; then
  die "Docker Engine is not reachable. Start Docker Desktop or Docker Engine, then retry."
fi

echo "Starting local Distributed Job Scheduler stack..."
docker compose -f "$COMPOSE_FILE" up -d --build

echo "Waiting for services to become healthy..."
"$ROOT_DIR/scripts/health-check.sh"

cat <<'MSG'

Local stack is ready.

Frontend:          http://localhost:3000
Backend:           http://localhost:8080
Swagger UI:        http://localhost:8080/swagger-ui.html
Backend health:    http://localhost:8080/actuator/health
Prometheus scrape: http://localhost:8080/actuator/prometheus

Run the smoke test:
  scripts/smoke-test.sh
MSG
