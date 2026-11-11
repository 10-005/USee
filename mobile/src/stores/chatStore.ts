import { create } from 'zustand';

import {
  chatSocket,
  createChatRoom,
  getChatMessages,
  getChatRoom,
  getChatRooms,
  leaveChatRoom,
  markChatRoomRead,
  type ChatCounterpart,
  type ChatMessageView,
  type ChatTargetType,
} from '../services/chat';
import { confirmImage, uploadImageToPendingStorage } from '../services/images';
import { useProfileStore } from './profileStore';

export type ChatPhoto = {
  byteSize: number;
  contentType: 'image/jpeg' | 'image/png';
  fileName: string;
  height: number;
  uri: string;
  width: number;
};
export type PostType = 'adoption' | 'found' | 'lost';
export type PostRef = { type: PostType; id: string };
export type ChatPreview = { name: string; post: PostRef };
export function samePost(a?: PostRef, b?: PostRef) {
  return a?.type === b?.type && a?.id === b?.id;
}
type Message = {
  id: string;
  kind: 'text' | 'photo';
  mine: boolean;
  sentAt: number;
  text?: string;
  photo?: { height: number; uri: string; width: number };
  post?: PostRef;
  pending?: boolean;
};
export type ChatRoom = {
  counterpart: ChatCounterpart;
  id: string;
  name: string;
  unreadCount: number;
  messages: Message[];
  lastMessageDttm: number;
};

type ChatState = {
  drafts: Record<string, string>;
  error: string | null;
  loaded: boolean;
  loading: boolean;
  rooms: ChatRoom[];
  ensureRoom: (id: string) => Promise<number | null>;
  leave: (id: string) => Promise<void>;
  load: (force?: boolean, unreadOnly?: boolean) => Promise<void>;
  loadMoreRooms: (unreadOnly?: boolean) => Promise<void>;
  loadMoreMessages: (id: string) => Promise<void>;
  markRead: (id: string) => Promise<void>;
  openPostRoom: (
    type: PostType,
    postId: string,
    name: string,
    targetSeq: string | number,
  ) => Promise<{ roomId: string; preview: ChatPreview }>;
  openShelterRoom: (targetSeq: string | number) => Promise<{ roomId: string }>;
  send: (id: string, photo?: ChatPhoto, preview?: ChatPreview) => Promise<void>;
  setDraft: (id: string, text: string) => void;
};

const subscriptions = new Map<string, () => void>();
const messageCursors = new Map<string, number | undefined>();
let roomCursor: number | undefined;
const toMessage = (message: ChatMessageView): Message => ({
  id: String(message.chatMessageSeq),
  kind: message.messageType === 'IMAGE' ? 'photo' : 'text',
  mine: message.senderUserSeq === useProfileStore.getState().profile.userSeq,
  sentAt: message.regDttm ? new Date(message.regDttm).getTime() : Date.now(),
  text: message.body ?? undefined,
  photo: message.imageUrl ? { height: 1, uri: message.imageUrl, width: 1 } : undefined,
  post:
    message.sharedTargetType && message.sharedTargetSeq
      ? {
          id: String(message.sharedTargetSeq),
          type:
            message.sharedTargetType === 'MISSING'
              ? 'lost'
              : message.sharedTargetType === 'FOUND'
              ? 'found'
              : 'adoption',
        }
      : undefined,
});

export const useChatStore = create<ChatState>((set, get) => {
  const subscribe = (roomId: string) => {
    if (subscriptions.has(roomId)) {
      return;
    }
    subscriptions.set(
      roomId,
      chatSocket.subscribe(Number(roomId), incoming => {
        const message = toMessage(incoming);
        set(state => ({
          rooms: state.rooms
            .map(room =>
              room.id === roomId
                ? {
                    ...room,
                    lastMessageDttm: message.sentAt,
                    messages: [
                      ...room.messages.filter(item =>
                        incoming.clientMessageId
                          ? item.id !== incoming.clientMessageId
                          : item.id !== message.id,
                      ),
                      message,
                    ].sort((a, b) => a.sentAt - b.sentAt),
                    unreadCount: message.mine ? room.unreadCount : room.unreadCount + 1,
                  }
                : room,
            )
            .sort((a, b) => b.lastMessageDttm - a.lastMessageDttm),
        }));
      }),
    );
  };
  return {
    drafts: {},
    error: null,
    loaded: false,
    loading: false,
    rooms: [],
    load: async (force = false, unreadOnly = false) => {
      if (get().loading || (get().loaded && !force)) {
        return;
      }
      set({ error: null, loading: true });
      try {
        const page = await getChatRooms(unreadOnly);
        const rooms: ChatRoom[] = page.content.map(item => ({
          counterpart: item.counterpart,
          id: String(item.chatRoomSeq),
          lastMessageDttm: item.lastMessageDttm ? new Date(item.lastMessageDttm).getTime() : 0,
          messages: [
            {
              id: String(item.lastMessage.chatMessageSeq),
              kind: item.lastMessage.messageType === 'IMAGE' ? 'photo' : 'text',
              mine: false,
              sentAt: item.lastMessageDttm ? new Date(item.lastMessageDttm).getTime() : 0,
              text:
                item.lastMessage.body ??
                (item.lastMessage.messageType === 'IMAGE'
                  ? '사진을 보냈어요'
                  : '게시글을 공유했어요'),
            },
          ],
          name: item.counterpart.displayName,
          unreadCount: item.unreadCount,
        }));
        roomCursor = page.nextBeforeLastMessageSeq ?? undefined;
        set(state => {
          const mergedRooms = rooms.map(serverRoom => {
            const currentRoom = state.rooms.find(room => room.id === serverRoom.id);
            if (!currentRoom || currentRoom.lastMessageDttm < serverRoom.lastMessageDttm) {
              return serverRoom;
            }
            return {
              ...serverRoom,
              lastMessageDttm: currentRoom.lastMessageDttm,
              messages: currentRoom.messages,
            };
          });
          const locallyOpenedRooms = state.rooms.filter(
            room => !rooms.some(serverRoom => serverRoom.id === room.id),
          );
          return {
            loaded: true,
            rooms: [...mergedRooms, ...locallyOpenedRooms].sort(
              (a, b) => b.lastMessageDttm - a.lastMessageDttm,
            ),
          };
        });
      } catch (error) {
        set({ error: error instanceof Error ? error.message : '대화 내역을 불러오지 못했어요.' });
        throw error;
      } finally {
        set({ loading: false });
      }
    },
    loadMoreRooms: async (unreadOnly = false) => {
      if (!roomCursor || get().loading) {
        return;
      }
      set({ loading: true });
      try {
        const page = await getChatRooms(unreadOnly, roomCursor);
        roomCursor = page.nextBeforeLastMessageSeq ?? undefined;
        const nextRooms: ChatRoom[] = page.content.map(item => ({
          counterpart: item.counterpart,
          id: String(item.chatRoomSeq),
          lastMessageDttm: item.lastMessageDttm ? new Date(item.lastMessageDttm).getTime() : 0,
          messages: [
            {
              id: String(item.lastMessage.chatMessageSeq),
              kind: item.lastMessage.messageType === 'IMAGE' ? 'photo' : 'text',
              mine: false,
              sentAt: item.lastMessageDttm ? new Date(item.lastMessageDttm).getTime() : 0,
              text:
                item.lastMessage.body ??
                (item.lastMessage.messageType === 'IMAGE'
                  ? '사진을 보냈어요'
                  : '게시글을 공유했어요'),
            },
          ],
          name: item.counterpart.displayName,
          unreadCount: item.unreadCount,
        }));
        set(state => ({
          rooms: [
            ...state.rooms,
            ...nextRooms.filter(next => !state.rooms.some(room => room.id === next.id)),
          ],
        }));
      } finally {
        set({ loading: false });
      }
    },
    ensureRoom: async id => {
      const roomSeq = Number(id);
      await useProfileStore.getState().loadProfile();
      const [detail, page] = await Promise.all([getChatRoom(roomSeq), getChatMessages(roomSeq)]);
      messageCursors.set(id, page.nextBeforeMessageSeq ?? undefined);
      const serverMessages = page.content.map(toMessage);
      set(state => {
        const currentRoom = state.rooms.find(item => item.id === id);
        const confirmedMessages = new Map(
          (currentRoom?.messages ?? [])
            .filter(message => !message.pending)
            .map(message => [message.id, message]),
        );
        serverMessages.forEach(message => confirmedMessages.set(message.id, message));
        const unmatchedPending = (currentRoom?.messages ?? []).filter(
          pendingMessage =>
            pendingMessage.pending &&
            !serverMessages.some(
              serverMessage =>
                serverMessage.mine &&
                serverMessage.kind === pendingMessage.kind &&
                serverMessage.text === pendingMessage.text &&
                Math.abs(serverMessage.sentAt - pendingMessage.sentAt) < 120_000,
            ),
        );
        const messages = [...confirmedMessages.values(), ...unmatchedPending].sort(
          (a, b) => a.sentAt - b.sentAt,
        );
        const room: ChatRoom = {
          counterpart: detail.counterpart,
          id,
          lastMessageDttm: messages.at(-1)?.sentAt ?? currentRoom?.lastMessageDttm ?? 0,
          messages,
          name: detail.counterpart.displayName,
          unreadCount: detail.unreadCount,
        };
        return { rooms: [room, ...state.rooms.filter(item => item.id !== id)] };
      });
      subscribe(id);
      return detail.counterpartLastReadMessageSeq;
    },
    loadMoreMessages: async id => {
      const cursor = messageCursors.get(id);
      if (!cursor) {
        return;
      }
      const page = await getChatMessages(Number(id), cursor);
      messageCursors.set(id, page.nextBeforeMessageSeq ?? undefined);
      set(state => ({
        rooms: state.rooms.map(room =>
          room.id === id
            ? {
                ...room,
                messages: [...page.content.map(toMessage), ...room.messages].sort(
                  (a, b) => a.sentAt - b.sentAt,
                ),
              }
            : room,
        ),
      }));
    },
    openPostRoom: async (type, postId, name, targetSeqValue) => {
      const targetSeq = Number(targetSeqValue);
      if (!Number.isFinite(targetSeq) || targetSeq <= 0) {
        throw new Error('대화 상대 정보가 올바르지 않습니다.');
      }
      const targetType: ChatTargetType = type === 'adoption' ? 'SHELTER' : 'USER';
      const created = await createChatRoom(targetType, targetSeq);
      return { roomId: String(created.chatRoomSeq), preview: { name, post: { type, id: postId } } };
    },
    openShelterRoom: async targetSeqValue => {
      const targetSeq = Number(targetSeqValue);
      if (!Number.isFinite(targetSeq) || targetSeq <= 0) {
        throw new Error('보호소 정보가 올바르지 않습니다.');
      }
      const created = await createChatRoom('SHELTER', targetSeq);
      return { roomId: String(created.chatRoomSeq) };
    },
    setDraft: (id, text) => set(state => ({ drafts: { ...state.drafts, [id]: text } })),
    markRead: async id => {
      const room = get().rooms.find(item => item.id === id);
      const latest = room?.messages.at(-1);
      if (!latest || latest.pending || !room?.unreadCount) {
        return;
      }
      await markChatRoomRead(Number(id), Number(latest.id));
      set(state => ({
        rooms: state.rooms.map(item => (item.id === id ? { ...item, unreadCount: 0 } : item)),
      }));
    },
    send: async (id, photo, preview) => {
      const body = get().drafts[id]?.trim();
      if (!photo && !body) {
        return;
      }
      const clientMessageId = `mobile-${Date.now()}-${Math.random().toString(36).slice(2)}`;
      let imageSeq: number | undefined;
      if (photo) {
        const issued = await uploadImageToPendingStorage(photo);
        imageSeq = (await confirmImage(issued.imageKey)).imageSeq;
      }
      const optimistic: Message = {
        id: clientMessageId,
        kind: photo ? 'photo' : 'text',
        mine: true,
        pending: true,
        post: preview?.post,
        sentAt: Date.now(),
        text: body,
        photo: photo ? { height: photo.height, uri: photo.uri, width: photo.width } : undefined,
      };
      set(state => ({
        drafts: photo ? state.drafts : { ...state.drafts, [id]: '' },
        rooms: state.rooms
          .map(room =>
            room.id === id
              ? {
                  ...room,
                  lastMessageDttm: optimistic.sentAt,
                  messages: [...room.messages, optimistic],
                }
              : room,
          )
          .sort((a, b) => b.lastMessageDttm - a.lastMessageDttm),
      }));
      subscribe(id);
      try {
        await chatSocket.send(Number(id), {
          body: body || undefined,
          clientMessageId,
          imageSeq,
          messageType: photo ? 'IMAGE' : preview?.post ? 'SHARED_POST' : 'TEXT',
          sharedTargetSeq: preview?.post ? Number(preview.post.id) : undefined,
          sharedTargetType: preview?.post
            ? preview.post.type === 'lost'
              ? 'MISSING'
              : preview.post.type === 'found'
              ? 'FOUND'
              : 'SHELTER_ANIMAL'
            : undefined,
        });
      } catch (error) {
        const verification = await getChatMessages(Number(id)).catch(() => null);
        const currentUserSeq = useProfileStore.getState().profile.userSeq;
        const confirmed = verification?.content.find(message => {
          if (message.senderUserSeq !== currentUserSeq) {
            return false;
          }
          if (message.regDttm && new Date(message.regDttm).getTime() < optimistic.sentAt - 10_000) {
            return false;
          }
          if (photo) {
            return message.messageType === 'IMAGE' && message.imageSeq === imageSeq;
          }
          if (preview?.post) {
            const targetType =
              preview.post.type === 'lost'
                ? 'MISSING'
                : preview.post.type === 'found'
                ? 'FOUND'
                : 'SHELTER_ANIMAL';
            return (
              message.messageType === 'SHARED_POST' &&
              message.body === body &&
              message.sharedTargetType === targetType &&
              message.sharedTargetSeq === Number(preview.post.id)
            );
          }
          return message.messageType === 'TEXT' && message.body === body;
        });
        if (confirmed) {
          const confirmedMessage = toMessage(confirmed);
          set(state => ({
            rooms: state.rooms.map(room =>
              room.id === id
                ? {
                    ...room,
                    lastMessageDttm: confirmedMessage.sentAt,
                    messages: [
                      ...room.messages.filter(message => message.id !== clientMessageId),
                      confirmedMessage,
                    ].sort((a, b) => a.sentAt - b.sentAt),
                  }
                : room,
            ),
          }));
          return;
        }
        set(state => ({
          drafts: photo ? state.drafts : { ...state.drafts, [id]: body ?? '' },
          rooms: state.rooms.map(room =>
            room.id === id
              ? {
                  ...room,
                  messages: room.messages.filter(message => message.id !== clientMessageId),
                }
              : room,
          ),
        }));
        throw error;
      }
    },
    leave: async id => {
      await leaveChatRoom(Number(id));
      subscriptions.get(id)?.();
      subscriptions.delete(id);
      set(state => {
        const drafts = { ...state.drafts };
        delete drafts[id];
        return { drafts, rooms: state.rooms.filter(room => room.id !== id) };
      });
    },
  };
});
