import { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Gauge, LogOut, RefreshCw, Repeat2, Timer, Workflow } from 'lucide-react';
import { clearToken, fetchJobs, fetchMetrics, fetchQueues, fetchWorkers, getToken, issueDevToken, pauseQueue, resumeQueue } from './api/client';
import { JobTable } from './components/JobTable';
import { MetricTile } from './components/MetricTile';
import { QueueTable } from './components/QueueTable';
import { ThroughputChart } from './components/ThroughputChart';
import { WorkerTable } from './components/WorkerTable';
import type { Job, Metrics, Queue, Worker } from './types';

const emptyMetrics: Metrics = {
  jobsByState: {
    CREATED: 0,
    QUEUED: 0,
    SCHEDULED: 0,
    LEASED: 0,
    RUNNING: 0,
    COMPLETED: 0,
    FAILED: 0,
    RETRYING: 0,
    DEAD_LETTERED: 0
  },
  queues: [],
  workersByStatus: {},
  throughput: [],
  averageProcessingTimeMillis: 0,
  failureRate: 0,
  retryRate: 0
};

function App() {
  const [tokenReady, setTokenReady] = useState(Boolean(getToken()));
  const [subject, setSubject] = useState('operator');
  const [metrics, setMetrics] = useState<Metrics>(emptyMetrics);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [queues, setQueues] = useState<Queue[]>([]);
  const [workers, setWorkers] = useState<Worker[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!getToken()) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [nextMetrics, nextJobs, nextQueues, nextWorkers] = await Promise.all([
        fetchMetrics(),
        fetchJobs(),
        fetchQueues(),
        fetchWorkers()
      ]);
      setMetrics(nextMetrics);
      setJobs(nextJobs.items);
      setQueues(nextQueues);
      setWorkers(nextWorkers);
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : 'Unable to load dashboard data');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
    const intervalId = window.setInterval(() => void refresh(), 15_000);
    return () => window.clearInterval(intervalId);
  }, [refresh, tokenReady]);

  const activeJobs = useMemo(() => {
    return ['QUEUED', 'SCHEDULED', 'LEASED', 'RUNNING'].reduce((total, state) => {
      return total + (metrics.jobsByState[state as keyof Metrics['jobsByState']] ?? 0);
    }, 0);
  }, [metrics.jobsByState]);

  async function connect() {
    setLoading(true);
    setError(null);
    try {
      await issueDevToken(subject);
      setTokenReady(true);
      await refresh();
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : 'Unable to issue token');
    } finally {
      setLoading(false);
    }
  }

  function logout() {
    clearToken();
    setTokenReady(false);
    setJobs([]);
    setQueues([]);
    setWorkers([]);
    setMetrics(emptyMetrics);
  }

  async function pause(id: string) {
    await pauseQueue(id);
    await refresh();
  }

  async function resume(id: string) {
    await resumeQueue(id);
    await refresh();
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div className="brand">
          <div className="brand__mark"><Workflow size={21} /></div>
          <div>
            <h1>Distributed Job Scheduler</h1>
            <p>Queue operations dashboard</p>
          </div>
        </div>
        <div className="topbar__actions">
          {!tokenReady ? (
            <>
              <input
                value={subject}
                onChange={(event) => setSubject(event.target.value)}
                aria-label="Token subject"
              />
              <button className="button button--primary" type="button" onClick={() => void connect()} disabled={loading}>
                Connect
              </button>
            </>
          ) : (
            <>
              <button className="icon-button" type="button" title="Refresh dashboard" onClick={() => void refresh()} disabled={loading}>
                <RefreshCw size={18} className={loading ? 'spin' : undefined} />
              </button>
              <button className="icon-button" type="button" title="Clear token" onClick={logout}>
                <LogOut size={18} />
              </button>
            </>
          )}
        </div>
      </header>

      {error ? <div className="error-banner">{error}</div> : null}

      <section className="metric-grid">
        <MetricTile icon={Gauge} label="Active jobs" value={activeJobs.toLocaleString()} tone="blue" />
        <MetricTile icon={AlertTriangle} label="Failed jobs" value={(metrics.jobsByState.FAILED + metrics.jobsByState.DEAD_LETTERED).toLocaleString()} tone="red" />
        <MetricTile icon={Repeat2} label="Retrying jobs" value={metrics.jobsByState.RETRYING.toLocaleString()} trend={`${(metrics.retryRate * 100).toFixed(1)}% retry rate`} tone="amber" />
        <MetricTile icon={Timer} label="Avg processing" value={`${Math.round(metrics.averageProcessingTimeMillis).toLocaleString()} ms`} trend={`${(metrics.failureRate * 100).toFixed(1)}% failure rate`} tone="green" />
      </section>

      <section className="content-grid">
        <ThroughputChart data={metrics.throughput} />
        <WorkerTable workers={workers} />
      </section>

      <section className="content-grid content-grid--wide">
        <JobTable jobs={jobs} />
        <QueueTable queues={queues} onPause={(id) => void pause(id)} onResume={(id) => void resume(id)} />
      </section>
    </main>
  );
}

export default App;
