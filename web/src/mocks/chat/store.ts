import { create } from 'zustand';
import { sampleConversations } from './fixtures';
import type { ChatMessage, Conversation } from '@/entities/chat/model';

export type DemoMessage = ChatMessage;

type DemoChatState = {
  conversations: Conversation[];
  messages: Record<string, DemoMessage[]>;
  sidebarOpen: boolean;
  selectedId?: string;
  drafts: Record<string, string>;
  setDraft: (id: string, text: string) => void;
  setSidebarOpen: (open: boolean) => void;
  showList: () => void;
  open: (id: string) => void;
  send: (id: string, message: DemoMessage) => void;
  markReadByRecipient: (chatId: string, messageIds: string[]) => void;
  leave: (id: string) => void;
};

export const useDemoChat = create<DemoChatState>((set) => ({
  conversations: sampleConversations,
  messages: Object.fromEntries(
    sampleConversations.map((chat) => [
      chat.id,
      [
        ...(chat.id === 'sample-chat-1'
          ? [
              {
                id: `${chat.id}-post`,
                type: 'post' as const,
                targetId: 'sample-01',
                targetType: 'SHELTER_ANIMAL' as const,
              },
            ]
          : []),
        {
          id: `${chat.id}-hello`,
          type: 'message' as const,
          sender: 'other' as const,
          text: chat.preview,
          time: chat.timeLabel,
        },
      ],
    ]),
  ),
  sidebarOpen: false,
  selectedId: undefined,
  drafts: {},
  setDraft: (id, text) => set((state) => ({ drafts: { ...state.drafts, [id]: text } })),
  setSidebarOpen: (sidebarOpen) => set({ sidebarOpen }),
  showList: () => set({ selectedId: undefined }),
  open: (id) =>
    set((state) => ({
      sidebarOpen: true,
      selectedId: id,
      conversations: state.conversations.map((chat) =>
        chat.id === id ? { ...chat, unreadCount: 0 } : chat,
      ),
    })),
  send: (id, message) =>
    set((state) => ({
      messages: { ...state.messages, [id]: [...(state.messages[id] ?? []), message] },
      conversations: state.conversations.map((chat) =>
        chat.id === id && message.type === 'message'
          ? { ...chat, preview: message.text ?? '사진', timeLabel: '방금 전' }
          : chat,
      ),
    })),
  markReadByRecipient: (chatId, messageIds) =>
    set((state) => ({
      messages: {
        ...state.messages,
        [chatId]: (state.messages[chatId] ?? []).map((message) =>
          message.type === 'message' && message.sender === 'me' && messageIds.includes(message.id)
            ? { ...message, readByRecipient: true }
            : message,
        ),
      },
    })),
  leave: (id) =>
    set((state) => ({
      selectedId: state.selectedId === id ? undefined : state.selectedId,
      drafts: Object.fromEntries(Object.entries(state.drafts).filter(([key]) => key !== id)),
      conversations: state.conversations.filter((chat) => chat.id !== id),
      messages: Object.fromEntries(Object.entries(state.messages).filter(([key]) => key !== id)),
    })),
}));
