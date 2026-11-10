import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { expect, it, vi } from 'vitest';
import { App } from '@/app/App';
import { AppProviders } from '@/app/providers/AppProviders';

vi.mock('@/shared/config/demo', () => ({ demoMode: false }));
it('로그인한 담당자가 목록에서 신청서를 열고 승인하면 목록 건수도 갱신된다', async () => {
  sessionStorage.clear();
  let status = 'SUBMITTED';
  const api = vi.fn(async (input: string, options: RequestInit) => {
    const url = new URL(input, 'https://example.org');
    const path = url.pathname.replace('/api/core/v1', '');
    let data: unknown;
    const item = {
      applicationSeq: 51,
      status,
      submittedDttm: '2026-11-09T09:00:00+09:00',
      animalSummary: null,
    };
    const report = {
      sessionSeq: 77,
      animalName: '봄이',
      breed: { breedName: '샴' },
      startedDttm: '2026-11-01T12:00:00+09:00',
      endedDttm: '2026-11-03T12:00:00+09:00',
      totalSpent: 1200,
    };
    if (path === '/auth/local/login') {
      expect(JSON.parse(String(options.body))).toEqual({
        loginId: 'shelter',
        password: 'password',
      });
      data = { role: 'SHELTER', accessToken: 'access', refreshToken: 'refresh' };
    } else {
      expect(new Headers(options.headers).get('Authorization')).toBe('Bearer access');
      switch (path) {
        case '/shelter-animals/me':
          data = {
            statusCounts: {
              total: 0,
              PROTECTING_AVAILABLE: 0,
              PROTECTING_UNAVAILABLE: 0,
              RETURNED: 0,
              ADOPTED: 0,
              ENDED: 0,
            },
            page: { content: [], page: 0, totalElements: 0, hasNext: false },
          };
          break;
        case '/adoption-applications/received':
          data = {
            content: [{ ...item, applicantName: '신청자 김봄' }],
            totalElements: 1,
            totalPages: 1,
            page: 0,
            size: 8,
          };
          break;
        case '/adoption-applications/received/status-counts':
          data = {
            total: 1,
            SUBMITTED: Number(status === 'SUBMITTED'),
            APPROVED: Number(status === 'APPROVED'),
            REJECTED: 0,
            ADOPTED: 0,
          };
          break;
        case '/adoption-applications/51':
          data = {
            ...item,
            applicant: { name: '신청자 김봄', birthDate: null, phone: '01000000000' },
            questions: [],
            answers: {},
          };
          break;
        case '/adoption-applications/51/simulation-reports':
          data = [report];
          break;
        case '/api/simulation/v1/simulations/77/report':
          data = {
            ...report,
            status: 'ABANDONED',
            personality: 'TIMID',
            expectedCost: 1000,
            spentByCategory: { EVENT: 1200 },
            dailySpent: { '2': 1200 },
            finalStats: {
              satiety: 55,
              hydration: 60,
              cleanliness: 70,
              bond: 62,
              calmness: 72,
              health: 84,
              energy: 58,
            },
            actionCount: 6,
            eventCount: 2,
            eventResolvedCount: 1,
            walkCount: 0,
            summary: null,
          };
          break;
        case '/adoption-applications/51/review':
          expect(JSON.parse(String(options.body))).toEqual({ decision: 'APPROVE' });
          status = 'APPROVED';
          data = { status };
          break;
        default:
          throw new Error(path);
      }
    }
    return new Response(
      JSON.stringify({
        status: 200,
        code: 'SUCCESS',
        data,
        message: '성공',
        errors: [],
        traceId: null,
      }),
    );
  });
  vi.stubGlobal('fetch', api);
  try {
    render(
      <AppProviders>
        <MemoryRouter initialEntries={['/adoptions']}>
          <App />
        </MemoryRouter>
      </AppProviders>,
    );
    fireEvent.change(screen.getByLabelText('아이디'), { target: { value: 'shelter' } });
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'password' } });
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));
    expect(await screen.findByRole('heading', { name: '보호소 홈' })).toBeInTheDocument();
    fireEvent.click(await screen.findByRole('link', { name: '입양 신청 1건 목록 보기' }));
    fireEvent.click(await screen.findByRole('link', { name: '51' }));
    expect(await screen.findByText('01000000000')).toBeVisible();
    fireEvent.click(screen.getByRole('link', { name: '모의 입양 리포트 보기' }));
    fireEvent.click(await screen.findByRole('link', { name: '상세 보기' }));
    expect(await screen.findByText('중도 포기')).toBeVisible();
    expect(screen.getByText('요약 준비 중')).toBeVisible();
    expect(screen.getByText('200원 초과')).toBeVisible();
    expect(screen.getByRole('button', { name: '2일차 지출 1,200원' })).toBeVisible();
    fireEvent.click(screen.getByRole('link', { name: '리포트 목록' }));
    fireEvent.click(await screen.findByRole('link', { name: '입양신청 상세' }));
    await screen.findByText('01000000000');
    const dialog = document.querySelector('dialog')!;
    dialog.showModal = () => {
      dialog.open = true;
    };
    dialog.close = () => {
      dialog.open = false;
    };
    fireEvent.click(screen.getByRole('button', { name: '서류 승인' }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '확인' }));
    expect(await screen.findByRole('button', { name: '최종 승인' })).toBeVisible();
    fireEvent.click(screen.getByRole('link', { name: '이전' }));
    expect(await screen.findByRole('button', { name: /서류 승인\s*1/ })).toBeVisible();
  } finally {
    vi.unstubAllGlobals();
    sessionStorage.clear();
  }
});
