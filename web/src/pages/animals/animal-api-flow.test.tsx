import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import { afterEach, expect, it, vi } from 'vitest';
import { AppProviders } from '@/app/providers/AppProviders';
import { ReportsPage } from '../records/ReportsPage';
import { RescueFormPage, RescueDetailPage } from '../records/RescuePages';
import { AnimalsPage } from './AnimalsPage';
import { AnimalDetailPage } from './AnimalDetailPage';
import { AnimalFormPage } from './AnimalFormPage';

vi.mock('@/shared/config/demo', () => ({ demoMode: false }));
afterEach(() => {
  sessionStorage.clear();
  vi.unstubAllGlobals();
});
it('구조 상세 서버 오류 시 목록에서 같은 기록을 찾아 한국 시간으로 표시하고 재조회로 복구한다', async () => {
  sessionStorage.setItem(
    'usee-shelter-session',
    JSON.stringify({ accessToken: 'test', refreshToken: 'refresh' }),
  );
  const row = {
    rescueSeq: 1,
    status: 'DONE',
    rescueReasonCode: 'OTHER',
    rescuedDttm: '2026-11-09T16:15:00Z',
    regDttm: '2026-11-10T01:30:00+09:00',
  };
  let recovered = false;
  vi.stubGlobal('fetch', async (input: string) => {
    const url = new URL(input, 'http://localhost');
    let data;
    if (url.pathname.endsWith('/rescues/1')) {
      if (!recovered)
        return new Response(JSON.stringify({ message: '서버 내부 오류' }), { status: 500 });
      data = { ...row, rescueAddress: '인동시장', linkedAnimalCount: 0 };
    } else if (url.pathname.endsWith('/rescues')) {
      const first = url.searchParams.get('page') === '0';
      data = { page: { content: first ? [{ ...row, rescueSeq: 2 }] : [row], totalPages: 2 } };
    } else throw new Error(url.pathname);
    return Response.json({
      status: 200,
      code: 'SUCCESS',
      message: '성공',
      data,
      errors: [],
      traceId: null,
    });
  });
  render(
    <AppProviders>
      <MemoryRouter initialEntries={['/rescues/1']}>
        <Routes>
          <Route path="/rescues/:id" element={<RescueDetailPage />} />
        </Routes>
      </MemoryRouter>
    </AppProviders>,
  );
  expect(await screen.findByText('2026-11-10 01:15', {}, { timeout: 4000 })).toBeInTheDocument();
  expect(screen.getByText('2026-11-10 01:30')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '구조일지 삭제' })).toBeNull();
  recovered = true;
  fireEvent.click(screen.getByRole('button', { name: '상세 다시 불러오기' }));
  expect(await screen.findByText('인동시장')).toBeInTheDocument();
});
it('담당 발견 신고를 구조 접수·완료한 뒤 같은 구조 번호로 보호동물을 등록하고 수정한다', async () => {
  sessionStorage.setItem(
    'usee-shelter-session',
    JSON.stringify({ accessToken: 'test', refreshToken: 'test-refresh' }),
  );
  const classification = {
    combinationSeq: 47,
    speciesSeq: 2,
    speciesName: '고양이',
    breedSeq: 32,
    breedName: '샴',
    colorSeq: 11,
    colorName: '회색',
  };
  const detail = {
    animalSeq: 23,
    classification,
    gender: 'FEMALE',
    isNeutered: false,
    age: null,
    weight: '3kg',
    regDttm: '2026-11-09T12:00:00+09:00',
    protectionStatus: 'PROTECTING',
    adoptionStatus: 'AVAILABLE',
    noticeStartDate: '2026-12-01',
    noticeEndDate: '2026-12-10',
    images: [{ imageSeq: 81, imageUrl: 'https://example.org/cat.png' }],
    rescueInfo: {},
    applicationCounts: { SUBMITTED: 0, APPROVED: 0 },
    healthCheck: null as string | null,
  };
  const found = {
    foundReportSeq: 41,
    classification,
    address: '구미 공원',
    occurredDttm: '2026-11-02T09:00:00+09:00',
    reporter: { name: '신고자' },
    status: 'ACTIVE',
    rescueStatus: 'NONE',
  };
  let rescueStatus = 'PROCESSING';
  let registered = false;
  let uploads = 0;
  let failManagement = true;
  const basicPatches: unknown[] = [];
  const api = vi.fn(async (url: string, options: RequestInit) => {
    const path = new URL(url, 'https://example.org').pathname;
    if (url === 'https://example.org/signed') {
      expect(options.method).toBe('PUT');
      expect(new Headers(options.headers).has('Authorization')).toBe(false);
      expect(new Headers(options.headers).get('If-None-Match')).toBe('*');
      uploads++;
      return new Response(null, { status: 200 });
    }
    const body = options.body ? JSON.parse(String(options.body)) : null;
    let data: unknown;
    let status = 200;
    switch (path) {
      case '/api/core/v1/found-reports/assigned':
        data = { content: [found], totalElements: 1, totalPages: 1, page: 0 };
        break;
      case '/api/core/v1/found-reports/41':
        data = found;
        break;
      case '/api/core/v1/animal-species':
      case '/api/core/v1/districts':
        data = [];
        break;
      case '/api/core/v1/rescues':
        expect(options.method).toBe('POST');
        expect(body).toMatchObject({
          foundReportSeq: 41,
          rescueReasonCode: 'INJURY_ILLNESS',
          rescueAddress: '구미 공원',
          rescuedDttm: '2026-11-02T09:00:00+09:00',
        });
        data = { rescueSeq: 89 };
        status = 201;
        break;
      case '/api/core/v1/rescues/89':
        data = {
          rescueSeq: 89,
          foundReportSeq: 41,
          status: rescueStatus,
          rescueAddress: '구미 공원',
          rescuedDttm: '2026-11-02T09:00:00+09:00',
          rescueReasonCode: 'INJURY_ILLNESS',
          foundReportSummary: found,
          linkedAnimalCount: 0,
        };
        break;
      case '/api/core/v1/rescues/89/status':
        expect(options.method).toBe('PATCH');
        expect(body).toEqual({ status: 'DONE' });
        rescueStatus = 'DONE';
        data = { rescueSeq: 89, status: rescueStatus };
        break;
      case '/api/core/v1/shelter-animals/me':
        data = {
          statusCounts: {
            total: registered ? 1 : 0,
            PROTECTING_AVAILABLE: registered ? 1 : 0,
            PROTECTING_UNAVAILABLE: 0,
            RETURNED: 0,
            ADOPTED: 0,
            ENDED: 0,
          },
          page: { page: 0, hasNext: false, content: registered ? [detail] : [] },
        };
        break;
      case '/api/core/v1/shelter-animals/me/23':
        data = detail;
        break;
      case '/api/core/v1/animal-combinations':
        data = [classification];
        break;
      case '/api/core/v1/images/upload-urls':
        expect(body.contentType).toBe('image/png');
        data = { uploadUrl: 'https://example.org/signed', imageKey: 'key', expiresInSeconds: 600 };
        break;
      case '/api/core/v1/images':
        expect(body).toEqual({ imageKey: 'key' });
        data = { imageSeq: 81 };
        break;
      case '/api/core/v1/shelter-animals':
        expect(body).toMatchObject({
          rescueSeq: 89,
          combinationSeq: 47,
          gender: 'FEMALE',
          isNeutered: false,
          imageSeqs: [81],
          adoptionStatus: 'AVAILABLE',
        });
        status = 201;
        registered = true;
        data = { animalSeq: 23 };
        break;
      case '/api/core/v1/shelter-animals/23':
        expect(body).toEqual({ healthCheck: '검진 완료', isNeutered: true });
        detail.isNeutered = body.isNeutered;
        basicPatches.push(body);
        detail.healthCheck = body.healthCheck;
        data = detail;
        break;
      case '/api/core/v1/shelter-animals/23/management':
        expect(body).toEqual({ status: 'PROTECTING', adoptionStatus: 'UNAVAILABLE' });
        if (failManagement) {
          failManagement = false;
          return new Response(JSON.stringify({ message: '잠시 후 다시 시도해 주세요.' }), {
            status: 409,
          });
        }
        detail.adoptionStatus = 'UNAVAILABLE';
        data = { animalSeq: 23 };
        break;
      default:
        throw new Error(path);
    }
    return new Response(
      JSON.stringify({ status, code: 'SUCCESS', message: '성공', data, errors: [], traceId: null }),
      { status },
    );
  });
  vi.stubGlobal('fetch', api);
  vi.stubGlobal(
    'URL',
    class extends URL {
      static createObjectURL() {
        return 'blob:preview';
      }
      static revokeObjectURL() {}
    },
  );
  render(
    <AppProviders>
      <MemoryRouter initialEntries={['/reports']}>
        <Routes>
          <Route path="/reports" element={<ReportsPage />} />
          <Route path="/rescues/new" element={<RescueFormPage />} />
          <Route path="/rescues/:id" element={<RescueDetailPage />} />
          <Route path="/animals" element={<AnimalsPage />} />
          <Route path="/animals/new" element={<AnimalFormPage />} />
          <Route path="/animals/:id" element={<AnimalDetailPage />} />
          <Route path="/animals/:id/edit" element={<AnimalFormPage />} />
        </Routes>
      </MemoryRouter>
    </AppProviders>,
  );
  fireEvent.click(await screen.findByRole('link', { name: '구조 접수' }));
  fireEvent.change(await screen.findByRole('combobox', { name: /구조 사유/ }), {
    target: { value: '부상·질병' },
  });
  fireEvent.click(screen.getByLabelText('구조 일시 *'));
  fireEvent.change(screen.getByLabelText('연도'), { target: { value: '2026' } });
  fireEvent.change(screen.getByLabelText('월'), { target: { value: '11' } });
  fireEvent.click(screen.getByRole('button', { name: '2026-11-02' }));
  fireEvent.click(screen.getByRole('button', { name: '확인' }));
  fireEvent.click(screen.getByRole('button', { name: '접수하기' }));
  fireEvent.click(await screen.findByRole('radio', { name: '완료' }));
  const dialog = screen.getByText('구조 상태를 저장하시겠어요?').closest('dialog')!;
  dialog.showModal = () => {
    dialog.open = true;
  };
  dialog.close = () => {
    dialog.open = false;
  };
  fireEvent.click(screen.getByRole('button', { name: '상태 저장' }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '저장' }));
  fireEvent.click(await screen.findByRole('link', { name: '보호동물 등록' }));
  expect(await screen.findByText('구미 공원')).toBeVisible();
  fireEvent.change(await screen.findByLabelText('동물 사진'), {
    target: { files: [new File(['cat'], 'cat.png', { type: 'image/png' })] },
  });
  fireEvent.change(screen.getByLabelText('동물 종류 *'), { target: { value: 'cat' } });
  fireEvent.change(screen.getByLabelText('품종 *'), { target: { value: '32' } });
  fireEvent.change(screen.getByLabelText('색상 *'), { target: { value: '회색' } });
  fireEvent.change(screen.getByLabelText('성별 *'), { target: { value: 'female' } });
  fireEvent.click(screen.getByRole('radio', { name: '미완료' }));
  fireEvent.click(screen.getByLabelText('공고기간 *'));
  fireEvent.change(screen.getByLabelText('연도'), { target: { value: '2026' } });
  fireEvent.change(screen.getByLabelText('월'), { target: { value: '12' } });
  fireEvent.click(screen.getByRole('button', { name: '2026-12-01' }));
  fireEvent.click(screen.getByRole('button', { name: '확인' }));
  fireEvent.click(screen.getByRole('radio', { name: '입양 가능' }));
  fireEvent.click(screen.getByRole('button', { name: '등록하기' }));
  fireEvent.click(await screen.findByRole('link', { name: '수정하기' }));
  expect(await screen.findByDisplayValue('3kg')).toBeVisible();
  expect(screen.getByRole('radio', { name: '미완료' })).toBeChecked();
  fireEvent.click(screen.getByRole('radio', { name: '완료' }));
  fireEvent.change(screen.getByLabelText('건강검진 특이사항'), { target: { value: '검진 완료' } });
  fireEvent.click(screen.getByRole('radio', { name: '입양 불가' }));
  fireEvent.click(screen.getByRole('button', { name: '수정하기' }));
  expect(await screen.findByText(/기본정보는 저장됐습니다/)).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: '수정하기' }));
  expect(await screen.findByText('검진 완료')).toBeVisible();
  expect(basicPatches).toHaveLength(1);
  expect(uploads).toBe(1);
});
