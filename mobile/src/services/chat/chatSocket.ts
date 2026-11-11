import { API_ORIGIN } from '../api/client';
import { getAccessToken } from '../auth';
import type { ChatMessageType, ChatMessageView, SharedTargetType } from './chatApi';

type SendMessage = {
  body?: string;
  clientMessageId: string;
  imageSeq?: number;
  messageType: ChatMessageType;
  sharedTargetSeq?: number;
  sharedTargetType?: SharedTargetType;
};

const encodeFrame = (command: string, headers: Record<string, string>, body = '') =>
  `${command}\n${Object.entries(headers)
    .map(([key, value]) => `${key}:${value}`)
    .join('\n')}\n\n${body}\0`;

export class ChatSocket {
  private socket?: WebSocket;
  private connected = false;
  private queue: string[] = [];
  private subscriptions = new Map<string, (message: ChatMessageView) => void>();
  private readListeners = new Set<{
    onRead: (roomSeq: number, cursor: number) => void;
    onConnect: () => void;
  }>();

  observeReadReceipts(onRead: (roomSeq: number, cursor: number) => void, onConnect: () => void) {
    const listener = { onRead, onConnect };
    this.readListeners.add(listener);
    return () => {
      this.readListeners.delete(listener);
    };
  }

  private pendingSends = new Map<
    string,
    { reject: (error: Error) => void; resolve: () => void; timer: ReturnType<typeof setTimeout> }
  >();

  connect() {
    if (this.socket && this.socket.readyState <= WebSocket.OPEN) {
      return;
    }
    const token = getAccessToken();
    if (!token) {
      throw new Error('로그인이 필요합니다.');
    }
    const NativeWebSocket = WebSocket as unknown as new (
      url: string,
      protocols: string[],
      options: { headers: Record<string, string> },
    ) => WebSocket;
    this.socket = new NativeWebSocket(
      `${API_ORIGIN.replace('https://', 'wss://')}/api/chat/v1/ws`,
      [],
      { headers: { origin: 'http://localhost:5173' } },
    );
    this.socket.onopen = () =>
      this.socket?.send(
        encodeFrame('CONNECT', {
          'accept-version': '1.2',
          Authorization: `Bearer ${token}`,
          authorization: `Bearer ${token}`,
          'heart-beat': '10000,10000',
          host: 'k15d106.p.ssafy.io',
        }),
      );
    this.socket.onmessage = event => this.onData(String(event.data));
    this.socket.onerror = () => this.rejectPending('채팅 서버에 연결하지 못했어요.');
    this.socket.onclose = () => {
      this.connected = false;
      this.socket = undefined;
      if (this.subscriptions.size > 0) {
        setTimeout(() => this.connect(), 1500);
      }
    };
  }

  subscribe(roomSeq: number, listener: (message: ChatMessageView) => void) {
    const destination = `/topic/rooms/${roomSeq}`;
    this.subscriptions.set(destination, listener);
    this.connect();
    if (this.connected) {
      this.sendSubscription(destination);
    }
    return () => {
      this.subscriptions.delete(destination);
      if (this.connected) {
        this.socket?.send(encodeFrame('UNSUBSCRIBE', { id: `room-${roomSeq}` }));
      }
    };
  }

  send(roomSeq: number, message: SendMessage): Promise<void> {
    this.connect();
    this.sendFrame(
      encodeFrame(
        'SEND',
        {
          destination: `/app/rooms/${roomSeq}/messages`,
          'content-type': 'application/json',
        },
        JSON.stringify({
          clientMessageId: message.clientMessageId,
          type: message.messageType,
          content: message.body,
          imageSeq: message.imageSeq,
          sharedTargetType: message.sharedTargetType,
          sharedTargetSeq: message.sharedTargetSeq,
        }),
      ),
    );
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pendingSends.delete(message.clientMessageId);
        reject(new Error('메시지 전송 확인 시간이 초과됐어요.'));
      }, 5_000);
      this.pendingSends.set(message.clientMessageId, { reject, resolve, timer });
    });
  }

  private sendFrame(frame: string) {
    if (this.connected && this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(frame);
    } else {
      this.queue.push(frame);
    }
  }

  private onData(data: string) {
    for (const frame of data.split('\0')) {
      if (!frame.trim() || frame === '\n') {
        continue;
      }
      const divider = frame.indexOf('\n\n');
      const head = (divider >= 0 ? frame.slice(0, divider) : frame).split('\n');
      const command = head.shift()?.trim();
      if (command === 'CONNECTED') {
        this.connected = true;
        this.socket?.send(
          encodeFrame('SUBSCRIBE', {
            destination: '/user/queue/errors',
            id: 'send-errors',
            ack: 'auto',
          }),
        );
        this.socket?.send(
          encodeFrame('SUBSCRIBE', {
            destination: '/user/queue/room-updates',
            id: 'room-updates',
            ack: 'auto',
          }),
        );
        this.readListeners.forEach(listener => listener.onConnect());
        this.subscriptions.forEach((_, destination) => this.sendSubscription(destination));
        this.queue.splice(0).forEach(item => this.socket?.send(item));
      } else if (command === 'MESSAGE' && divider >= 0) {
        const headers = Object.fromEntries(head.map(line => line.split(/:(.*)/s).slice(0, 2)));
        if (headers.subscription === 'room-updates') {
          const update = JSON.parse(frame.slice(divider + 2));
          const room = update.room;
          if (
            update.type === 'COUNTERPART_READ' &&
            Number.isSafeInteger(room?.chatRoomSeq) &&
            Number.isSafeInteger(room?.counterpartLastReadMessageSeq)
          ) {
            this.readListeners.forEach(listener =>
              listener.onRead(room.chatRoomSeq, room.counterpartLastReadMessageSeq),
            );
          }
          continue;
        }
        if (headers.subscription === 'send-errors') {
          const failure = JSON.parse(frame.slice(divider + 2));
          const reason = failure.message ?? '채팅 서버가 메시지를 거절했어요.';
          const id = failure.data?.clientMessageId;
          const pending = this.pendingSends.get(id);
          if (pending) {
            clearTimeout(pending.timer);
            this.pendingSends.delete(id);
            pending.reject(new Error(reason));
          } else if (!id) {
            this.rejectPending(reason);
          }
          continue;
        }
        const message = JSON.parse(frame.slice(divider + 2)) as ChatMessageView;
        if (message.clientMessageId) {
          const pending = this.pendingSends.get(message.clientMessageId);
          if (pending) {
            clearTimeout(pending.timer);
            this.pendingSends.delete(message.clientMessageId);
            pending.resolve();
          }
        }
        const listener = this.subscriptions.get(headers.destination);
        if (listener) {
          listener(message);
        }
      } else if (command === 'ERROR') {
        const body = divider >= 0 ? frame.slice(divider + 2).trim() : '';
        this.rejectPending(body || '채팅 서버가 메시지를 처리하지 못했어요.');
      }
    }
  }

  private sendSubscription(destination: string) {
    const roomSeq = destination.split('/').at(-1);
    this.socket?.send(
      encodeFrame('SUBSCRIBE', { destination, id: `room-${roomSeq}`, ack: 'auto' }),
    );
  }

  private rejectPending(message: string) {
    this.pendingSends.forEach(pending => {
      clearTimeout(pending.timer);
      pending.reject(new Error(message));
    });
    this.pendingSends.clear();
  }
}

export const chatSocket = new ChatSocket();
