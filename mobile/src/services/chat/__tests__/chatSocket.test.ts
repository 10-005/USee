import { ChatSocket } from '../chatSocket';

jest.mock('../../api/client', () => ({ API_ORIGIN: 'https://example.test' }));
jest.mock('../../auth', () => ({ getAccessToken: () => 'test-token' }));

it('개인 채널의 상대 읽음 이벤트만 전달하고 다시 연결되면 재구독한다', () => {
  const original = global.WebSocket;
  const send = jest.fn();
  const sockets: FakeSocket[] = [];
  class FakeSocket {
    static OPEN = 1;
    readyState = 1;
    send = send;
    onmessage!: (event: { data: string }) => void;
    onclose!: () => void;
    constructor() {
      sockets.push(this);
    }
  }
  global.WebSocket = FakeSocket as unknown as typeof WebSocket;
  jest.useFakeTimers();
  try {
    const chat = new ChatSocket();
    const onRead = jest.fn();
    const onConnect = jest.fn();
    const onMessage = jest.fn();
    chat.observeReadReceipts(onRead, onConnect);
    chat.subscribe(501, onMessage);
    sockets[0].onmessage({ data: 'CONNECTED\n\n\0' });
    for (const type of ['READ', 'MESSAGE', 'COUNTERPART_READ']) {
      const body = JSON.stringify({
        type,
        room: { chatRoomSeq: 501, counterpartLastReadMessageSeq: 102 },
      });
      sockets[0].onmessage({ data: 'MESSAGE\nsubscription:room-updates\n\n' + body + '\0' });
    }
    expect(onRead.mock.calls).toEqual([[501, 102]]);
    expect(onMessage).not.toHaveBeenCalled();
    sockets[0].onclose();
    jest.advanceTimersByTime(1500);
    sockets[1].onmessage({ data: 'CONNECTED\n\n\0' });
    expect(onConnect).toHaveBeenCalledTimes(2);
    expect(
      send.mock.calls.filter(([frame]) => frame.includes('/user/queue/room-updates')),
    ).toHaveLength(2);
  } finally {
    global.WebSocket = original;
    jest.useRealTimers();
  }
});
