import { create } from 'zustand';
import { STORAGE_KEYS } from '../constants/storageKeys';
import { getStoredValue, setStoredValue } from '../services/storage/jsonStorage';
import { removeChatPhoto } from '../services/storage/chatPhotoStorage';

export type ChatPhoto = { filename: string; width: number; height: number };
type Content = { kind: 'text'; text: string } | { kind: 'photo'; photo: ChatPhoto };
export type PostType = 'adoption' | 'found' | 'lost';
export type PostRef = { type: PostType; id: string };
export function samePost(a?: PostRef, b?: PostRef) {
  return a?.type === b?.type && a?.id === b?.id;
}
type Message = Content & { id: string; mine: boolean; sentAt: number; post?: PostRef };
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

export type ChatPreview = { name: string; post: PostRef };

// Convert rooms saved by earlier versions into a single timeline per fixture publisher.
function migrateRooms(source: Room[]) {
  const rooms = new Map<string, Room>();
  for (const room of source) {
    if (!room.messages.length) {continue;}
    const match = /^post:(adoption|found|lost):(.+)$/.exec(room.id);
    const sample = room.id === 'sample-chat-1';
    let id = room.id;
    let post: PostRef | undefined;
    if (match) {
      post = { type: match[1] as PostType, id: match[2] };
      const author =
        post.type === 'adoption' ? 'iksan-shelter' : room.name === '내 닉네임' ? 'self' : 'sisha';
      id = `publisher:${author}`;
    } else if (sample) {
      id = 'publisher:sisha';
      if (room.animalId) {post = { type: 'adoption', id: room.animalId };}
    }
    const previous = rooms.get(id);
    if (!match && !sample && !previous) {
      rooms.set(id, room);
      continue;
    }
    const messages = [
      ...(previous?.messages ?? []),
      ...room.messages.map(message => ({
        ...message,
        post: message.post ?? post,
      })),
    ]
      .sort((a, b) => a.sentAt - b.sentAt)
      .map((message, index) => ({ ...message, id: String(index + 1) }));
    rooms.set(id, {
      id,
      name: room.name,
      unreadCount: (previous?.unreadCount ?? 0) + room.unreadCount,
      messages,
    });
  }
  return Array.from(rooms.values());
}
type ChatState = {
  rooms: Room[];
  drafts: Record<string, string>;
  loaded: boolean;
  load: () => Promise<void>;
  openPostRoom: (
    type: PostType,
    postId: string,
    name: string,
    publisherId: string,
  ) => Promise<{ roomId: string; preview: ChatPreview }>;
  markRead: (id: string) => Promise<void>;
  setDraft: (id: string, text: string) => void;
  send: (id: string, photo?: ChatPhoto, preview?: ChatPreview) => Promise<void>;
  leave: (id: string) => Promise<void>;
};

// Serialize changes so a slow write cannot replace a newer conversation.
let pending = Promise.resolve();
function enqueue<T>(task: () => Promise<T>) {
  const next = pending.then(task);
  pending = next.then(
    () => {},
    () => {},
  );
  return next;
}

export const useChatStore = create<ChatState>((set, get) => {
  const load = async () => {
    if (get().loaded) {
      return;
    }
    const stored = await getStoredValue<Room[]>(STORAGE_KEYS.CHAT_ROOMS);
    const migratedRooms = migrateRooms(stored ?? initialRooms);
    // Keep one unread fixture visible in development so the unread UI can be reviewed.
    const rooms =
      __DEV__ && migratedRooms.length > 0 && migratedRooms.every(room => room.unreadCount === 0)
        ? migratedRooms.map((room, index) => (index === 0 ? { ...room, unreadCount: 1 } : room))
        : migratedRooms;
    if (
      stored &&
      (rooms.length !== stored.length || rooms.some((room, index) => room !== stored[index]))
    ) {
      await setStoredValue(STORAGE_KEYS.CHAT_ROOMS, rooms);
    }
    set({ rooms, loaded: true });
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
    openPostRoom: (type, postId, name, publisherId) =>
      enqueue(async () => {
        await load();
        return {
          roomId: `publisher:${publisherId}`,
          preview: { name, post: { type, id: postId } },
        };
      }),
    setDraft: (id, text) => set(state => ({ drafts: { ...state.drafts, [id]: text } })),
    markRead: id =>
      enqueue(async () => {
        await load();
        const { rooms } = get();
        if (rooms.find(room => room.id === id)?.unreadCount) {
          await commit(rooms.map(room => (room.id === id ? { ...room, unreadCount: 0 } : room)));
        }
      }),
    send: (id, photo, preview) => {
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
        const room: Room | undefined =
          rooms.find(item => item.id === id) ??
          (preview && { id, name: preview.name, unreadCount: 0, messages: [] });
        if (!room) {
          throw new Error('채팅방을 찾을 수 없어요.');
        }
        const content: Content = photo
          ? { kind: 'photo', photo }
          : { kind: 'text', text: draft.trim() };
        const message: Message = {
          ...content,
          post: preview?.post ?? room.messages[room.messages.length - 1]?.post,
          id: String(room.messages.length + 1),
          mine: true,
          sentAt: Date.now(),
        };
        await commit([
          { ...room, messages: [...room.messages, message] },
          ...rooms.filter(item => item.id !== id),
        ]);
      }).catch(error => {
        if (!photo && !get().drafts[id] && (preview || get().rooms.some(room => room.id === id))) {
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
