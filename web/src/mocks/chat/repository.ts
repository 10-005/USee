import type { ChatRepository } from '@/entities/chat/data/contracts';
import { useDemoChat } from './store';

/** 네트워크 없는 데모 채팅 어댑터입니다. */
export const chatMockRepository: ChatRepository = {
  async listConversations(options) {
    options?.signal?.throwIfAborted();
    return useDemoChat.getState().conversations;
  },
  async listMessages(id, options) {
    options?.signal?.throwIfAborted();
    return useDemoChat.getState().messages[id] ?? [];
  },
  async sendMessage(id, message) {
    useDemoChat.getState().send(id, message);
  },
  async leaveConversation(id) {
    useDemoChat.getState().leave(id);
  },
};
