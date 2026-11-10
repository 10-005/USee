import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Routes, Route } from 'react-router';
import { expect, it, vi } from 'vitest';
import { AdoptionDetailPage } from './AdoptionDetailPage';
import { shelterRequest } from '@/shared/api/session';

vi.mock('@/shared/config/demo', () => ({ demoMode: false }));
vi.mock('@/shared/api/session', () => ({ shelterRequest: vi.fn() }));

it('동물 번호로 다른 페이지의 신청서까지 묶고, 상세 조회가 실패한 신청서에서도 양쪽으로 이동한다', async () => {
  const row = (id: number, animalSeq = 7) => ({
    applicationSeq: id,
    animalSummary: { animalSeq, speciesName: '개', breedName: id === 13 ? '믹스' : '진돗개' },
    status: 'SUBMITTED',
    applicant: { name: '신청자' },
    questions: [],
    answers: {},
  });
  const api = vi.mocked(shelterRequest).mockImplementation(async (path) => {
    const url = new URL(path, 'https://test.invalid');
    if (url.pathname.endsWith('/received')) {
      expect(url.searchParams.get('animalSeq')).toBe('7');
      return {
        content:
          url.searchParams.get('page') === '0'
            ? [row(13), row(12), row(14, 8)]
            : [row(12), row(11)],
        totalPages: 2,
      };
    }
    if (path.endsWith('/12')) throw new Error('상세 조회 실패');
    return row(Number(path.split('/').at(-1)));
  });
  const cache = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 60000 } },
  });
  render(
    <QueryClientProvider client={cache}>
      <MemoryRouter initialEntries={['/adoptions/13?page=2']}>
        <Routes>
          <Route path="/adoptions/:id" element={<AdoptionDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  await screen.findByText('해당 동물의 신청서 1 / 3');
  expect(api.mock.calls.some(([p]) => p.endsWith('/12') || p.endsWith('/11'))).toBe(false);
  const move = (name: string) => fireEvent.click(screen.getByRole('button', { name }));
  move('다음 신청서');
  await screen.findByRole('alert');
  expect(screen.getByText('해당 동물의 신청서 2 / 3')).toBeVisible();
  move('이전 신청서');
  await screen.findByRole('heading', { name: '[개] 믹스' });
  move('다음 신청서');
  await screen.findByRole('alert');
  move('다음 신청서');
  await screen.findByRole('heading', { name: '[개] 진돗개' });
  expect(screen.getByText('해당 동물의 신청서 3 / 3')).toBeVisible();
  expect(screen.getByRole('link', { name: '이전' })).toHaveAttribute('href', '/adoptions?page=2');
});
