const API_BASE = process.env.API_BASE ?? 'http://localhost:8080';
const JOB_COUNT = Number.parseInt(process.env.JOB_COUNT ?? '10000', 10);
const WORKER_COUNT = Number.parseInt(process.env.WORKER_COUNT ?? '100', 10);
const SUBMIT_CONCURRENCY = Number.parseInt(process.env.SUBMIT_CONCURRENCY ?? '100', 10);
const LEASE_BATCH = Number.parseInt(process.env.LEASE_BATCH ?? '25', 10);

const latencies = [];
let failures = 0;
let completed = 0;
let retries = 0;

function percentile(values, p) {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length));
  return Math.round(sorted[index]);
}

async function timed(label, fn) {
  const started = performance.now();
  try {
    return await fn();
  } finally {
    const elapsed = performance.now() - started;
    latencies.push(elapsed);
    if (elapsed > 5000) {
      console.warn(`${label} took ${Math.round(elapsed)}ms`);
    }
  }
}

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${globalThis.token}`,
      ...options.headers
    }
  });
  if (!response.ok) {
    failures++;
    throw new Error(`${options.method ?? 'GET'} ${path} failed: ${response.status} ${await response.text()}`);
  }
  return response.status === 204 ? undefined : response.json();
}

async function issueToken() {
  const response = await fetch(`${API_BASE}/api/auth/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ subject: 'load-test', roles: ['ADMIN', 'OPERATOR', 'VIEWER'] })
  });
  if (!response.ok) {
    throw new Error(`Unable to issue token: ${response.status} ${await response.text()}`);
  }
  const body = await response.json();
  globalThis.token = body.accessToken;
}

async function createQueue(name) {
  try {
    await request('/api/queues', {
      method: 'POST',
      body: JSON.stringify({ name, description: 'Load test queue', deadLetterQueueName: 'default-dlq' })
    });
  } catch (error) {
    if (!String(error.message).includes('409')) throw error;
  }
}

async function registerWorkers(queueName) {
  await Promise.all(Array.from({ length: WORKER_COUNT }, (_, index) => {
    const workerId = `load-worker-${index}`;
    return request('/api/workers/register', {
      method: 'POST',
      body: JSON.stringify({ id: workerId, hostname: workerId, capacity: LEASE_BATCH, queues: [queueName] })
    });
  }));
}

async function runWithConcurrency(items, concurrency, worker) {
  let cursor = 0;
  const runners = Array.from({ length: concurrency }, async () => {
    while (cursor < items.length) {
      const item = items[cursor++];
      await worker(item);
    }
  });
  await Promise.all(runners);
}

async function submitJobs(queueName) {
  const jobs = Array.from({ length: JOB_COUNT }, (_, index) => index);
  await runWithConcurrency(jobs, SUBMIT_CONCURRENCY, async (index) => {
    await timed('submit job', () => request('/api/jobs', {
      method: 'POST',
      headers: { 'Idempotency-Key': `load-${queueName}-${index}` },
      body: JSON.stringify({
        name: `load-job-${index}`,
        payload: { index },
        queueName,
        priority: index % 10,
        maxAttempts: 3,
        initialBackoffSeconds: 1,
        maxBackoffSeconds: 30
      })
    }));
  });
}

async function workerLoop(workerIndex) {
  const workerId = `load-worker-${workerIndex}`;
  let idleRounds = 0;
  while (completed < JOB_COUNT && idleRounds < 10) {
    const leased = await request(`/api/workers/${workerId}/leases`, {
      method: 'POST',
      body: JSON.stringify({ limit: LEASE_BATCH })
    });
    if (leased.length === 0) {
      idleRounds++;
      await new Promise((resolve) => setTimeout(resolve, 200));
      continue;
    }
    idleRounds = 0;
    await Promise.all(leased.map(async (job) => {
      try {
        const running = await timed('start job', () => request(`/api/workers/${workerId}/jobs/${job.id}/start`, { method: 'POST' }));
        await timed('complete job', () => request(`/api/workers/${workerId}/jobs/${running.id}/complete`, {
          method: 'POST',
          body: JSON.stringify({ durationMs: 1 })
        }));
        completed++;
      } catch (error) {
        retries++;
        console.warn(error.message);
      }
    }));
  }
}

async function main() {
  const queueName = `load-${Date.now()}`;
  const started = performance.now();
  await issueToken();
  await createQueue(queueName);
  await registerWorkers(queueName);
  await submitJobs(queueName);
  await Promise.all(Array.from({ length: WORKER_COUNT }, (_, index) => workerLoop(index)));
  const metrics = await request('/api/metrics');
  const elapsedSeconds = (performance.now() - started) / 1000;
  const report = {
    apiBase: API_BASE,
    queueName,
    jobCount: JOB_COUNT,
    workerCount: WORKER_COUNT,
    completed,
    failures,
    retries,
    throughputJobsPerSecond: Number((completed / elapsedSeconds).toFixed(2)),
    latencyMs: {
      p50: percentile(latencies, 50),
      p95: percentile(latencies, 95),
      p99: percentile(latencies, 99)
    },
    schedulerMetrics: {
      failureRate: metrics.failureRate,
      retryRate: metrics.retryRate,
      averageProcessingTimeMillis: metrics.averageProcessingTimeMillis
    }
  };
  console.log(JSON.stringify(report, null, 2));
  if (completed < JOB_COUNT) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
