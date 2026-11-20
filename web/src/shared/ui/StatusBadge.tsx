import type { HTMLAttributes } from 'react';
import './shared-ui.css';

export function StatusBadge({ className = '', ...props }: HTMLAttributes<HTMLSpanElement>) {
  return <span className={`ui-status-badge ${className}`} {...props} />;
}
