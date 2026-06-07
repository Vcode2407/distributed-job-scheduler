import { Activity } from 'lucide-react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts';
import type { ThroughputPoint } from '../types';

interface ThroughputChartProps {
  data: ThroughputPoint[];
}

export function ThroughputChart({ data }: ThroughputChartProps) {
  const formatted = data.map((point) => ({
    ...point,
    label: new Date(point.bucket).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }));

  return (
    <section className="panel panel--chart">
      <div className="panel__heading">
        <div>
          <h2>Throughput</h2>
          <p>Completed and failed transitions over the last 24 hours</p>
        </div>
        <Activity size={20} aria-hidden="true" />
      </div>
      <div className="chart-frame">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={formatted} margin={{ top: 14, right: 18, bottom: 0, left: -18 }}>
            <defs>
              <linearGradient id="completed" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#1e9b73" stopOpacity={0.28} />
                <stop offset="95%" stopColor="#1e9b73" stopOpacity={0.02} />
              </linearGradient>
              <linearGradient id="failed" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#d84b4b" stopOpacity={0.26} />
                <stop offset="95%" stopColor="#d84b4b" stopOpacity={0.02} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="#e7eaf0" strokeDasharray="4 4" vertical={false} />
            <XAxis dataKey="label" tickLine={false} axisLine={false} minTickGap={24} />
            <YAxis tickLine={false} axisLine={false} width={38} />
            <Tooltip contentStyle={{ borderRadius: 8, border: '1px solid #dbe1ea' }} />
            <Area type="monotone" dataKey="completed" stroke="#1e9b73" fillOpacity={1} fill="url(#completed)" />
            <Area type="monotone" dataKey="failed" stroke="#d84b4b" fillOpacity={1} fill="url(#failed)" />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </section>
  );
}
