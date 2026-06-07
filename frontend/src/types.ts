export type JobState =
  | 'CREATED'
  | 'QUEUED'
  | 'SCHEDULED'
  | 'LEASED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'RETRYING'
  | 'DEAD_LETTERED';

export type WorkerStatus = 'STARTING' | 'HEALTHY' | 'DEGRADED' | 'OFFLINE';

export interface Job {
  id: string;
  queueId: string;
  name: string;
  payload: string;
  state: JobState;
  priority: number;
  scheduledAt: string;
  cronExpression?: string;
  attemptCount: number;
  maxAttempts: number;
  idempotencyKey?: string;
  leasedBy?: string;
  leaseExpiresAt?: string;
  lastError?: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface JobListResponse {
  items: Job[];
  total: number;
  limit: number;
  offset: number;
}

export interface Queue {
  id: string;
  name: string;
  description?: string;
  paused: boolean;
  deadLetterQueueId?: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface Worker {
  id: string;
  hostname: string;
  status: WorkerStatus;
  capacity: number;
  queues: string[];
  lastHeartbeatAt: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ThroughputPoint {
  bucket: string;
  completed: number;
  failed: number;
}

export interface Metrics {
  jobsByState: Record<JobState, number>;
  queues: Array<{ id: string; name: string; paused: boolean }>;
  workersByStatus: Partial<Record<WorkerStatus, number>>;
  throughput: ThroughputPoint[];
  averageProcessingTimeMillis: number;
  failureRate: number;
  retryRate: number;
}
