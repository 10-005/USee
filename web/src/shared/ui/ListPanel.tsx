import type { ReactNode, SelectHTMLAttributes } from 'react';
import './shelter-ui.css';

export function FilterSelect({
  label,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { label: string }) {
  return (
    <select {...props} aria-label={label} className="shelter-filter">
      {children}
    </select>
  );
}
export function ListPanel({
  filters,
  actions,
  total,
  unit = '개',
  sort,
  children,
  emptyMessage = '검색 결과가 없습니다.',
}: {
  filters?: ReactNode;
  actions?: ReactNode;
  total: number;
  unit?: string;
  sort?: ReactNode;
  children: ReactNode;
  emptyMessage?: string;
}) {
  return (
    <section className="shelter-list" aria-label="조회 결과">
      <div className="shelter-list__toolbar">
        <div className="shelter-list__filters">{filters}</div>
        <div className="shelter-list__actions">{actions}</div>
      </div>
      <div className="shelter-list__summary">
        <span role="status">
          총 <strong>{total}</strong>
          {unit}
        </span>
        {sort}
      </div>
      {total === 0 ? (
        <p className="shelter-empty" role="status">
          {emptyMessage}
        </p>
      ) : (
        <div className="shelter-list__grid">{children}</div>
      )}
    </section>
  );
}
