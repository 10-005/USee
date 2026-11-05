import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
  sessionStorage.setItem('usee-demo-login', 'true');
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
  vi.unstubAllGlobals();
  cleanup();
  Reflect.deleteProperty(HTMLDialogElement.prototype, 'showModal');
  Reflect.deleteProperty(HTMLDialogElement.prototype, 'close');
});
describe('예시 업무 흐름', () => {
  it('수정 화면에서 삭제를 취소하거나 확인할 수 있다', async () => {
    const original = useShelterDemo.getState().animals[1]!;
    const adoptions = useShelterDemo.getState().adoptions;
    open(`/animals/${original.id}/edit`);
    fireEvent.click(screen.getByRole('button', { name: '삭제하기' }));
    let dialog = screen.getByRole('dialog', { name: '보호동물을 삭제할까요?' });
    expect(dialog).toHaveTextContent('복구할 수 없습니다');
    fireEvent.click(within(dialog).getByRole('button', { name: '취소' }));
    expect(useShelterDemo.getState().animals.some((item) => item.id === original.id)).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: '삭제하기' }));
    dialog = screen.getByRole('dialog', { name: '보호동물을 삭제할까요?' });
    fireEvent.click(within(dialog).getByRole('button', { name: '삭제하기' }));
    expect(await screen.findByRole('heading', { name: '보호동물 목록' })).toBeInTheDocument();
    expect(useShelterDemo.getState().animals.some((item) => item.id === original.id)).toBe(false);
    expect(useShelterDemo.getState().adoptions).toEqual(adoptions);
  });
  it.each([
    ['실패', '취소'],
    ['취소', '실패'],
  ] as const)(
    '구조 상세와 수정 화면에서 %s 및 %s 사유를 입력해야 저장한다',
    async (first, second) => {
      const rescue = useShelterDemo.getState().rescues.find((r) => r.status === '진행 중')!;
      open(`/rescues/${rescue.id}`);
      fireEvent.click(screen.getByRole('radio', { name: first }));
      const field = screen.getByRole('textbox', { name: `${first} 사유 *` });
      fireEvent.change(field, { target: { value: '   ' } });
      fireEvent.click(screen.getByRole('button', { name: '상태 저장' }));
      expect(screen.getByRole('alert')).toHaveTextContent(`${first} 사유를 입력해 주세요.`);
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      fireEvent.change(field, { target: { value: ' 현장에서 동물을 찾지 못함 ' } });
      fireEvent.click(screen.getByRole('button', { name: '상태 저장' }));
      fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '저장' }));
      fireEvent.click(await screen.findByRole('link', { name: '수정' }));
      expect(screen.getByLabelText(`${first} 사유 *`)).toHaveValue('현장에서 동물을 찾지 못함');
      fireEvent.click(screen.getByRole('radio', { name: second }));
      fireEvent.click(screen.getByRole('button', { name: '저장하기' }));
      expect(screen.getByRole('alert')).toHaveTextContent(`${second} 사유를 입력해 주세요.`);
      expect(useShelterDemo.getState().rescues.find((r) => r.id === rescue.id)?.status).toBe(first);
      fireEvent.change(screen.getByLabelText(`${second} 사유 *`), {
        target: { value: '현장 확인 결과' },
      });
      fireEvent.click(screen.getByRole('button', { name: '저장하기' }));
      expect(await screen.findByRole('link', { name: '수정' })).toBeVisible();
    },
  );
  it('구조 상세는 접수 일시와 구조 일시를 각각 표시하고 수정 후에도 접수 일시를 유지한다', () => {
    const rescue = useShelterDemo.getState().rescues[0]!;
    open(`/rescues/${rescue.id}`);
    expect(screen.getByText('접수 일시', { exact: false })).toHaveTextContent(
      rescue.receivedAt!.replace('T', ' '),
    );
    expect(screen.getByText(rescue.date.replace('T', ' '))).toBeVisible();
    fireEvent.click(screen.getByRole('link', { name: '수정' }));
    fireEvent.click(screen.getByRole('button', { name: '저장하기' }));
    expect(
      useShelterDemo.getState().rescues.find((item) => item.id === rescue.id)?.receivedAt,
    ).toBe(rescue.receivedAt);
  });
  it('보호동물 상세에 내부 식별자 대신 예시 공고번호를 표시한다', () => {
    open('/animals/sample-01');
    expect(screen.getByText('공고번호 경북-구미-2026-00001')).toBeVisible();
  });
  it('구조 수정 화면에서 진행 상태를 바꾸고 저장할 수 있다', async () => {
    const rescue = useShelterDemo.getState().rescues.find((item) => item.status === '진행 중')!;
    open(`/rescues/${rescue.id}/edit`);
    const group = screen.getByRole('group', { name: '구조 상태' });
    expect(within(group).getByRole('radio', { name: '진행 중' })).toBeChecked();
    fireEvent.click(within(group).getByRole('radio', { name: '완료' }));
    expect(useShelterDemo.getState().rescues.find((item) => item.id === rescue.id)?.status).toBe(
      '진행 중',
    );
    fireEvent.click(screen.getByRole('button', { name: '저장하기' }));
    expect(useShelterDemo.getState().rescues.find((item) => item.id === rescue.id)?.status).toBe(
      '완료',
    );
    expect(await screen.findByRole('link', { name: '보호동물 등록' })).toBeVisible();
  });
  it('사유 없이 최종 승인하면 연결된 동물도 입양 완료로 변경된다', () => {
    const application = useShelterDemo.getState().adoptions.find((a) => a.status === '서류 승인')!;
    open(`/adoptions/${application.id}`);
    fireEvent.click(screen.getByRole('button', { name: '최종 승인' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).queryByRole('textbox')).not.toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: '확인' }));
    expect(
      useShelterDemo.getState().animals.find((a) => a.id === application.animalId),
    ).toMatchObject({
      status: 'adopted',
      adoptable: false,
      closingReason: undefined,
    });
    fireEvent.click(screen.getByRole('link', { name: '보호동물 정보 보기' }));
    expect(screen.getByRole('heading', { name: /입양 완료/ })).toBeVisible();
    expect(screen.queryByText('최종 승인 사유')).not.toBeInTheDocument();
  });
  it('발견 신고에서 진행 중과 완료 탭의 건수와 카드가 각각 구조 상태와 일치한다', () => {
    open('/reports');
    const tabs = within(screen.getByRole('group', { name: '접수 상태' }));
    for (const [status, count] of [
      ['진행 중', 6],
      ['완료', 1],
    ] as const) {
      const tab = tabs.getByRole('button', { name: `${status} ${count}` });
      fireEvent.click(tab);
      expect(tab).toHaveAttribute('aria-pressed', 'true');
      const cards = screen.getAllByRole('article');
      expect(cards).toHaveLength(count);
      for (const card of cards) {
        expect(within(card).getByText(`구조 ${status}`)).toBeVisible();
      }
    }
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
  it('완료된 구조에서 등록을 열면 상단에 연결된 구조 신고 카드를 표시한다', () => {
    const rescue = useShelterDemo.getState().rescues.find((r) => r.status === '완료')!;
    open(`/rescues/${rescue.id}`);
    fireEvent.click(screen.getByRole('link', { name: '보호동물 등록' }));
    expect(screen.getByRole('heading', { name: '연결된 구조 신고' })).toBeVisible();
    expect(screen.getByText(rescue.id)).toBeVisible();
    expect(screen.getByText(rescue.location)).toBeVisible();
    expect(screen.getByText(rescue.date.replace('T', ' '))).toBeVisible();
    expect(screen.queryByLabelText('발생장소')).not.toBeInTheDocument();
  });
  it('완료된 구조에서 보호동물을 등록하면 구조 연결과 장소를 유지한다', async () => {
    vi.stubGlobal(
      'URL',
      class extends URL {
        static createObjectURL() {
          return 'blob:test-photo';
        }
        static revokeObjectURL() {}
      },
    );
    const rescue = useShelterDemo.getState().rescues.find((r) => r.status === '완료')!;
    open(`/rescues/${rescue.id}`);
    fireEvent.click(screen.getByRole('link', { name: '보호동물 등록' }));
    fireEvent.change(screen.getByLabelText('동물 사진'), {
      target: { files: [new File(['photo'], 'cat.png', { type: 'image/png' })] },
    });
    fireEvent.change(screen.getByLabelText('동물 종류 *'), { target: { value: 'dog' } });
    const breedField = screen.getByLabelText<HTMLSelectElement>('품종 *');
    expect([...breedField.options].at(-1)?.value).toBe('기타');
    expect([...breedField.options].filter((o) => o.text === '도고 아르젠티노')).toHaveLength(1);
    fireEvent.change(breedField, { target: { value: '도고 아르젠티노' } });
    expect(
      [...screen.getByLabelText<HTMLSelectElement>('색상 *').options].map((o) => o.value),
    ).toEqual(['', '은색&흰색', '흰색', '밝은 금색', '살구색', '크림색', '기타']);
    fireEvent.change(screen.getByLabelText('동물 종류 *'), { target: { value: 'cat' } });
    expect([...breedField.options].at(-1)?.value).toBe('기타');
    fireEvent.change(screen.getByLabelText('품종 *'), { target: { value: '러시안 블루' } });
    expect(
      Array.from(screen.getByLabelText<HTMLSelectElement>('색상 *').options, (o) => o.value),
    ).toEqual(['', '파란색', '푸른 회색', '기타']);
    fireEvent.change(screen.getByLabelText('색상 *'), { target: { value: '푸른 회색' } });
    fireEvent.change(screen.getByLabelText('성별 *'), { target: { value: 'female' } });
    fireEvent.click(screen.getByRole('radio', { name: '미완료' }));
    fireEvent.click(screen.getByLabelText('공고기간 *'));
    fireEvent.change(screen.getByLabelText('연도'), { target: { value: '2026' } });
    fireEvent.change(screen.getByLabelText('월'), { target: { value: '12' } });
    fireEvent.click(screen.getByRole('button', { name: '2026-12-01' }));
    fireEvent.click(screen.getByRole('button', { name: '확인' }));
    expect(screen.queryByRole('radio', { name: '보호 중' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('radio', { name: '입양 가능' }));
    fireEvent.click(screen.getByRole('button', { name: '등록하기' }));
    expect(await screen.findByRole('heading', { name: '보호동물 상세' })).toBeVisible();
    expect(useShelterDemo.getState().animals[0]).toMatchObject({
      rescueId: rescue.id,
      location: rescue.location,
      name: '러시안 블루',
      status: 'protected',
    });
    const registeredAnimal = useShelterDemo.getState().animals[0]!;
    const rescueLink = screen.getByRole('link', { name: `${rescue.id} 구조 상세보기` });
    expect(screen.getByRole('heading', { name: '구조정보' }).closest('section')).toContainElement(
      rescueLink,
    );
    expect(within(rescueLink).queryByText('구조 장소')).not.toBeInTheDocument();
    expect(rescueLink).toHaveAttribute('href', `/rescues/${rescue.id}`);
    fireEvent.click(rescueLink);
    expect(screen.queryByRole('link', { name: '보호동물 등록' })).not.toBeInTheDocument();
    const detailLink = screen.getByRole('link', { name: '등록된 보호동물 상세보기' });
    expect(detailLink).toHaveAttribute('href', `/animals/${registeredAnimal.id}`);
    fireEvent.click(detailLink);
    expect(screen.getByRole('heading', { name: '보호동물 상세' })).toBeVisible();
  });
  it('구조 상세에서 이전을 누르면 필터와 정렬이 적용된 2페이지로 돌아간다', () => {
    open('/rescues?source=direct&period=90&sort=oldest&page=2');
    const link = screen.getAllByRole('link', { name: /^R-/ })[0]!;
    const detailUrl = link.getAttribute('href');
    fireEvent.click(link.closest('tr')!);
    fireEvent.click(screen.getByRole('link', { name: '이전' }));
    expect(screen.getByRole('combobox', { name: '접수 경로' })).toHaveValue('direct');
    expect(screen.getByRole('combobox', { name: '기간' })).toHaveValue('90');
    expect(screen.getByRole('combobox', { name: '정렬' })).toHaveValue('oldest');
    expect(screen.getByRole('button', { name: '2페이지' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getAllByRole('link', { name: /^R-/ })[0]).toHaveAttribute('href', detailUrl);
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
    const table = screen.getByRole('table', { name: '입양 신청 목록' });
    expect(within(table).getByText('공고번호 경북-구미-2026-00001')).toBeVisible();
    expect(within(table).getAllByRole('link', { name: /^A-/ })).toHaveLength(1);
    fireEvent.click(within(table).getByRole('link', { name: /^A-/ }).closest('tr')!);
    fireEvent.click(screen.getByRole('link', { name: '이전' }));
    expect(screen.getByRole('button', { name: '전체 1' })).toBeInTheDocument();
    expect(
      within(screen.getByRole('table')).getByText('공고번호 경북-구미-2026-00001'),
    ).toBeVisible();
  });
  it('입양 상태 필터를 바꾸면 페이지를 초기화하고 실제 결과 수를 표시한다', () => {
    open('/adoptions?page=3');
    expect(screen.getByRole('button', { name: '3페이지' })).toHaveAttribute('aria-current', 'page');
    fireEvent.click(screen.getByRole('button', { name: '심사 대기 6' }));
    expect(screen.getByRole('status')).toHaveTextContent('총 6건');
    expect(screen.getByRole('button', { name: '1페이지' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getAllByRole('link', { name: /^A-/ })).toHaveLength(6);
  });
  it('거절 사유가 비어 있으면 저장하지 않고 사유 입력 후 상태를 변경한다', async () => {
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
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
  it('구조 접수 후 상세에서 상태를 저장하고 목록으로 복귀한다', async () => {
    open('/rescues/new');
    fireEvent.click(screen.getByRole('button', { name: '접수하기' }));
    expect(screen.getByText('구조 사유를 선택해 주세요.')).toBeVisible();
    expect(screen.getByText('구조 장소를 입력해 주세요.')).toBeVisible();
    expect(screen.getByLabelText('구조 일시 *')).toHaveAccessibleDescription(
      '구조 일시를 선택해 주세요.',
    );
    fireEvent.click(screen.getByLabelText('구조 일시 *'));
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
    expect(await screen.findByRole('heading', { name: /R-demo-/ })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('radio', { name: '완료' }));
    fireEvent.click(screen.getByRole('button', { name: '상태 저장' }));
    expect(useShelterDemo.getState().rescues[0]?.status).toBe('진행 중');
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '저장' }));
    expect(useShelterDemo.getState().rescues[0]).toMatchObject({
      status: '완료',
      location: '구미 공원',
    });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    fireEvent.click(screen.getByRole('link', { name: '이전' }));
    expect(screen.getByRole('status')).toHaveTextContent('총 25건');
  });
  it('등록 시 누락된 필수 항목을 표시하고 선택한 항목의 오류를 해제한다', () => {
    open('/animals/new');
    fireEvent.click(screen.getByRole('button', { name: '등록하기' }));
    expect(screen.getByText('최소 1장의 사진을 첨부해 주세요.')).toBeVisible();
    expect(screen.queryByText('상태를 선택해 주세요.')).not.toBeInTheDocument();
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
    fireEvent.change(screen.getByLabelText('품종 *'), { target: { value: '러시안 블루' } });
    fireEvent.change(screen.getByLabelText('색상 *'), { target: { value: '푸른 회색' } });
    fireEvent.click(screen.getByRole('button', { name: '수정하기' }));
    expect(await screen.findByRole('heading', { name: '보호동물 상세' })).toBeInTheDocument();
    expect(useShelterDemo.getState().animals[0]).toMatchObject({
      species: 'cat',
      period: '2026-12-01 ~ 2026-12-10',
      name: '러시안 블루',
      color: '푸른 회색',
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
