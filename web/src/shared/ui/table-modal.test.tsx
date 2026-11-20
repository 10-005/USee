import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfirmModal } from './Modal';
import { DataTable, Pagination } from './DataTable';

describe('모달 제어', () => {
  beforeEach(() => {
    // jsdom has no native dialog implementation; focus trapping is verified in the browser.
    Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
      configurable: true,
      value: function (this: HTMLDialogElement) {
        this.setAttribute('open', '');
      },
    });
    Object.defineProperty(HTMLDialogElement.prototype, 'close', {
      configurable: true,
      value: function (this: HTMLDialogElement) {
        this.removeAttribute('open');
      },
    });
  });
  afterEach(() => {
    cleanup();
    Reflect.deleteProperty(HTMLDialogElement.prototype, 'showModal');
    Reflect.deleteProperty(HTMLDialogElement.prototype, 'close');
  });
  it('Escape는 닫기만 요청하고 확인 버튼만 확인 콜백을 실행한다', () => {
    const onClose = vi.fn();
    const onConfirm = vi.fn();
    render(
      <ConfirmModal
        open
        title="작성을 취소할까요?"
        description="저장되지 않아요."
        onClose={onClose}
        onConfirm={onConfirm}
      />,
    );
    const dialog = screen.getByRole('dialog', { name: '작성을 취소할까요?' });
    expect(dialog).toHaveAccessibleDescription('저장되지 않아요.');
    fireEvent(dialog, new Event('cancel', { cancelable: true }));
    expect(onClose).toHaveBeenCalledOnce();
    expect(onConfirm).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '작성 취소' }));
    expect(onConfirm).toHaveBeenCalledOnce();
  });
  it('닫을 때 스크롤과 열기 전 포커스를 복구한다', () => {
    const trigger = document.createElement('button');
    document.body.append(trigger);
    trigger.focus();
    const props = { title: '확인', onClose: vi.fn(), onConfirm: vi.fn() };
    const { rerender } = render(<ConfirmModal {...props} open />);
    expect(document.body.style.overflow).toBe('hidden');
    rerender(<ConfirmModal {...props} open={false} />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(document.body.style.overflow).toBe('');
    expect(trigger).toHaveFocus();
    trigger.remove();
  });
});

describe('테이블과 페이지 이동', () => {
  it('열 제목·셀 및 빈 결과를 제공한다', () => {
    const columns = [{ id: 'name', header: '신청자', render: (row: { name: string }) => row.name }];
    const { rerender } = render(
      <DataTable
        caption="신청 목록"
        columns={columns}
        rows={[{ name: '김민지' }]}
        rowKey={(row) => row.name}
      />,
    );
    expect(screen.getByRole('table', { name: '신청 목록' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: '신청자' })).toHaveAttribute('scope', 'col');
    expect(screen.getByRole('cell', { name: '김민지' })).toBeInTheDocument();
    rerender(
      <DataTable caption="신청 목록" columns={columns} rows={[]} rowKey={(row) => row.name} />,
    );
    expect(screen.getByRole('cell')).toHaveTextContent('조회 결과가 없습니다.');
  });
  it('페이지 경계를 막고 변경할 페이지를 전달한다', () => {
    const change = vi.fn();
    const { rerender } = render(<Pagination page={1} pageCount={8} onPageChange={change} />);
    expect(screen.getByRole('button', { name: '이전 페이지' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '다음 페이지' }));
    expect(change).toHaveBeenCalledWith(2);
    rerender(<Pagination page={8} pageCount={8} onPageChange={change} />);
    expect(screen.getByRole('button', { name: '다음 페이지' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '8페이지' })).toHaveAttribute('aria-current', 'page');
  });
});
