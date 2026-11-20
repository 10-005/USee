import { QueryClient } from '@tanstack/react-query';
import { afterEach, expect, it, vi } from 'vitest';
import { animalListOptions } from './animals';

afterEach(() => {
  vi.unstubAllGlobals();
  sessionStorage.clear();
});
it('보호 중 탭은 서버 정렬과 페이지 경계를 유지하며 두 보호 상태를 합친다', async () => {
  sessionStorage.setItem(
    'usee-shelter-session',
    JSON.stringify({ accessToken: 'test', refreshToken: 'r' }),
  );
  const card = (id: number) => ({
    animalSeq: id,
    classification: null,
    protectionStatus: id % 3 === 0 ? 'ENDED' : 'PROTECTING',
    adoptionStatus: id % 2 ? 'AVAILABLE' : 'UNAVAILABLE',
    gender: 'UNKNOWN',
    isNeutered: null,
    regDttm: '2026-11-09T00:00:00+09:00',
  });
  const cards = Array.from({ length: 24 }, (_, i) => card(i + 1));
  const called: number[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string) => {
      const url = new URL(input, 'https://example.org');
      const page = Number(url.searchParams.get('page'));
      called.push(page);
      expect(url.pathname).toBe('/api/core/v1/shelter-animals/me');
      expect(url.searchParams.get('period')).toBe('RECENT_7D');
      expect(url.searchParams.get('gender')).toBe('MALE');
      expect(url.searchParams.get('isNeutered')).toBe('false');
      expect(url.searchParams.get('sort')).toBe('regDttm,asc');
      const data = {
        statusCounts: {
          total: 24,
          PROTECTING_AVAILABLE: 8,
          PROTECTING_UNAVAILABLE: 8,
          ENDED: 8,
          ADOPTED: 0,
          RETURNED: 0,
        },
        page: { page, hasNext: page === 0, content: cards.slice(page * 12, (page + 1) * 12) },
      };
      return new Response(
        JSON.stringify({
          status: 200,
          code: 'SUCCESS',
          message: '성공',
          data,
          errors: [],
          traceId: null,
        }),
      );
    }),
  );
  const cache = new QueryClient();
  const result = await cache.fetchQuery(
    animalListOptions(
      { status: 'protected', period: '7', species: '', sex: 'male', adoptable: '', neutered: 'no' },
      2,
      true,
    ),
  );
  expect(result.animals.map((a) => a.id)).toEqual(['19', '20', '22', '23']);
  expect(result.counts).toMatchObject({ protected: 16, closed: 8, '': 24 });
  expect(called).toEqual([0, 1]);
  cache.clear();
});
