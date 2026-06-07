import { Pause, Play, Rows3 } from 'lucide-react';
import type { Queue } from '../types';

interface QueueTableProps {
  queues: Queue[];
  onPause: (id: string) => void;
  onResume: (id: string) => void;
}

export function QueueTable({ queues, onPause, onResume }: QueueTableProps) {
  return (
    <section className="panel">
      <div className="panel__heading">
        <div>
          <h2>Queues</h2>
          <p>Pause state and dead-letter routing</p>
        </div>
        <Rows3 size={20} aria-hidden="true" />
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Status</th>
              <th>DLQ</th>
              <th aria-label="Actions"></th>
            </tr>
          </thead>
          <tbody>
            {queues.map((queue) => (
              <tr key={queue.id}>
                <td>
                  <span className="primary-cell">{queue.name}</span>
                  <span className="secondary-cell">{queue.description || queue.id.slice(0, 8)}</span>
                </td>
                <td>{queue.paused ? 'Paused' : 'Running'}</td>
                <td>{queue.deadLetterQueueId ? queue.deadLetterQueueId.slice(0, 8) : 'None'}</td>
                <td className="action-cell">
                  <button
                    className="icon-button"
                    type="button"
                    title={queue.paused ? 'Resume queue' : 'Pause queue'}
                    onClick={() => (queue.paused ? onResume(queue.id) : onPause(queue.id))}
                  >
                    {queue.paused ? <Play size={17} /> : <Pause size={17} />}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
