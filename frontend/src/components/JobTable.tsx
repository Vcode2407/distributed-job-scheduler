import { Clock3 } from 'lucide-react';
import { StatusPill } from './StatusPill';
import type { Job } from '../types';

interface JobTableProps {
  jobs: Job[];
}

export function JobTable({ jobs }: JobTableProps) {
  return (
    <section className="panel">
      <div className="panel__heading">
        <div>
          <h2>Active Jobs</h2>
          <p>Latest submitted work across queues</p>
        </div>
        <Clock3 size={20} aria-hidden="true" />
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>State</th>
              <th>Priority</th>
              <th>Attempts</th>
              <th>Scheduled</th>
            </tr>
          </thead>
          <tbody>
            {jobs.map((job) => (
              <tr key={job.id}>
                <td>
                  <span className="primary-cell">{job.name}</span>
                  <span className="secondary-cell">{job.id.slice(0, 8)}</span>
                </td>
                <td><StatusPill value={job.state} /></td>
                <td>{job.priority}</td>
                <td>{job.attemptCount}/{job.maxAttempts}</td>
                <td>{new Date(job.scheduledAt).toLocaleString()}</td>
              </tr>
            ))}
            {jobs.length === 0 ? (
              <tr>
                <td colSpan={5} className="empty-cell">No jobs found</td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </section>
  );
}
