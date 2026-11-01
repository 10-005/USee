import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Button } from './Button';
import { TextField } from './TextField';
import { ActivityCard } from './ActivityCard';

describe('공통 UI 접근성과 동작', () => {
  it('기본 버튼은 폼 제출을 하지 않고 비활성 버튼은 클릭을 막는다', () => {
    const onClick = vi.fn();
    render(
      <Button disabled onClick={onClick}>
        저장
      </Button>,
    );
    const button = screen.getByRole('button', { name: '저장' });
    expect(button).toHaveAttribute('type', 'button');
    fireEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });
  it('라벨과 오류 안내를 각각 고유한 입력 필드에 연결한다', () => {
    render(
      <>
        <TextField label="아이디" error="필수 입력입니다" />
        <TextField label="이름" hint="실명을 입력하세요" />
      </>,
    );
    expect(screen.getByLabelText('아이디')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('아이디')).toHaveAccessibleDescription('필수 입력입니다');
    expect(screen.getByLabelText('이름')).toHaveAccessibleDescription('실명을 입력하세요');
    expect(screen.getByLabelText('아이디').id).not.toBe(screen.getByLabelText('이름').id);
  });
  it('클릭 가능한 내역만 버튼으로 제공한다', () => {
    const onClick = vi.fn();
    render(
      <>
        <ActivityCard
          title="입양 신청"
          description="서류 제출"
          timeLabel="방금"
          onClick={onClick}
        />
        <ActivityCard title="구조 일지" description="접수" timeLabel="어제" />
      </>,
    );
    fireEvent.click(screen.getByRole('button', { name: /입양 신청/ }));
    expect(onClick).toHaveBeenCalledOnce();
    expect(screen.getByRole('article')).toHaveTextContent('구조 일지');
  });
});
