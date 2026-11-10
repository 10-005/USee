import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { ChatSidebar, type ChatSummary } from '@/shared/ui/ChatSidebar';
import { ChatRoomView } from '@/shared/ui/ChatRoom';
import { animalLabel } from '@/entities/shelter/animal-label';
import { adoptionStatusLabels } from '@/entities/shelter/data/applications';
import {
  chatRequest,
  counterpartOptions,
  historyOptions,
  roomsOptions,
  timeLabel,
  unreadOptions,
  type Message,
  type Room,
} from '@/entities/chat/data/live';
import { useChatSocket } from '@/entities/chat/data/socket';
import type { ChatMessage } from '@/entities/chat/model';
import avatar from '@/shared/assets/figma/chat-avatar.png';
import { createImageUploader } from '@/shared/api/image-upload';
import { refreshChatToken, shelterRequest } from '@/shared/api/session';

function CounterpartCard({
  roomId,
  onClose,
  identity,
}: {
  roomId: number;
  onClose: () => void;
  identity: (displayName?: string, avatarSrc?: string) => ReactNode;
}) {
  const query = useQuery(counterpartOptions(roomId));
  if (query.isError)
    return (
      <>
        {identity()}
        <button type="button" onClick={() => void query.refetch()}>
          상대방 정보 다시 불러오기
        </button>
      </>
    );
  if (query.isPending)
    return (
      <>
        {identity()}
        <p role="status">상대방 정보를 불러오고 있어요.</p>
      </>
    );
  const { nickname, realName, profileImage, applications } = query.data;
  return (
    <>
      {identity(
        realName ? nickname + ' (' + realName + ')' : nickname,
        profileImage?.imageUrl || avatar,
      )}
      {applications.length > 0 && (
        <section className="chat-profile__applications">
          <h3>관련 입양 신청</h3>
          {applications.map((item) => (
            <Link
              key={item.applicationSeq}
              to={'/adoptions/' + item.applicationSeq}
              onClick={onClose}
            >
              <strong>{animalLabel(item.speciesName, item.breedName)}</strong>
              <span>
                {item.submittedDttm?.slice(0, 10) || '미등록'} · 신청 {item.applicationSeq}번 ·{' '}
                {adoptionStatusLabels[item.status]}
              </span>
            </Link>
          ))}
        </section>
      )}
    </>
  );
}
function summary(room: Room): ChatSummary {
  const last = room.lastMessage;
  return {
    id: String(room.chatRoomSeq),
    name: room.counterpart.displayName,
    avatarSrc: room.counterpart.profileImage?.imageUrl || avatar,
    preview:
      last?.body ||
      (last?.messageType === 'IMAGE'
        ? '사진을 보냈어요'
        : last?.messageType === 'SHARED_POST'
          ? '게시글을 공유했어요'
          : ''),
    unreadCount: room.unreadCount,
    timeLabel: timeLabel(room.lastMessageDttm),
  };
}
export function LiveChatSidebar() {
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<Room>();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [drafts, setDrafts] = useState<Record<number, string>>({});
  const connection = useChatSocket();
  const rooms = useInfiniteQuery({ ...roomsOptions(unreadOnly), enabled: open });
  const unread = useQuery(unreadOptions);
  const items = new Map(
    rooms.data?.pages.flatMap((p) => p.content).map((room) => [room.chatRoomSeq, room]),
  );
  return (
    <ChatSidebar
      open={open}
      onOpenChange={setOpen}
      conversations={Array.from(items.values(), summary)}
      totalUnread={unread.data?.totalUnreadMessageCount}
      onSelect={(id) => setSelected(items.get(Number(id)))}
      onFilterChange={setUnreadOnly}
      listStatus={
        rooms.error ? (
          <button onClick={() => void rooms.refetch()}>대화 목록 다시 불러오기</button>
        ) : rooms.isPending ? (
          <p role="status">대화 목록을 불러오는 중입니다.</p>
        ) : undefined
      }
      listFooter={
        rooms.hasNextPage && (
          <button disabled={rooms.isFetching} onClick={() => void rooms.fetchNextPage()}>
            대화 더 보기
          </button>
        )
      }
      roomView={
        open && selected ? (
          <LiveRoom
            key={selected.chatRoomSeq}
            room={selected}
            connection={connection}
            text={drafts[selected.chatRoomSeq] ?? ''}
            setText={(text) => setDrafts((prev) => ({ ...prev, [selected.chatRoomSeq]: text }))}
            onBack={() => setSelected(undefined)}
          />
        ) : undefined
      }
    />
  );
}

function displayMessage(m: Message, other: number | null): ChatMessage {
  if (
    m.messageType === 'SHARED_POST' &&
    !m.sharedPostUnavailable &&
    m.sharedTargetType === 'SHELTER_ANIMAL' &&
    m.sharedTargetSeq
  )
    return { type: 'post', id: String(m.chatMessageSeq), animalId: String(m.sharedTargetSeq) };
  return {
    type: 'message',
    id: String(m.chatMessageSeq),
    sender: m.senderUserSeq === other ? 'other' : 'me',
    time: timeLabel(m.regDttm),
    image: m.imageUrl ?? undefined,
    text:
      m.body ??
      (m.messageType === 'IMAGE'
        ? '사진'
        : m.sharedPostUnavailable
          ? '확인할 수 없는 게시글입니다.'
          : '게시글을 공유했어요.'),
  };
}
type Connection = ReturnType<typeof useChatSocket>;
type SendResult = { id: string; resolve: () => void; reject: (error: Error) => void };
function LiveRoom({
  room,
  connection,
  text,
  setText,
  onBack,
}: {
  room: Room;
  connection: Connection;
  text: string;
  setText: (text: string) => void;
  onBack: () => void;
}) {
  const id = room.chatRoomSeq;
  const cache = useQueryClient();
  const history = useInfiniteQuery(historyOptions(id));
  const [incoming, setIncoming] = useState<Message[]>([]);
  const pending = useRef<SendResult | null>(null);
  const [upload] = useState(createImageUploader);
  const activeClient = useRef<NonNullable<Connection>['client'] | null>(null);
  useEffect(() => {
    if (!connection) return;
    const { client } = connection;
    if (!client.connected) return;
    const errors = client.subscribe('/user/queue/errors', (frame) => {
      const error = JSON.parse(frame.body);
      if (pending.current && pending.current.id === error.data?.clientMessageId)
        pending.current.reject(new Error(error.message));
      if (error.code === 'AUTH_TOKEN_EXPIRED')
        void refreshChatToken(client.connectHeaders.Authorization?.slice(7) ?? '')
          .finally(() => client.forceDisconnect())
          .catch(() => {});
    });
    const subscription = client.subscribe('/topic/rooms/' + id, (frame) => {
      const message: Message = JSON.parse(frame.body);
      if (message.chatRoomSeq !== id) return;
      setIncoming((prev) =>
        prev.some((m) => m.chatMessageSeq === message.chatMessageSeq) ? prev : [...prev, message],
      );
      if (pending.current && message.clientMessageId === pending.current.id)
        pending.current.resolve();
    });
    activeClient.current = client;
    void cache.invalidateQueries({ queryKey: historyOptions(id).queryKey });
    return () => {
      activeClient.current = null;
      pending.current?.reject(
        new Error('연결이 끊겼습니다. 대화 내역에서 전송 결과를 확인해 주세요.'),
      );
      if (client.connected) {
        subscription.unsubscribe();
        errors.unsubscribe();
      }
    };
  }, [connection, id, cache]);
  const byId = new Map(
    history.data?.pages.flatMap((page) => page.content).map((m) => [m.chatMessageSeq, m]),
  );
  for (const m of incoming) byId.set(m.chatMessageSeq, m);
  const ordered = Array.from(byId.values()).sort((a, b) => a.chatMessageSeq - b.chatMessageSeq);
  const latest = ordered.at(-1)?.chatMessageSeq;
  useEffect(() => {
    if (!latest) return;
    function markVisibleRoomRead() {
      if (document.visibilityState !== 'visible') return;
      void chatRequest('/rooms/' + id + '/read', {
        method: 'PATCH',
        json: { lastReadMessageSeq: latest },
      })
        .then(() => {
          if (!connection?.client.connected) {
            void cache.invalidateQueries({ queryKey: ['chat', 'rooms'] });
            void cache.invalidateQueries({ queryKey: ['chat', 'unread'] });
          }
        })
        .catch(() => {});
    }
    markVisibleRoomRead();
    document.addEventListener('visibilitychange', markVisibleRoomRead);
    return () => document.removeEventListener('visibilitychange', markVisibleRoomRead);
  }, [id, latest, cache, connection]);

  function send(payload: Record<string, unknown>) {
    const client = activeClient.current;
    if (!client?.connected) return Promise.reject(new Error('채팅 연결을 기다려 주세요.'));
    const clientMessageId = crypto.randomUUID();
    let timer: number;
    return new Promise<void>((resolve, reject) => {
      timer = window.setTimeout(
        () =>
          reject(
            new Error('전송 확인이 지연되고 있어요. 다시 보내기 전에 대화 내역을 확인해 주세요.'),
          ),
        10000,
      );
      pending.current = { id: clientMessageId, resolve, reject };
      try {
        client.publish({
          destination: '/app/rooms/' + id + '/messages',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ ...payload, clientMessageId }),
        });
      } catch (error) {
        reject(error);
      }
    }).finally(() => {
      window.clearTimeout(timer);
      pending.current = null;
    });
  }
  return (
    <ChatRoomView
      chat={summary(room)}
      renderProfile={(close, identity) => (
        <CounterpartCard roomId={id} onClose={close} identity={identity} />
      )}
      onBack={onBack}
      text={text}
      setText={setText}
      messages={ordered.map((m) => displayMessage(m, room.counterpart.userSeq))}
      connectionNotice={connection ? undefined : '실시간 채팅에 연결하는 중입니다.'}
      historyControls={
        <>
          {history.error ? (
            <button onClick={() => void history.refetch()}>대화 내역 다시 불러오기</button>
          ) : (
            history.isPending && <p role="status">대화 내역을 불러오는 중입니다.</p>
          )}
          {history.hasNextPage && (
            <button disabled={history.isFetching} onClick={() => void history.fetchNextPage()}>
              이전 메시지 보기
            </button>
          )}
        </>
      }
      sendText={(content) => send({ type: 'TEXT', content })}
      sendPhoto={async (file) => send({ type: 'IMAGE', imageSeq: await upload.confirm(file) })}
      renderPost={(seq) => (
        <Link className="chat-post" to={'/animals/' + seq}>
          공유된 보호동물 보기
        </Link>
      )}
      leave={async () => {
        await chatRequest('/rooms/' + id + '/leave', { method: 'POST' });
        cache.removeQueries({ queryKey: historyOptions(id).queryKey });
        setText('');
        void cache.invalidateQueries({ queryKey: ['chat', 'rooms'] });
        void cache.invalidateQueries({ queryKey: ['chat', 'unread'] });
      }}
      report={async (reason) => {
        const codes: Record<string, string> = {
          '욕설 / 비방 / 괴롭힘': 'HARASSMENT',
          '스팸 / 광고': 'SPAM_AD',
          '음란 / 부적절한 내용': 'OBSCENE',
          기타: 'OTHER',
        };
        await shelterRequest('/reports', {
          method: 'POST',
          json: {
            targetType: 'USER',
            targetSeq: room.counterpart.userSeq,
            reasonCode: codes[reason],
          },
        });
      }}
    />
  );
}
