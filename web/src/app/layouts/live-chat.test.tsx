import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, expect, it, vi } from 'vitest';
import { AppProviders } from '@/app/providers/AppProviders';
import { LiveChatSidebar } from './LiveChatSidebar';

const broker = vi.hoisted(() => ({
  subscriptions: new Map<string, (frame: { body: string }) => void>(),
  publish: vi.fn(),
  reconnect: () => {},
}));
vi.mock('@stomp/stompjs', () => ({
  Client: class {
    connected = true;
    publish = broker.publish;
    constructor(private options: { beforeConnect: () => Promise<void>; onConnect: () => void }) {}
    async activate() {
      await this.options.beforeConnect();
      broker.reconnect = () => this.options.onConnect();
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
  Reflect.deleteProperty(HTMLDialogElement.prototype, 'showModal');
  Reflect.deleteProperty(HTMLDialogElement.prototype, 'close');
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
  let serverCursor: number | null = null;
  let serverMessages = [old];
  let failReadOnce = false;
  const fetchMock = vi.fn(async (input: string, options: RequestInit) => {
    expect(new Headers(options.headers).get('Authorization')).toBe('Bearer test');
    const path = new URL(input, 'http://localhost').pathname;
    let data;
    if (path.endsWith('/rooms')) data = { content: [room], hasNext: false };
    else if (path.endsWith('/rooms/9')) data = { counterpartLastReadMessageSeq: serverCursor };
    else if (path.endsWith('/messages')) data = { content: serverMessages, hasNext: false };
    else if (path.endsWith('/shelter-animals/me/42'))
      data = {
        animalSeq: 42,
        classification: { speciesName: '개', breedName: '말티즈', colorName: '흰색' },
        gender: 'MALE',
        age: '2살',
        weight: '3.5kg',
        protectionStatus: 'PROTECTING',
        images: [{ imageUrl: '/maltese.jpg' }],
        regDttm: '2026-11-10',
        rescueInfo: {},
      };
    else if (/\/(missing|found)-reports\/42$/.test(path))
      data = {
        classification: { speciesName: '개', breedName: '진돗개', colorName: '흰색' },
        status: 'ACTIVE',
        gender: 'UNKNOWN',
        address: '구미',
        occurredDttm: '2026-11-10T12:00:00+09:00',
        description: path.includes('missing-reports') ? '빨간 목줄' : '공원에서 발견',
        representativeImageUrl: '/dog.jpg',
        images: [],
      };
    else if (path.endsWith('/read') && failReadOnce) {
      failReadOnce = false;
      throw new Error('offline');
    } else if (path.endsWith('/unread-count')) data = { totalUnreadMessageCount: 1 };
    else if (path.endsWith('/read')) data = { unreadCount: 0 };
    else if (path === '/api/chat/v1/chats/rooms/9/counterpart') {
      data = {
        nickname: '회원닉네임',
        realName: '김가람',
        profileImage: null,
        applications: [
          {
            applicationSeq: 71,
            speciesName: '개',
            breedName: '말티즈',
            status: 'SUBMITTED',
            submittedDttm: null,
          },
        ],
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
  await waitFor(() => expect(broker.subscriptions.has('/user/queue/room-updates')).toBe(true));
  act(() =>
    broker.subscriptions.get('/user/queue/room-updates')!({
      body: JSON.stringify({ type: 'MESSAGE', room, totalUnreadMessageCount: 3 }),
    }),
  );
  expect(
    await screen.findByRole('button', { name: '채팅 열기, 안 읽은 메시지 3개' }),
  ).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: /채팅 열기/ }));
  fireEvent.click(await screen.findByRole('button', { name: /신청자/ }));
  expect(await screen.findByText('첫 문의')).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: '신청자 프로필 보기' }));
  expect(await screen.findByRole('link', { name: /신청 71번/ })).toHaveAttribute(
    'href',
    '/adoptions/71',
  );
  expect(screen.queryByRole('link', { name: /신청 72번/ })).toBeNull();
  expect(screen.getByText('회원닉네임 (김가람)')).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: '닫기' }));
  fireEvent.click(screen.getByRole('button', { name: '신청자 상대방 정보' }));
  fireEvent.click(await screen.findByRole('link', { name: /신청 71번/ }));
  expect(screen.queryByRole('dialog')).toBeNull();
  const receive = (message: object) =>
    act(() => broker.subscriptions.get('/topic/rooms/9')!({ body: JSON.stringify(message) }));
  receive(old);
  expect(screen.getAllByText('첫 문의')).toHaveLength(1);
  const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
  receive({ ...old, chatMessageSeq: 16, body: '방문 가능한가요?' });
  expect(screen.getByText('방문 가능한가요?')).toBeVisible();
  const readLatest = () =>
    fetchMock.mock.calls.some(
      ([input, options]) =>
        input.endsWith('/read') && options.body === JSON.stringify({ lastReadMessageSeq: 16 }),
    );
  expect(readLatest()).toBe(false);
  failReadOnce = true;
  visibility.mockReturnValue('visible');
  fireEvent(document, new Event('visibilitychange'));
  await waitFor(() => expect(readLatest()).toBe(true));
  await waitFor(
    () =>
      expect(
        fetchMock.mock.calls.filter(
          ([url, opts]) =>
            url.endsWith('/read') && opts.body === JSON.stringify({ lastReadMessageSeq: 16 }),
        ),
      ).toHaveLength(2),
    { timeout: 3000 },
  );
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
  receive({
    ...old,
    chatMessageSeq: 18,
    messageType: 'SHARED_POST',
    sharedTargetType: 'SHELTER_ANIMAL',
    sharedTargetSeq: 42,
  });
  const card = await screen.findByRole('link', {
    name: /보호.*말티즈.*2살 \| 수컷 \| 흰색 \| 3.5kg/,
  });
  expect(card).toHaveAttribute('href', '/animals/42');
  expect(card.querySelector('img')).toHaveAttribute('src', '/maltese.jpg');
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
  for (const [i, kind, label, description] of [
    [19, 'MISSING', '실종', '빨간 목줄'],
    [20, 'FOUND', '발견', '공원에서 발견'],
  ] as const) {
    receive({
      ...old,
      chatMessageSeq: i,
      messageType: 'SHARED_POST',
      sharedTargetType: kind,
      sharedTargetSeq: 42,
    });
    fireEvent.click(await screen.findByRole('button', { name: new RegExp(label + '.*원문 보기') }));
    expect(screen.getByRole('dialog', { name: label + ' 신고 원문' })).toBeVisible();
    expect(screen.getByText(description)).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: '닫기' }));
  }
  expect(screen.getAllByText('안읽음')).toHaveLength(1);
  receive({
    ...old,
    chatMessageSeq: 21,
    senderUserSeq: 99,
    messageType: 'IMAGE',
    imageUrl: '/mine.jpg',
  });
  expect(screen.getAllByText('안읽음')).toHaveLength(2);
  const counterpartRead = (roomId: number, seq: number) =>
    act(() =>
      broker.subscriptions.get('/user/queue/room-updates')!({
        body: JSON.stringify({
          type: 'COUNTERPART_READ',
          room: { chatRoomSeq: roomId, counterpartLastReadMessageSeq: seq },
          totalUnreadMessageCount: 0,
        }),
      }),
    );
  counterpartRead(9, 17);
  await waitFor(() => expect(screen.getAllByText('안읽음')).toHaveLength(1));
  counterpartRead(9, 16);
  counterpartRead(10, 21);
  // Returning to the tab can yield an older snapshot than the last socket event.
  serverCursor = 15;
  const detailCalls = () => fetchMock.mock.calls.filter(([url]) => url.endsWith('/rooms/9')).length;
  const beforeFocus = detailCalls();
  visibility.mockReturnValue('hidden');
  fireEvent(window, new Event('visibilitychange'));
  visibility.mockReturnValue('visible');
  fireEvent(window, new Event('visibilitychange'));
  await waitFor(() => expect(detailCalls()).toBeGreaterThan(beforeFocus));
  expect(screen.getAllByText('안읽음')).toHaveLength(1);
  serverCursor = 21;
  act(() => broker.reconnect());
  await waitFor(() => expect(screen.queryByText('안읽음')).toBeNull());

  const first = { ...old, chatMessageSeq: 22, body: '첫 번째 연속 메시지' };
  const second = { ...old, chatMessageSeq: 23, body: '두 번째 연속 메시지' };
  serverMessages = [second, first, old];
  receive(second);
  act(() =>
    broker.subscriptions.get('/user/queue/room-updates')!({
      body: JSON.stringify({ type: 'MESSAGE', room, totalUnreadMessageCount: 2 }),
    }),
  );
  expect(await screen.findByText(first.body)).toBeVisible();
  expect(screen.getAllByText(/번째 연속 메시지/).map((element) => element.textContent)).toEqual([
    first.body,
    second.body,
  ]);
});
