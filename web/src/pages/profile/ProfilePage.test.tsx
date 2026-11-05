import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { useShelterDemo } from '@/mocks/shelter/store';
import { shelterMockRepository } from '@/mocks/shelter/repository';
import { ProfilePage } from './ProfilePage';

beforeEach(() => {
  useShelterDemo.setState(useShelterDemo.getInitialState(), true);
  render(
    <MemoryRouter>
      <ProfilePage />
    </MemoryRouter>,
  );
});
afterEach(cleanup);

it('저장 실패 시 수정한 내용과 오류 메시지를 유지한다', async () => {
  cleanup();
  const failure = vi
    .spyOn(shelterMockRepository, 'saveProfile')
    .mockRejectedValueOnce(new Error('offline'));
  render(
    <MemoryRouter>
      <ProfilePage />
    </MemoryRouter>,
  );
  fireEvent.click(screen.getByRole('button', { name: '정보 수정' }));
  fireEvent.change(screen.getByLabelText('휴무일'), { target: { value: '월요일' } });
  fireEvent.click(screen.getByRole('button', { name: '저장하기' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('저장하지 못했습니다');
  expect(screen.getByLabelText('휴무일')).toHaveValue('월요일');
  expect(useShelterDemo.getState().profile.holiday).not.toBe('월요일');
  failure.mockRestore();
});

it('기본정보는 비활성화하고 수정 가능한 정보만 저장한다', () => {
  const original = useShelterDemo.getState().profile;
  fireEvent.click(screen.getByRole('button', { name: '정보 수정' }));
  for (const label of ['보호소 이름', '주소', '전화번호']) {
    expect(screen.getByLabelText(label)).toBeDisabled();
  }
  fireEvent.change(screen.getByLabelText('휴무일'), { target: { value: '월요일' } });
  fireEvent.click(screen.getByRole('button', { name: '저장하기' }));
  expect(useShelterDemo.getState().profile).toEqual({ ...original, holiday: '월요일' });
});

it.each([
  ['배경이미지', 'bannerImage'],
  ['프로필이미지', 'profileImage'],
] as const)('%s는 미리보기 후 저장하기를 눌러 반영한다', async (label, key) => {
  fireEvent.click(screen.getByRole('button', { name: '정보 수정' }));
  fireEvent.change(screen.getByLabelText(`${label} 선택`), {
    target: { files: [new File(['image'], 'photo.png', { type: 'image/png' })] },
  });
  await waitFor(() =>
    expect(screen.getByAltText(label).getAttribute('src')).toMatch(/^data:image\/png;base64,/),
  );
  expect(useShelterDemo.getState().profile[key]).toBeUndefined();
  fireEvent.click(screen.getByRole('button', { name: '저장하기' }));
  expect(screen.getByAltText(label)).toHaveAttribute('src', useShelterDemo.getState().profile[key]);
});

it('지원하지 않는 파일은 기존 이미지를 유지한다', () => {
  fireEvent.click(screen.getByRole('button', { name: '정보 수정' }));
  const original = screen.getByAltText('배경이미지').getAttribute('src');
  fireEvent.change(screen.getByLabelText('배경이미지 선택'), {
    target: { files: [new File(['text'], 'file.txt', { type: 'text/plain' })] },
  });
  expect(screen.getByRole('alert')).toHaveTextContent('10MB 이하');
  expect(screen.getByAltText('배경이미지')).toHaveAttribute('src', original);
});

it('조회 상태에서는 변경 UI가 없고 취소하면 원래 이미지로 돌아간다', async () => {
  expect(screen.queryByRole('button', { name: '배경이미지 변경' })).not.toBeInTheDocument();
  expect(screen.queryByLabelText('프로필이미지 선택')).not.toBeInTheDocument();
  const original = screen.getByAltText('프로필이미지').getAttribute('src');
  fireEvent.click(screen.getByRole('button', { name: '정보 수정' }));
  fireEvent.change(screen.getByLabelText('프로필이미지 선택'), {
    target: { files: [new File(['image'], 'photo.png', { type: 'image/png' })] },
  });
  await waitFor(() =>
    expect(screen.getByAltText('프로필이미지').getAttribute('src')).not.toBe(original),
  );
  fireEvent.click(screen.getByRole('button', { name: '취소' }));
  expect(screen.getByAltText('프로필이미지')).toHaveAttribute('src', original);
  expect(useShelterDemo.getState().profile.profileImage).toBeUndefined();
  fireEvent.click(screen.getByRole('button', { name: '정보 수정' }));
  expect(screen.getByAltText('프로필이미지')).toHaveAttribute('src', original);
});
