import type { Key, ReactNode } from 'react';
import { Button } from './Button';
import './table-modal.css';

export type TableColumn<T> = {
  id: string;
  header: string;
  render: (row: T) => ReactNode;
  width?: string;
};

export function DataTable<T>({
  className = '',
  caption,
  columns,
  rows,
  rowKey,
  emptyMessage = '조회 결과가 없습니다.',
}: {
  className?: string;
  caption: string;
  columns: readonly TableColumn<T>[];
  rows: readonly T[];
  rowKey: (row: T) => Key;
  emptyMessage?: string;
}) {
  return (
    <div className={`ui-table-scroll ${className}`} role="region" aria-label={caption} tabIndex={0}>
      <table className="ui-table">
        <caption className="ui-visually-hidden">{caption}</caption>
        <colgroup>
          {columns.map((column) => (
            <col key={column.id} style={{ width: column.width }} />
          ))}
        </colgroup>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.id} scope="col">
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length ? (
            rows.map((row) => (
              <tr key={rowKey(row)}>
                {columns.map((column) => (
                  <td key={column.id}>{column.render(row)}</td>
                ))}
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan={columns.length} className="ui-table__empty">
                {emptyMessage}
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

/** Page numbers are one-based; the parent owns fetching and slicing rows. */
export function Pagination({
  page,
  pageCount,
  onPageChange,
}: {
  page: number;
  pageCount: number;
  onPageChange: (page: number) => void;
}) {
  if (pageCount < 1) return null;
  const current = Math.max(1, Math.min(pageCount, page));
  const start = Math.max(1, Math.min(current - 2, pageCount - 4));
  const pages = Array.from({ length: Math.min(5, pageCount) }, (_, index) => start + index);
  return (
    <nav className="ui-pagination" aria-label="페이지 이동">
      <Button
        aria-label="이전 페이지"
        disabled={current === 1}
        onClick={() => onPageChange(current - 1)}
      >
        &lt;
      </Button>
      {pages.map((value) => (
        <Button
          key={value}
          aria-label={`${value}페이지`}
          aria-current={value === current ? 'page' : undefined}
          onClick={() => onPageChange(value)}
        >
          {value}
        </Button>
      ))}
      <Button
        aria-label="다음 페이지"
        disabled={current === pageCount}
        onClick={() => onPageChange(current + 1)}
      >
        &gt;
      </Button>
    </nav>
  );
}
