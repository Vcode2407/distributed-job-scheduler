CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE queues (
  id UUID PRIMARY KEY,
  name VARCHAR(120) NOT NULL UNIQUE,
  description TEXT,
  paused BOOLEAN NOT NULL DEFAULT FALSE,
  dead_letter_queue_id UUID REFERENCES queues(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE workers (
  id VARCHAR(160) PRIMARY KEY,
  hostname VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  capacity INTEGER NOT NULL,
  queues TEXT[] NOT NULL DEFAULT '{}',
  last_heartbeat_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE jobs (
  id UUID PRIMARY KEY,
  queue_id UUID NOT NULL REFERENCES queues(id),
  name VARCHAR(180) NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  state VARCHAR(32) NOT NULL,
  priority INTEGER NOT NULL DEFAULT 0,
  scheduled_at TIMESTAMPTZ NOT NULL,
  cron_expression VARCHAR(120),
  attempt_count INTEGER NOT NULL DEFAULT 0,
  max_attempts INTEGER NOT NULL DEFAULT 3,
  initial_backoff_seconds INTEGER NOT NULL DEFAULT 30,
  max_backoff_seconds INTEGER NOT NULL DEFAULT 3600,
  idempotency_key VARCHAR(180),
  leased_by VARCHAR(160) REFERENCES workers(id),
  lease_expires_at TIMESTAMPTZ,
  last_error TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT chk_jobs_attempts CHECK (attempt_count >= 0 AND max_attempts >= 1),
  CONSTRAINT chk_jobs_priority CHECK (priority >= 0)
);

CREATE UNIQUE INDEX ux_jobs_idempotency_key
  ON jobs(idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE INDEX ix_jobs_due
  ON jobs(queue_id, state, scheduled_at, priority DESC, created_at ASC);

CREATE INDEX ix_jobs_state_updated
  ON jobs(state, updated_at DESC);

CREATE INDEX ix_jobs_lease_expiry
  ON jobs(state, lease_expires_at)
  WHERE lease_expires_at IS NOT NULL;

CREATE TABLE job_execution_history (
  id UUID PRIMARY KEY,
  job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
  worker_id VARCHAR(160) REFERENCES workers(id),
  from_state VARCHAR(32),
  to_state VARCHAR(32) NOT NULL,
  message TEXT,
  duration_ms BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_job_execution_history_job
  ON job_execution_history(job_id, created_at DESC);

CREATE INDEX ix_job_execution_history_worker
  ON job_execution_history(worker_id, created_at DESC);

CREATE TABLE dead_letter_jobs (
  id UUID PRIMARY KEY,
  job_id UUID NOT NULL UNIQUE REFERENCES jobs(id) ON DELETE CASCADE,
  queue_id UUID NOT NULL REFERENCES queues(id),
  reason TEXT NOT NULL,
  payload JSONB NOT NULL,
  failed_attempts INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_dead_letter_jobs_queue
  ON dead_letter_jobs(queue_id, created_at DESC);

CREATE TABLE outbox_events (
  id UUID PRIMARY KEY,
  aggregate_id UUID NOT NULL,
  aggregate_type VARCHAR(80) NOT NULL,
  event_type VARCHAR(120) NOT NULL,
  payload JSONB NOT NULL,
  status VARCHAR(32) NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_outbox_events_pending
  ON outbox_events(status, created_at ASC);

INSERT INTO queues (id, name, description, paused)
VALUES
  ('00000000-0000-0000-0000-000000000001', 'default', 'Default queue', false),
  ('00000000-0000-0000-0000-000000000002', 'default-dlq', 'Default dead-letter queue', false);

UPDATE queues
SET dead_letter_queue_id = '00000000-0000-0000-0000-000000000002'
WHERE id = '00000000-0000-0000-0000-000000000001';
