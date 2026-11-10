import type { ReactNode, SelectHTMLAttributes } from 'react';
import { LoadStateMessage, type LoadState } from './LoadState';
import './shelter-ui.css';

export function FilterSelect({
  label,
  variant,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { label: string; variant?: 'filter' | 'sort' }) {
  const select = (
    <select
      {...props}
      aria-label={label}
      className={[
        'shelter-filter',
        variant && 'shelter-filter--menu',
        variant === 'sort' && 'shelter-filter--sort',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {variant === 'filter' && <option value="">전체</option>}
      {children}
    </select>
  );
  if (variant !== 'filter') return select;
  return (
    <div className="shelter-filter-field">
      {select}
      {!props.value && (
        <span className="shelter-filter-field-label" aria-hidden="true">
          {label}
        </span>
      )}
    </div>
  );
}
export function FilteredListEmpty({ title }: { title: string }) {
  return (
    <p className="shelter-empty filtered-list-empty" role="status">
      <strong>{title}</strong>
      <span>필터 조건을 변경하거나 초기화해 주세요.</span>
    </p>
  );
}
export function ListPanel({
  header,
  filters,
  actions,
  total,
  unit = '개',
  sort,
  children,
  emptyState,
  loadState = { status: 'ready' },
}: {
  header?: ReactNode;
  filters?: ReactNode;
  actions?: ReactNode;
  total: number;
  unit?: string;
  sort?: ReactNode;
  children: ReactNode;
  emptyState?: ReactNode;
  loadState?: LoadState;
}) {
  return (
    <section className="shelter-list" aria-label="조회 결과">
      {header}
      <div className="shelter-list__toolbar">
        <div className="shelter-list__filters">{filters}</div>
        <div className="shelter-list__actions">{actions}</div>
      </div>
      {loadState.status === 'ready' && (
        <div className="shelter-list__summary">
          <span role="status">
            총 <strong>{total}</strong>
            {unit}
          </span>
          {sort}
        </div>
      )}
      <LoadStateMessage state={loadState}>
        {total === 0 ? (
          (emptyState ?? (
            <p className="shelter-empty" role="status">
              검색 결과가 없습니다.
            </p>
          ))
        ) : (
          <div className="shelter-list__grid">{children}</div>
        )}
      </LoadStateMessage>
    </section>
  );
}
