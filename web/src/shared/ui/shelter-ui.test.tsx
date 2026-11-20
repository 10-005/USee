import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useState } from 'react';
import { MemoryRouter } from 'react-router';
import { ChatSidebar } from './ChatSidebar';
import { ChoiceGroup } from './ChoiceGroup';
import { ImageUpload } from './ImageUpload';
import { ShelterPreviewPage } from '@/pages/ui-preview/ShelterPreviewPage';

describe('보호소 공통 컴포넌트', () => {
  it('채팅을 열고 안 읽은 대화만 필터링한 뒤 Escape로 닫는다', () => {
    const onSelect = vi.fn();
    function Example() {
      const [open, setOpen] = useState(false);
      return (
        <ChatSidebar
          open={open}
          onOpenChange={setOpen}
          conversations={[
            {
              id: 'a',
              name: '읽지 않은 대화',
              avatarSrc: '/avatar.png',
              preview: '문의',
              timeLabel: '방금',
              unreadCount: 2,
            },
            {
              id: 'b',
              name: '읽은 대화',
              avatarSrc: '/avatar.png',
              preview: '감사합니다',
              timeLabel: '어제',
              unreadCount: 0,
            },
          ]}
          onSelect={onSelect}
        />
      );
    }
    render(<Example />);
    expect(screen.queryByRole('button', { name: /읽지 않은 대화/ })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /채팅 열기/ }));
    fireEvent.click(screen.getByRole('button', { name: '안 읽음 2' }));
    expect(screen.queryByRole('button', { name: /읽은 대화/ })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /읽지 않은 대화/ }));
    expect(onSelect).toHaveBeenCalledWith('a');
    fireEvent.keyDown(screen.getByRole('button', { name: '채팅 닫기' }), { key: 'Escape' });
    expect(screen.getByRole('button', { name: /채팅 열기/ })).toHaveFocus();
  });
  it('선택 그룹은 하나의 값만 선택하고 disabled 상태를 전달한다', () => {
    function Example() {
      const [value, setValue] = useState('yes');
      return (
        <ChoiceGroup
          label="중성화"
          value={value}
          onChange={setValue}
          options={[
            { value: 'yes', label: '완료' },
            { value: 'no', label: '미완료' },
          ]}
        />
      );
    }
    render(<Example />);
    fireEvent.click(screen.getByRole('radio', { name: '미완료' }));
    expect(screen.getByRole('radio', { name: '미완료' })).toBeChecked();
    expect(screen.getByRole('radio', { name: '완료' })).not.toBeChecked();
  });
  it('사진 개수와 파일 형식을 검사하고 유효한 파일만 전달한다', () => {
    const onChange = vi.fn();
    render(<ImageUpload files={[]} onChange={onChange} maxFiles={1} />);
    const input = screen.getByLabelText('동물 사진');
    fireEvent.change(input, {
      target: { files: [new File(['x'], 'x.txt', { type: 'text/plain' })] },
    });
    expect(screen.getByRole('alert')).toHaveTextContent('파일 형식과 크기');
    expect(onChange).not.toHaveBeenCalled();
    const valid = new File(['png'], 'animal.png', { type: 'image/png' });
    fireEvent.change(input, { target: { files: [valid, valid] } });
    expect(screen.getByRole('alert')).toHaveTextContent('최대 1장');
    fireEvent.change(input, { target: { files: [valid] } });
    expect(onChange).toHaveBeenCalledWith([valid]);
  });
  it('목록 필터 조합의 빈 결과와 초기화를 처리한다', () => {
    render(
      <MemoryRouter>
        <ShelterPreviewPage />
      </MemoryRouter>,
    );
    const list = screen.getByRole('region', { name: '조회 결과' });
    fireEvent.change(screen.getByRole('combobox', { name: '상태' }), {
      target: { value: 'adopted' },
    });
    fireEvent.change(screen.getByRole('combobox', { name: '입양 가능 여부' }), {
      target: { value: 'true' },
    });
    expect(within(list).getByText('검색 결과가 없습니다.')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: '필터 초기화' }));
    expect(within(list).getByText('4')).toBeVisible();
    expect(within(list).queryByText('검색 결과가 없습니다.')).not.toBeInTheDocument();
  });
});
