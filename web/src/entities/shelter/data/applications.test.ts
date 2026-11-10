import { afterEach, expect, it, vi } from 'vitest';
import { QueryClient } from '@tanstack/react-query';
import { login, shelterRequest } from '@/shared/api/session';
import { receivedOptions, countOptions, detailOptions, submitReview } from './applications';

function reply(data: unknown, status = 200, code = 'SUCCESS') {
  return new Response(
    JSON.stringify({ status, code, message: code, data, errors: [], traceId: null }),
    { status },
  );
}
const session = { role: 'SHELTER', accessToken: 'first', refreshToken: 'refresh-1' };
afterEach(() => {
  sessionStorage.clear();
  vi.unstubAllGlobals();
});
it('보호소 수신 목록·건수·제출 당시 신청서 내용을 매핑한다', async () => {
  const item = {
    applicationSeq: 91,
    status: 'SUBMITTED',
    submittedDttm: '2026-11-09T12:00:00+09:00',
    animalSummary: null,
  };
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(reply(session))
    .mockResolvedValueOnce(
      reply({
        content: [{ ...item, applicantName: '김가람' }],
        page: 1,
        size: 8,
        totalElements: 9,
        totalPages: 2,
      }),
    )
    .mockResolvedValueOnce(reply({ total: 9, SUBMITTED: 9, APPROVED: 0, REJECTED: 0, ADOPTED: 0 }))
    .mockResolvedValueOnce(
      reply({
        ...item,
        applicant: { name: '김가람', phone: null, birthDate: null },
        questions: [
          { questionSeq: 4, questionText: '함께 사는 가족(동거인)이 있나요?' },
          { questionSeq: 5, questionText: '동거인 모두 입양에 동의했나요?' },
          { questionSeq: 6, questionText: '현재 함께 사는 다른 동물이 있나요?' },
          { questionSeq: 7, questionText: '함께 사는 동물을 소개해 주세요' },
          { questionSeq: 11, questionText: '동물 알레르기가 있는 가족이 있나요?' },
          { questionSeq: 13, questionText: '입양을 결심한 이유' },
          { questionSeq: 17, questionText: '입양 전 교육 이수 여부' },
          { questionSeq: 20, questionText: '이수일' },
        ],
        answers: {
          4: '있음',
          5: '모두 동의',
          6: '없음',
          11: '없음',
          13: '가족으로 맞이하고 싶습니다.',
          17: '미이수',
        },
      }),
    );
  vi.stubGlobal('fetch', fetchMock);
  await login('shelter', 'password');
  const cache = new QueryClient();
  const page = await cache.fetchQuery(
    receivedOptions(
      new URLSearchParams({ page: '2', animalId: '41', status: '심사 대기', sort: 'oldest' }),
    ),
  );
  const url = new URL(fetchMock.mock.calls[1]![0], 'https://example.org');
  expect(url.pathname).toBe('/api/core/v1/adoption-applications/received');
  expect(Object.fromEntries(url.searchParams)).toEqual({
    page: '1',
    size: '8',
    animalSeq: '41',
    status: 'SUBMITTED',
    sort: 'submittedDttm,asc',
  });
  expect(page.content[0]).toMatchObject({ id: '91', status: '심사 대기', applicant: '김가람' });
  expect((await cache.fetchQuery(countOptions('41'))).total).toBe(9);
  expect(fetchMock.mock.calls[2]![0]).toContain('/received/status-counts?animalSeq=41');
  const { questions } = await cache.fetchQuery(detailOptions('91'));
  expect(questions.map(({ title }) => title)).toEqual([
    '신청자 기본 정보',
    '주거 및 동거 환경',
    '양육 경험 및 돌봄 환경',
    '입양 및 돌봄 계획',
    '입양 전 교육',
  ]);
  expect(questions.slice(1).map(({ rows }) => rows)).toEqual([
    [
      ['함께 사는 가족(동거인)이 있나요?', '있음'],
      ['동거인 모두 입양에 동의했나요?', '모두 동의'],
      ['동물 알레르기가 있는 가족이 있나요?', '없음'],
    ],
    [['현재 함께 사는 다른 동물이 있나요?', '없음']],
    [['입양을 결심한 이유', '가족으로 맞이하고 싶습니다.']],
    [['입양 전 교육 이수 여부', '미이수']],
  ]);
  cache.clear();
});
it('심사 단계에 맞는 요청과 거절 사유를 보낸다', async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(reply(session))
    .mockImplementation(() => Promise.resolve(reply({})));
  vi.stubGlobal('fetch', fetchMock);
  await login('shelter', 'password');
  await submitReview('91', '심사 대기', '서류 승인');
  await submitReview('91', '심사 대기', '거절', '사유');
  await submitReview('91', '서류 승인', '거절', '사유');
  await submitReview('91', '서류 승인', '최종 승인');
  expect(
    fetchMock.mock.calls
      .slice(1)
      .map(([url, options]) => [
        url.split('/').pop(),
        options.method,
        options.body && JSON.parse(options.body),
      ]),
  ).toEqual([
    ['review', 'POST', { decision: 'APPROVE' }],
    ['review', 'POST', { decision: 'REJECT', rejectionReason: '사유' }],
    ['fail', 'POST', { rejectionReason: '사유' }],
    ['complete', 'POST', undefined],
  ]);
});
it('동시 만료 요청은 갱신 토큰을 한 번만 사용한다', async () => {
  const fetchMock = vi.fn(async (url: string, options: RequestInit) => {
    if (url.endsWith('/login')) return reply(session);
    if (url.endsWith('/refresh'))
      return reply({ accessToken: 'second', refreshToken: 'refresh-2' });
    return new Headers(options.headers).get('Authorization') === 'Bearer first'
      ? reply(null, 401, 'AUTH_TOKEN_EXPIRED')
      : reply('ok');
  });
  vi.stubGlobal('fetch', fetchMock);
  await login('shelter', 'password');
  expect(await Promise.all([shelterRequest('/a'), shelterRequest('/b')])).toEqual(['ok', 'ok']);
  expect(fetchMock.mock.calls.filter(([url]) => url.endsWith('/refresh'))).toHaveLength(1);
});
