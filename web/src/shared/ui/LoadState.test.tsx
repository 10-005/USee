import { fireEvent, render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { ListPanel } from './ListPanel';
import { DataTable } from './DataTable';

it('목록은 로딩과 오류를 빈 결과와 구분하고 재시도를 제공한다', () => {
  const retry = vi.fn();
  const { rerender } = render(
    <ListPanel total={0} loadState={{ status: 'loading' }}>
      내용
    </ListPanel>,
  );
  expect(screen.getByRole('status')).toHaveTextContent('불러오는 중');
  expect(screen.queryByText('검색 결과가 없습니다.')).not.toBeInTheDocument();
  rerender(
    <ListPanel total={0} loadState={{ status: 'error', retry }}>
      내용
    </ListPanel>,
  );
  expect(screen.getByRole('alert')).toHaveTextContent('정보를 불러오지 못했습니다.');
  fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
  expect(retry).toHaveBeenCalledOnce();
  rerender(<ListPanel total={0}>내용</ListPanel>);
  expect(screen.getByText('검색 결과가 없습니다.')).toBeVisible();
});

it('표는 오류 상태에서 빈 결과 대신 재시도를 표시한다', () => {
  const retry = vi.fn();
  render(
    <DataTable
      caption="목록"
      columns={[{ id: 'name', header: '이름', render: (row: string) => row }]}
      rows={[]}
      rowKey={(row) => row}
      loadState={{ status: 'error', retry }}
    />,
  );
  expect(screen.getByRole('alert')).toBeVisible();
  expect(screen.queryByText('조회 결과가 없습니다.')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
  expect(retry).toHaveBeenCalledOnce();
});
