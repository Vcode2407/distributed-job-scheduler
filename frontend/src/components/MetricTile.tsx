import type { LucideIcon } from 'lucide-react';

interface MetricTileProps {
  icon: LucideIcon;
  label: string;
  value: string;
  trend?: string;
  tone?: 'blue' | 'green' | 'red' | 'amber' | 'neutral';
}

export function MetricTile({ icon: Icon, label, value, trend, tone = 'neutral' }: MetricTileProps) {
  return (
    <section className={`metric-tile metric-tile--${tone}`}>
      <div className="metric-tile__icon" aria-hidden="true">
        <Icon size={19} strokeWidth={2.2} />
      </div>
      <div>
        <p>{label}</p>
        <strong>{value}</strong>
        {trend ? <span>{trend}</span> : null}
      </div>
    </section>
  );
}
