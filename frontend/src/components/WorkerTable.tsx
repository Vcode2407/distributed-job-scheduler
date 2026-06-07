import { Server } from 'lucide-react';
import { StatusPill } from './StatusPill';
import type { Worker } from '../types';

interface WorkerTableProps {
  workers: Worker[];
}

export function WorkerTable({ workers }: WorkerTableProps) {
  return (
    <section className="panel">
      <div className="panel__heading">
        <div>
          <h2>Workers</h2>
          <p>Heartbeat health and capacity</p>
        </div>
        <Server size={20} aria-hidden="true" />
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Worker</th>
              <th>Status</th>
              <th>Capacity</th>
              <th>Queues</th>
              <th>Heartbeat</th>
            </tr>
          </thead>
          <tbody>
            {workers.map((worker) => (
              <tr key={worker.id}>
                <td>
                  <span className="primary-cell">{worker.id}</span>
                  <span className="secondary-cell">{worker.hostname}</span>
                </td>
                <td><StatusPill value={worker.status} /></td>
                <td>{worker.capacity}</td>
                <td>{worker.queues.join(', ')}</td>
                <td>{new Date(worker.lastHeartbeatAt).toLocaleTimeString()}</td>
              </tr>
            ))}
            {workers.length === 0 ? (
              <tr>
                <td colSpan={5} className="empty-cell">No workers registered</td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </section>
  );
}
