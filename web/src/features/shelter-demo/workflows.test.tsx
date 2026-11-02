import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { App } from '@/app/App';
import { AppProviders } from '@/app/providers/AppProviders';
import { useShelterDemo } from './store';

function open(path: string) {
  render(
    <AppProviders>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </AppProviders>,
  );
}
beforeEach(() => {
  useShelterDemo.setState(useShelterDemo.getInitialState(), true);
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
describe('예시 업무 흐름', () => {
  it('최종 승인 사유를 입력하면 연결된 동물도 입양 완료로 변경된다', () => {
    const application = useShelterDemo.getState().adoptions.find((a) => a.status === '서류 승인')!;
    open(`/adoptions/${application.id}`);
    fireEvent.click(screen.getByRole('button', { name: '최종 승인' }));
    const dialog = screen.getByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: '확인' }));
    expect(within(dialog).getByText('최종 승인 사유를 입력해 주세요.')).toBeVisible();
    fireEvent.change(within(dialog).getByLabelText(/최종 승인 사유/), {
      target: { value: '방문 상담 완료' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: '확인' }));
    expect(
      useShelterDemo.getState().animals.find((a) => a.id === application.animalId),
    ).toMatchObject({
      status: 'adopted',
      adoptable: false,
      closingReason: '방문 상담 완료',
    });
    fireEvent.click(screen.getByRole('link', { name: '보호동물 정보 보기' }));
    expect(screen.getByRole('heading', { name: /입양 완료/ })).toBeVisible();
    expect(screen.getByRole('heading', { name: '입양 정보' })).toBeVisible();
    expect(screen.getByText('최종 승인 사유')).toBeVisible();
    expect(screen.getByText('방문 상담 완료')).toBeVisible();
  });
  it('발견 신고의 구조 상세에서 돌아오면 신고 상태와 정렬, 페이지를 유지한다', () => {
    open('/reports?status=신고%20진행%20중&sort=oldest&period=30&page=2');
    const detail = screen.getAllByRole('link', { name: /구조 상세보기/ })[0]!;
    const href = detail.getAttribute('href');
    fireEvent.click(detail);
    fireEvent.click(screen.getByRole('link', { name: '이전' }));
    expect(screen.getByRole('combobox', { name: '신고 상태' })).toHaveValue('신고 진행 중');
    expect(screen.getByRole('combobox', { name: '발견 기간' })).toHaveValue('30');
    expect(screen.getByRole('combobox', { name: '정렬' })).toHaveValue('oldest');
    expect(screen.getByRole('button', { name: '2페이지' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getAllByRole('link', { name: /구조 상세보기/ })[0]).toHaveAttribute('href', href);
  });
  it('완료된 구조에서 등록을 열면 해당 구조 정보와 발생장소를 불러온다', () => {
    const rescue = useShelterDemo.getState().rescues.find((r) => r.status === '완료')!;
    open(`/rescues/${rescue.id}`);
    fireEvent.click(screen.getByRole('link', { name: '보호동물 등록' }));
    expect(screen.getByRole('heading', { name: '연결된 구조 정보' })).toBeVisible();
    expect(screen.getByText(rescue.id)).toBeVisible();
    expect(screen.getByText(rescue.location)).toBeVisible();
    expect(screen.getByText(rescue.date.replace('T', ' '))).toBeVisible();
    expect(screen.getByLabelText('발생장소')).toHaveValue(rescue.location);
  });
  it('구조 상세에서 이전을 누르면 필터와 정렬이 적용된 2페이지로 돌아간다', () => {
    open('/rescues?source=direct&period=90&sort=oldest&page=2');
    const link = screen.getAllByRole('link', { name: '상세보기' })[0]!;
    const detailUrl = link.getAttribute('href');
    fireEvent.click(link);
    fireEvent.click(screen.getByRole('link', { name: '이전' }));
    expect(screen.getByRole('combobox', { name: '접수 경로' })).toHaveValue('direct');
    expect(screen.getByRole('combobox', { name: '기간' })).toHaveValue('90');
    expect(screen.getByRole('combobox', { name: '정렬' })).toHaveValue('oldest');
    expect(screen.getByRole('button', { name: '2페이지' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getAllByRole('link', { name: '상세보기' })[0]).toHaveAttribute('href', detailUrl);
  });
  it('보호동물 상세에서 이전을 누르면 보던 목록 필터와 정렬을 복원한다', () => {
    open(
      '/animals?status=protected&period=30&species=dog&sex=female&adoptable=true&neutered=yes&sort=oldest',
    );
    fireEvent.click(screen.getByRole('img', { name: '보호동물 예시 사진' }));
    expect(screen.getByRole('heading', { name: '보호동물 상세' })).toBeVisible();
    fireEvent.click(screen.getByRole('link', { name: '이전' }));
    expect(screen.getByRole('status')).toHaveTextContent('총 1마리');
    expect(
      within(screen.getByRole('group', { name: '상태 필터' })).getByRole('button', {
        name: /보호 중/,
      }),
    ).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('combobox', { name: '정렬' })).toHaveValue('oldest');
  });
  it('동물 상세에서 입양 신청을 열면 해당 동물의 건수와 신청만 표시한다', () => {
    open('/animals/sample-01');
    fireEvent.click(screen.getByRole('link', { name: '입양 신청 자세히 보기' }));
    expect(screen.getByRole('button', { name: '전체 1' })).toBeInTheDocument();
    const table = screen.getByRole('table', { name: '입양신청 목록' });
    expect(within(table).getByText('sample-01')).toBeVisible();
    expect(within(table).getAllByRole('link', { name: '상세보기' })).toHaveLength(1);
    fireEvent.click(within(table).getByRole('link', { name: '상세보기' }));
    fireEvent.click(screen.getByRole('link', { name: '이전' }));
    expect(screen.getByRole('button', { name: '전체 1' })).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByText('sample-01')).toBeVisible();
  });
  it('입양 상태 필터를 바꾸면 페이지를 초기화하고 실제 결과 수를 표시한다', () => {
    open('/adoptions?page=3');
    expect(screen.getByRole('button', { name: '3페이지' })).toHaveAttribute('aria-current', 'page');
    fireEvent.click(screen.getByRole('button', { name: '심사 대기 6' }));
    expect(screen.getByRole('status')).toHaveTextContent('총 6건');
    expect(screen.getByRole('button', { name: '1페이지' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getAllByRole('link', { name: '상세보기' })).toHaveLength(6);
  });
  it('거절 사유가 비어 있으면 저장하지 않고 사유 입력 후 상태를 변경한다', () => {
    open('/adoptions/A-1029-01');
    fireEvent.click(screen.getByRole('button', { name: '서류 거절' }));
    const dialog = screen.getByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: '확인' }));
    expect(screen.getByRole('alert')).toHaveTextContent('거절 사유를 입력');
    expect(useShelterDemo.getState().adoptions[0]?.status).toBe('심사 대기');
    fireEvent.change(screen.getByRole('textbox', { name: /거절 사유/ }), {
      target: { value: '돌봄 계획 보완 필요' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: '확인' }));
    expect(useShelterDemo.getState().adoptions[0]).toMatchObject({
      status: '거절',
      reason: '돌봄 계획 보완 필요',
    });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
  it('구조 접수 후 상세에서 상태를 저장하고 목록으로 복귀한다', () => {
    open('/rescues/new');
    fireEvent.click(screen.getByRole('button', { name: '접수하기' }));
    expect(screen.getByText('구조 사유를 선택해 주세요.')).toBeVisible();
    expect(screen.getByText('구조 장소를 입력해 주세요.')).toBeVisible();
    expect(screen.getByLabelText('구조일 *')).toHaveAccessibleDescription(
      '구조 날짜와 시간을 선택해 주세요.',
    );
    fireEvent.click(screen.getByLabelText('구조일 *'));
    fireEvent.change(screen.getByLabelText('연도'), { target: { value: '2026' } });
    fireEvent.change(screen.getByLabelText('월'), { target: { value: '11' } });
    fireEvent.click(screen.getByRole('button', { name: '2026-11-02' }));
    fireEvent.click(screen.getByRole('button', { name: '확인' }));
    fireEvent.change(screen.getByRole('combobox', { name: /구조 사유/ }), {
      target: { value: '부상·질병' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: /구조 장소/ }), {
      target: { value: '구미 공원' },
    });
    fireEvent.click(screen.getByRole('button', { name: '접수하기' }));
    expect(screen.getByRole('heading', { name: /R-demo-/ })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('radio', { name: '완료' }));
    fireEvent.click(screen.getByRole('button', { name: '상태 저장' }));
    expect(useShelterDemo.getState().rescues[0]).toMatchObject({
      status: '완료',
      location: '구미 공원',
    });
    fireEvent.click(screen.getByRole('link', { name: '이전' }));
    expect(screen.getByRole('status')).toHaveTextContent('총 25건');
  });
  it('등록 시 누락된 필수 항목을 표시하고 선택한 항목의 오류를 해제한다', () => {
    open('/animals/new');
    fireEvent.click(screen.getByRole('button', { name: '등록하기' }));
    expect(screen.getByText('최소 1장의 사진을 첨부해 주세요.')).toBeVisible();
    expect(screen.getByText('상태를 선택해 주세요.')).toBeVisible();
    expect(screen.getByLabelText('동물 종류 *')).toHaveAccessibleDescription(
      '동물 종류를 선택해 주세요.',
    );
    fireEvent.change(screen.getByLabelText('동물 종류 *'), { target: { value: 'cat' } });
    expect(screen.queryByText('동물 종류를 선택해 주세요.')).toBeNull();
    expect(screen.getByText('품종을 선택해 주세요.')).toBeVisible();
  });
  it('동물 수정에서 선택한 10일 공고기간과 기본정보를 상세에 반영한다', async () => {
    open('/animals/sample-01/edit');
    fireEvent.click(screen.getByLabelText('공고기간 *'));
    fireEvent.change(screen.getByLabelText('월'), { target: { value: '12' } });
    fireEvent.click(screen.getByRole('button', { name: '2026-12-01' }));
    fireEvent.click(screen.getByRole('button', { name: '확인' }));
    fireEvent.change(screen.getByLabelText('동물 종류 *'), { target: { value: 'cat' } });
    expect(screen.getByLabelText('품종 *')).toHaveValue('');
    expect(screen.getByLabelText('색상 *')).toBeDisabled();
    fireEvent.change(screen.getByLabelText('품종 *'), { target: { value: '러시안블루' } });
    fireEvent.change(screen.getByLabelText('색상 *'), { target: { value: '회색' } });
    fireEvent.click(screen.getByRole('button', { name: '수정하기' }));
    expect(await screen.findByRole('heading', { name: '보호동물 상세' })).toBeInTheDocument();
    expect(useShelterDemo.getState().animals[0]).toMatchObject({
      species: 'cat',
      period: '2026-12-01 ~ 2026-12-10',
      name: '러시안블루',
      color: '회색',
    });
  });
  it('예시 로그인은 지정한 계정만 통과하고 존재하지 않는 상세는 복귀 링크를 제공한다', () => {
    open('/login');
    fireEvent.change(screen.getByLabelText('아이디'), { target: { value: 'demo' } });
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'wrong' } });
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));
    expect(screen.getByRole('alert')).toHaveTextContent('실제 계정 인증은 연결 전');
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'demo' } });
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));
    expect(screen.getByRole('heading', { name: '보호소 홈' })).toBeInTheDocument();
    cleanup();
    open('/animals/not-existing');
    expect(screen.getByRole('heading', { name: '정보를 찾을 수 없습니다' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '이전' })).toHaveAttribute('href', '/animals');
  });
});
