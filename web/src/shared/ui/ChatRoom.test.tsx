import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router';
import { ShelterPageLayout } from '@/app/layouts/ShelterPageLayout';
import { useDemoChat } from '@/shared/model/demo-chat';
import { App } from '@/app/App';
import { AppProviders } from '@/app/providers/AppProviders';

beforeEach(() => {
  useDemoChat.setState(useDemoChat.getInitialState(), true);
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
  Reflect.deleteProperty(HTMLDialogElement.prototype, 'showModal');
  Reflect.deleteProperty(HTMLDialogElement.prototype, 'close');
});

it('게시글 카드로 상세 페이지에 이동해도 열린 채팅방과 작성 중인 메시지를 유지한다', () => {
  render(
    <AppProviders>
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    </AppProviders>,
  );
  fireEvent.click(screen.getByRole('button', { name: /채팅 열기/ }));
  fireEvent.click(screen.getByRole('button', { name: /시샤/ }));
  fireEvent.change(screen.getByRole('textbox', { name: '메시지' }), {
    target: { value: '작성 중' },
  });
  fireEvent.click(within(screen.getByLabelText('대화 내용')).getByRole('link', { name: /보호/ }));
  expect(screen.getByRole('heading', { name: '보호동물 상세' })).toBeVisible();
  expect(screen.getByRole('button', { name: '채팅 닫기' })).toHaveAttribute(
    'aria-expanded',
    'true',
  );
  expect(screen.getByRole('textbox', { name: '메시지' })).toHaveValue('작성 중');
  expect(
    within(screen.getByLabelText('대화 내용')).getByText('아이 몇 개월인지 알 수 있을까요?'),
  ).toBeVisible();
});

it('채팅방에서 메시지를 보내고 목록으로 돌아가면 최근 메시지가 반영된다', () => {
  render(
    <MemoryRouter>
      <ShelterPageLayout>본문</ShelterPageLayout>
    </MemoryRouter>,
  );
  fireEvent.click(screen.getByRole('button', { name: /채팅 열기/ }));
  fireEvent.click(screen.getByRole('button', { name: /시샤/ }));
  expect(
    within(screen.getByLabelText('대화 내용')).getByRole('link', { name: /보호/ }),
  ).toHaveAttribute('href', '/animals/sample-01');
  fireEvent.change(screen.getByRole('textbox', { name: '메시지' }), {
    target: { value: '안녕하세요' },
  });
  fireEvent.click(screen.getByRole('button', { name: '메시지 보내기' }));
  expect(within(screen.getByLabelText('대화 내용')).getByText('안녕하세요')).toBeVisible();
  expect(within(screen.getByLabelText('대화 내용')).getByText('안읽음')).toBeVisible();
  const sent = useDemoChat
    .getState()
    .messages['sample-chat-1']?.find(
      (message) => message.type === 'message' && message.sender === 'me',
    );
  expect(sent).toBeDefined();
  act(() => useDemoChat.getState().markReadByRecipient('sample-chat-1', [sent!.id]));
  expect(within(screen.getByLabelText('대화 내용')).queryByText('안읽음')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '채팅 목록으로' }));
  expect(screen.getByRole('button', { name: /시샤/ })).toHaveTextContent('안녕하세요');
});

it('신고와 나가기 확인창이 채팅 영역에 표시되고 나가면 대화 목록에서 삭제한다', () => {
  render(
    <MemoryRouter>
      <ShelterPageLayout>본문</ShelterPageLayout>
    </MemoryRouter>,
  );
  fireEvent.click(screen.getByRole('button', { name: /채팅 열기/ }));
  fireEvent.click(screen.getByRole('button', { name: /시샤/ }));
  fireEvent.click(screen.getByRole('button', { name: '채팅방 메뉴' }));
  expect(screen.queryByRole('button', { name: '차단하기' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '신고하기' }));
  const report = screen.getByRole('dialog', { name: '사용자 신고' });
  expect(screen.getByRole('complementary', { name: '채팅' })).toContainElement(report);
  expect(within(report).getByText('신고 대상 · 시샤')).toBeVisible();
  expect(within(report).getByRole('button', { name: '신고하기' })).toBeDisabled();
  fireEvent.click(within(report).getByLabelText('스팸 / 광고'));
  fireEvent.click(within(report).getByRole('button', { name: '신고하기' }));
  expect(screen.getByRole('status')).toHaveTextContent('실제 신고는 접수되지 않았습니다');
  fireEvent.click(screen.getByRole('button', { name: '채팅방 메뉴' }));
  fireEvent.click(screen.getByRole('button', { name: '채팅방 나가기' }));
  fireEvent.click(
    within(screen.getByRole('dialog')).getByRole('button', { name: '채팅방 나가기' }),
  );
  expect(screen.queryByRole('button', { name: /시샤/ })).not.toBeInTheDocument();
});
