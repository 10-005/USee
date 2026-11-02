import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { AnimalsPage } from './AnimalsPage';
import { selectAnimals } from './model';
import { sampleAnimals, sampleReferenceDate } from './sample-data';

describe('보호동물 목록', () => {
  it('여섯 필터를 함께 적용하고 원본 데이터를 변경하지 않는다', () => {
    const params = new URLSearchParams(
      'status=protected&period=30&species=dog&sex=female&adoptable=true&neutered=yes',
    );
    const result = selectAnimals(sampleAnimals, params, sampleReferenceDate);
    expect(result.map((animal) => animal.id)).toEqual(['sample-01']);
    expect(sampleAnimals).toHaveLength(24);
    expect(sampleAnimals[0]?.id).toBe('sample-01');
  });

  it('등록일로 정렬하고 기간 경계 및 잘못된 필터를 처리한다', () => {
    const recent = selectAnimals(
      sampleAnimals,
      new URLSearchParams('period=30'),
      sampleReferenceDate,
    );
    expect(recent).toHaveLength(6);
    expect(recent.some((animal) => animal.id === 'sample-07')).toBe(false);
    const oldest = selectAnimals(
      sampleAnimals,
      new URLSearchParams('sort=oldest'),
      sampleReferenceDate,
    );
    expect(oldest[0]?.id).toBe('sample-24');
    expect(
      selectAnimals(
        sampleAnimals,
        new URLSearchParams('status=invalid&period=-1'),
        sampleReferenceDate,
      ),
    ).toHaveLength(24);
  });

  it('URL 필터로 진입해 빈 결과를 표시하고 초기화로 전체 목록을 복구한다', () => {
    render(
      <MemoryRouter initialEntries={['/animals?species=other&sort=oldest']}>
        <AnimalsPage />
      </MemoryRouter>,
    );
    expect(screen.getByRole('combobox', { name: '축종' })).toHaveValue('other');
    expect(screen.getByText('조건에 맞는 보호동물이 없어요.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '필터 초기화' }));
    expect(screen.getByRole('combobox', { name: '축종' })).toHaveValue('');
    expect(screen.getByRole('combobox', { name: '정렬' })).toHaveValue('latest');
    expect(screen.getByRole('status')).toHaveTextContent('총 24마리');
    expect(screen.getAllByRole('img', { name: '보호동물 예시 사진' })).toHaveLength(12);
    const firstCard = screen.getAllByRole('img', { name: '보호동물 예시 사진' })[0];
    fireEvent.click(screen.getByRole('button', { name: '다음 페이지' }));
    expect(firstCard).not.toBeInTheDocument();
    expect(screen.getAllByRole('img', { name: '보호동물 예시 사진' })).toHaveLength(12);
    expect(screen.getByRole('button', { name: '다음 페이지' })).toBeDisabled();
  });

  it('필터 변경 시 결과 개수가 바뀌고 채팅 사이드바를 열고 닫는다', () => {
    render(
      <MemoryRouter>
        <AnimalsPage />
      </MemoryRouter>,
    );
    fireEvent.click(within(screen.getByRole('group', { name: '상태 필터' })).getByRole('button', { name: /입양 완료/ }));
    expect(screen.getByRole('status')).toHaveTextContent('총 4마리');
    expect(screen.getAllByRole('img', { name: '보호동물 예시 사진' })).toHaveLength(4);
    fireEvent.click(screen.getByRole('button', { name: /채팅 열기/ }));
    const sidebar = screen.getByRole('complementary', { name: '채팅' });
    expect(within(sidebar).getByRole('button', { name: /시샤/ })).toBeVisible();
    fireEvent.keyDown(sidebar, { key: 'Escape' });
    expect(screen.getByRole('button', { name: /채팅 열기/ })).toHaveAttribute(
      'aria-expanded',
      'false',
    );
  });
});
