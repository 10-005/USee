import type { ReactNode } from 'react';
import './shared-ui.css';

export type StatCardProps = {
  label: string;
  value: ReactNode;
  tone?: 'blue' | 'green' | 'orange';
};

export function StatCard({ label, value, tone = 'blue' }: StatCardProps) {
  return (
    <dl className={`ui-stat-card ui-stat-card--${tone}`}>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </dl>
  );
}
