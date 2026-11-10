import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, expect, it, vi } from 'vitest';
import { AppProviders } from '@/app/providers/AppProviders';
import { LiveChatSidebar } from './LiveChatSidebar';

const broker = vi.hoisted(() => ({
  subscriptions: new Map<string, (frame: { body: string }) => void>(),
  publish: vi.fn(),
}));
vi.mock('@stomp/stompjs', () => ({
  Client: class {
    connected = true;
    publish = broker.publish;
    constructor(private options: { beforeConnect: () => Promise<void>; onConnect: () => void }) {}
    async activate() {
      await this.options.beforeConnect();
      this.options.onConnect();
    }
    deactivate() {
      this.connected = false;
    }
    subscribe(path: string, fn: (frame: { body: string }) => void) {
      broker.subscriptions.set(path, fn);
      return { unsubscribe: () => broker.subscriptions.delete(path) };
    }
  },
}));
afterEach(() => {
  vi.unstubAllGlobals();
  sessionStorage.clear();
  broker.subscriptions.clear();
});
it('서버 이력과 구독 메시지를 합치고 전송 확인을 받으면 초안을 지운다', async () => {
  sessionStorage.setItem(
    'usee-shelter-session',
    JSON.stringify({ accessToken: 'test', refreshToken: 'refresh' }),
  );
  const room = {
    chatRoomSeq: 9,
    counterpart: { userSeq: 2, displayName: '신청자', profileImage: null },
    unreadCount: 1,
    lastMessage: null,
    lastMessageDttm: null,
  };
  const old = {
    chatRoomSeq: 9,
    chatMessageSeq: 15,
    senderUserSeq: 2,
    messageType: 'TEXT',
    body: '첫 문의',
  };
  const fetchMock = vi.fn(async (input: string, options: RequestInit) => {
    expect(new Headers(options.headers).get('Authorization')).toBe('Bearer test');
    const path = new URL(input, 'http://localhost').pathname;
    let data;
    if (path.endsWith('/rooms')) data = { content: [room], hasNext: false };
    else if (path.endsWith('/messages')) data = { content: [old], hasNext: false };
    else if (path.endsWith('/unread-count')) data = { totalUnreadMessageCount: 1 };
    else if (path.endsWith('/read')) data = { unreadCount: 0 };
    else if (path.endsWith('/adoption-applications/received')) {
      data = { content: [{ applicationSeq: 71 }, { applicationSeq: 72 }], hasNext: false };
    } else if (path.endsWith('/71') || path.endsWith('/72')) {
      const mine = path.endsWith('/71');
      data = {
        applicationSeq: mine ? 71 : 72,
        userSeq: mine ? 2 : 3,
        applicant: { name: '신청자' },
        questions: [],
        answers: {},
        status: 'SUBMITTED',
        animalSummary: { animalSeq: 10, speciesName: '개', breedName: '말티즈' },
      };
    } else throw new Error(path);
    return Response.json({
      status: 200,
      code: 'SUCCESS',
      message: '성공',
      data,
      errors: [],
      traceId: null,
    });
  });
  vi.stubGlobal('fetch', fetchMock);
  render(
    <AppProviders>
      <MemoryRouter>
        <LiveChatSidebar />
      </MemoryRouter>
    </AppProviders>,
  );
  fireEvent.click(screen.getByRole('button', { name: /채팅 열기/ }));
  fireEvent.click(await screen.findByRole('button', { name: /신청자/ }));
  expect(await screen.findByText('첫 문의')).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: '신청자 프로필 보기' }));
  expect(await screen.findByRole('link', { name: /신청 71번/ })).toHaveAttribute(
    'href',
    '/adoptions/71',
  );
  expect(screen.queryByRole('link', { name: /신청 72번/ })).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: '닫기' }));
  fireEvent.click(screen.getByRole('button', { name: '신청자 상대방 정보' }));
  fireEvent.click(await screen.findByRole('link', { name: /신청 71번/ }));
  expect(screen.queryByRole('dialog')).toBeNull();
  const receive = (message: object) =>
    act(() => broker.subscriptions.get('/topic/rooms/9')!({ body: JSON.stringify(message) }));
  receive(old);
  expect(screen.getAllByText('첫 문의')).toHaveLength(1);
  receive({ ...old, chatMessageSeq: 16, body: '방문 가능한가요?' });
  expect(screen.getByText('방문 가능한가요?')).toBeVisible();
  const input = screen.getByRole('textbox', { name: '메시지' });
  fireEvent.change(input, { target: { value: '네, 가능합니다.' } });
  fireEvent.click(screen.getByRole('button', { name: '메시지 보내기' }));
  const frame = broker.publish.mock.calls[0]![0];
  expect(frame.destination).toBe('/app/rooms/9/messages');
  expect(input).toHaveValue('네, 가능합니다.');
  receive({
    ...old,
    chatMessageSeq: 17,
    senderUserSeq: 99,
    clientMessageId: JSON.parse(frame.body).clientMessageId,
    body: '네, 가능합니다.',
  });
  await waitFor(() => expect(input).toHaveValue(''));
  expect(screen.getByText('네, 가능합니다.')).toBeVisible();
});
