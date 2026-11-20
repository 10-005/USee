import type { ChatMessage, Conversation } from '../model';

/** 전송 방식과 서버 메시지 스키마는 API 확정 후 어댑터에서 결정합니다. */
export interface ChatRepository {
  listConversations(options?: { signal?: AbortSignal }): Promise<Conversation[]>;
  listMessages(conversationId: string, options?: { signal?: AbortSignal }): Promise<ChatMessage[]>;
  sendMessage(conversationId: string, message: ChatMessage): Promise<void>;
  leaveConversation(conversationId: string): Promise<void>;
}
