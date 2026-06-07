# Distributed Job Scheduling Platform Architecture

This project implements a production-oriented distributed scheduler inspired by Google Cloud Tasks. The backend follows hexagonal architecture: domain rules live in `domain`, use cases in `application`, adapters in `infrastructure`, and HTTP contracts in `api`.

## Folder Structure

```text
.
├── backend
│   ├── src/main/java/com/example/scheduler
│   │   ├── domain
│   │   ├── application
│   │   ├── infrastructure
│   │   └── api
│   ├── src/main/resources/db/migration
│   └── src/test/java/com/example/scheduler
├── frontend
│   └── src
├── docs
└── docker-compose.yml
```

## High Level Architecture

```mermaid
flowchart LR
  Client["API clients / producers"] --> API["Spring Boot REST API"]
  Dashboard["React Dashboard"] --> API
  API --> App["Application use cases"]
  App --> Domain["Domain state machine"]
  App --> Postgres[("PostgreSQL")]
  App --> Redis[("Redis locks")]
  App --> Outbox[("Transactional outbox")]
  Outbox --> Relay["Outbox relay"]
  Relay --> Kafka[("Kafka event bus")]
  Kafka --> Consumers["Kafka consumers"]
  Consumers --> App
  Workers["Worker nodes"] --> API
  App --> WorkersView["Worker health monitor"]
  WorkersView --> Postgres
```

## Job Submission Sequence

```mermaid
sequenceDiagram
  participant C as Client
  participant A as REST API
  participant S as JobApplicationService
  participant D as Domain State Machine
  participant P as PostgreSQL
  participant O as Outbox
  participant K as Kafka

  C->>A: POST /api/jobs
  A->>S: createJob(command)
  S->>P: find queue + idempotency key
  S->>D: CREATED -> QUEUED or SCHEDULED
  S->>P: persist job in transaction
  S->>O: append JobSubmitted event
  A-->>C: 201 Created
  O->>K: relay pending event
```

## Leasing and Completion Sequence

```mermaid
sequenceDiagram
  participant W as Worker
  participant A as REST API
  participant L as JobLeaseService
  participant R as Redis Lock
  participant P as PostgreSQL
  participant O as Outbox

  W->>A: POST /api/workers/{id}/leases
  A->>L: leaseDueJobs(worker, batch)
  L->>R: acquire queue lease lock
  L->>P: SELECT due jobs FOR UPDATE SKIP LOCKED
  L->>P: mark LEASED with lease expiry
  L->>O: append JobLeased events
  A-->>W: leased jobs
  W->>A: POST /api/workers/{id}/jobs/{jobId}/start
  A->>P: LEASED -> RUNNING
  W->>A: POST /api/workers/{id}/jobs/{jobId}/complete
  A->>P: RUNNING -> COMPLETED
```

## Retry and Dead Letter Sequence

```mermaid
sequenceDiagram
  participant W as Worker
  participant A as REST API
  participant E as JobExecutionService
  participant D as Domain State Machine
  participant P as PostgreSQL
  participant DLQ as Dead Letter Store

  W->>A: report failure
  A->>E: failJob(jobId, reason)
  E->>D: RUNNING -> RETRYING or FAILED
  alt attempts remaining
    E->>P: persist next scheduled retry with exponential backoff
  else attempts exhausted
    E->>D: FAILED -> DEAD_LETTERED
    E->>DLQ: persist dead letter job
  end
```

## ER Diagram

```mermaid
erDiagram
  queues ||--o{ jobs : contains
  queues ||--o{ queues : "dead letters to"
  workers ||--o{ jobs : leases
  jobs ||--o{ job_execution_history : records
  jobs ||--o| dead_letter_jobs : archives
  jobs ||--o{ outbox_events : emits

  queues {
    uuid id PK
    string name UK
    string description
    boolean paused
    uuid dead_letter_queue_id FK
    bigint version
  }

  jobs {
    uuid id PK
    uuid queue_id FK
    string name
    jsonb payload
    string state
    int priority
    timestamptz scheduled_at
    string cron_expression
    int attempt_count
    int max_attempts
    string idempotency_key UK
    string leased_by
    timestamptz lease_expires_at
    bigint version
  }

  workers {
    string id PK
    string hostname
    string status
    int capacity
    timestamptz last_heartbeat_at
    bigint version
  }

  job_execution_history {
    uuid id PK
    uuid job_id FK
    string worker_id FK
    string from_state
    string to_state
    string message
    bigint duration_ms
  }

  dead_letter_jobs {
    uuid id PK
    uuid job_id FK
    uuid queue_id FK
    string reason
    jsonb payload
  }

  outbox_events {
    uuid id PK
    uuid aggregate_id
    string aggregate_type
    string event_type
    jsonb payload
    string status
  }
```

## Deployment Diagram

```mermaid
flowchart TB
  subgraph Docker Compose
    FE["frontend: nginx + React"]
    APP["app: Spring Boot"]
    PG[("postgres")]
    RD[("redis")]
    ZK["zookeeper"]
    KF["kafka"]
  end

  Browser["Operator browser"] --> FE
  FE --> APP
  Producers["Job producers"] --> APP
  Workers["Worker pool"] --> APP
  APP --> PG
  APP --> RD
  APP --> KF
  KF --> ZK
```

## Scalability Notes

- PostgreSQL stores authoritative job state with optimistic locking and `FOR UPDATE SKIP LOCKED` batch leasing.
- Kafka carries state-change events and supports at-least-once event delivery.
- Redis locks coordinate batch leasing and recovery loops across app replicas.
- The transactional outbox ensures database writes and event publication cannot silently diverge.
- Workers scale horizontally by registering capacity, heartbeating, and pulling leases in batches.
- Queue pause/resume is enforced at lease time so already running work can finish gracefully.
