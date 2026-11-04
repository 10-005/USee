import { create } from 'zustand';
import { STORAGE_KEYS } from '../constants/storageKeys';
import { getStoredValue, setStoredValue } from '../services/storage/jsonStorage';
import { removeChatPhoto } from '../services/storage/chatPhotoStorage';

export type ChatPhoto = { filename: string; width: number; height: number };
type Content = { kind: 'text'; text: string } | { kind: 'photo'; photo: ChatPhoto };
type Message = Content & { id: string; mine: boolean; sentAt: number };
type Room = {
  id: string;
  name: string;
  unreadCount: number;
  messages: Message[];
  animalId?: string;
};

const now = Date.now();
const initialRooms: Room[] = [
  {
    id: 'sample-chat-1',
    name: '시샤',
    unreadCount: 1,
    animalId: 'animal-1',
    messages: [
      {
        id: '1',
        kind: 'text',
        text: '아이 몇 개월인지 알 수 있을까요?',
        mine: false,
        sentAt: now - 3600000,
      },
    ],
  },
  {
    id: 'sample-chat-2',
    name: '민지',
    unreadCount: 0,
    messages: [
      { id: '1', kind: 'text', text: '확인 감사합니다.', mine: false, sentAt: now - 7200000 },
    ],
  },
];

type ChatState = {
  rooms: Room[];
  drafts: Record<string, string>;
  loaded: boolean;
  load: () => Promise<void>;
  markRead: (id: string) => Promise<void>;
  setDraft: (id: string, text: string) => void;
  send: (id: string, photo?: ChatPhoto) => Promise<void>;
  leave: (id: string) => Promise<void>;
};

// Serialize changes so a slow write cannot replace a newer conversation.
let pending = Promise.resolve();
function enqueue(task: () => Promise<void>) {
  const next = pending.then(task);
  pending = next.catch(() => {});
  return next;
}

export const useChatStore = create<ChatState>((set, get) => {
  const load = async () => {
    if (get().loaded) {
      return;
    }
    const stored = await getStoredValue<Room[]>(STORAGE_KEYS.CHAT_ROOMS);
    set({ rooms: stored ?? initialRooms, loaded: true });
  };
  const commit = async (rooms: Room[]) => {
    await setStoredValue(STORAGE_KEYS.CHAT_ROOMS, rooms);
    set({ rooms });
  };
  return {
    rooms: [],
    drafts: {},
    loaded: false,
    load: () => enqueue(load),
    setDraft: (id, text) => set(state => ({ drafts: { ...state.drafts, [id]: text } })),
    markRead: id =>
      enqueue(async () => {
        await load();
        const { rooms } = get();
        if (rooms.find(room => room.id === id)?.unreadCount) {
          await commit(rooms.map(room => (room.id === id ? { ...room, unreadCount: 0 } : room)));
        }
      }),
    send: (id, photo) => {
      const draft = get().drafts[id] ?? '';
      if (!photo && !draft.trim()) {
        return Promise.resolve();
      }
      if (!photo) {
        get().setDraft(id, '');
      }
      return enqueue(async () => {
        await load();
        const { rooms } = get();
        const room = rooms.find(item => item.id === id);
        if (!room) {
          throw new Error('채팅방을 찾을 수 없어요.');
        }
        const content: Content = photo
          ? { kind: 'photo', photo }
          : { kind: 'text', text: draft.trim() };
        const message: Message = {
          ...content,
          id: String(room.messages.length + 1),
          mine: true,
          sentAt: Date.now(),
        };
        await commit([
          { ...room, messages: [...room.messages, message] },
          ...rooms.filter(item => item.id !== id),
        ]);
      }).catch(error => {
        if (!photo && !get().drafts[id] && get().rooms.some(room => room.id === id)) {
          get().setDraft(id, draft);
        }
        throw error;
      });
    },
    leave: id =>
      enqueue(async () => {
        await load();
        const { rooms } = get();
        const room = rooms.find(item => item.id === id);
        if (!room) {
          return;
        }
        await commit(rooms.filter(item => item.id !== id));
        set(state => {
          const drafts = { ...state.drafts };
          delete drafts[id];
          return { drafts };
        });
        await Promise.allSettled(
          room.messages.flatMap(message =>
            message.kind === 'photo' ? [removeChatPhoto(message.photo.filename)] : [],
          ),
        );
      }),
  };
});
