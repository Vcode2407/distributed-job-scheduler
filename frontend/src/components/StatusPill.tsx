interface StatusPillProps {
  value: string;
}

const toneByStatus: Record<string, string> = {
  COMPLETED: 'green',
  HEALTHY: 'green',
  RUNNING: 'blue',
  LEASED: 'blue',
  QUEUED: 'amber',
  SCHEDULED: 'amber',
  RETRYING: 'amber',
  FAILED: 'red',
  DEAD_LETTERED: 'red',
  OFFLINE: 'red',
  DEGRADED: 'amber',
  STARTING: 'blue'
};

export function StatusPill({ value }: StatusPillProps) {
  const tone = toneByStatus[value] ?? 'neutral';
  return <span className={`status-pill status-pill--${tone}`}>{value.replace('_', ' ')}</span>;
}
