import { dismissReadChat } from '../notifications/pushBanner';
import type { ApiResponse } from '../api';
import { chatClient } from '../api/client';

export type ChatTargetType = 'USER' | 'SHELTER';
export type ChatMessageType = 'TEXT' | 'IMAGE' | 'SHARED_POST';
export type SharedTargetType = 'MISSING' | 'FOUND' | 'SHELTER_ANIMAL';
export type ChatImageRef = { imageSeq: number; imageUrl: string | null; displayOrder: number };
export type ChatCounterpart = {
  type: ChatTargetType;
  userSeq: number | null;
  shelterSeq: number | null;
  displayName: string;
  profileImage: ChatImageRef | null;
};
export type ChatMessageView = {
  chatMessageSeq: number;
  clientMessageId: string | null;
  chatRoomSeq: number;
  senderUserSeq: number;
  senderDisplayName: string;
  messageType: ChatMessageType;
  body: string | null;
  imageSeq: number | null;
  imageUrl: string | null;
  sharedTargetType: SharedTargetType | null;
  sharedTargetSeq: number | null;
  sharedPostUnavailable: boolean;
  regDttm: string | null;
};
export type ChatRoomItem = {
  chatRoomSeq: number;
  roomType: ChatTargetType;
  counterpart: ChatCounterpart;
  lastMessage: Pick<ChatMessageView, 'chatMessageSeq' | 'messageType' | 'body'>;
  lastMessageSeq: number;
  lastMessageDttm: string | null;
  unreadCount: number;
};
export type ChatRoomPage = {
  content: ChatRoomItem[];
  nextBeforeLastMessageSeq: number | null;
  hasNext: boolean;
};
export type ChatRoomDetail = {
  chatRoomSeq: number;
  roomType: ChatTargetType;
  counterpart: ChatCounterpart;
  counterpartLastReadMessageSeq: number | null;
  lastReadMessageSeq: number | null;
  lastHiddenMessageSeq: number | null;
  unreadCount: number;
};
export type ChatMessagePage = {
  content: ChatMessageView[];
  nextBeforeMessageSeq: number | null;
  hasNext: boolean;
};
export type CounterpartProfile = {
  userSeq: number;
  nickname: string;
  realName: string | null;
  profileImage: ChatImageRef | null;
  applications: Array<{
    applicationSeq: number;
    speciesName: string;
    breedName: string;
    status: 'SUBMITTED' | 'APPROVED' | 'ADOPTED' | 'REJECTED';
    submittedDttm: string | null;
  }>;
};

export async function getChatRooms(unreadOnly = false, beforeLastMessageSeq?: number) {
  const response = await chatClient.get<ApiResponse<ChatRoomPage>>('/chats/rooms', {
    params: { unreadOnly, size: 20, ...(beforeLastMessageSeq ? { beforeLastMessageSeq } : {}) },
  });
  return response.data.data;
}
export async function createChatRoom(targetType: ChatTargetType, targetSeq: number) {
  const response = await chatClient.post<
    ApiResponse<{ chatRoomSeq: number; roomType: ChatTargetType; reused: boolean }>
  >(
    '/chats/rooms',
    targetType === 'USER'
      ? { targetType, targetUserSeq: targetSeq }
      : { targetType, targetShelterSeq: targetSeq },
  );
  return response.data.data;
}
export async function getChatRoom(chatRoomSeq: number) {
  const response = await chatClient.get<ApiResponse<ChatRoomDetail>>(`/chats/rooms/${chatRoomSeq}`);
  return response.data.data;
}
export async function getChatMessages(chatRoomSeq: number, beforeMessageSeq?: number) {
  const response = await chatClient.get<ApiResponse<ChatMessagePage>>(
    `/chats/rooms/${chatRoomSeq}/messages`,
    {
      params: { size: 30, ...(beforeMessageSeq ? { beforeMessageSeq } : {}) },
    },
  );
  return response.data.data;
}
export async function markChatRoomRead(chatRoomSeq: number, lastReadMessageSeq: number) {
  const response = await chatClient.patch<
    ApiResponse<{
      lastReadMessageSeq: number | null;
      unreadCount: number;
      totalUnreadMessageCount: number;
    }>
  >(`/chats/rooms/${chatRoomSeq}/read`, { lastReadMessageSeq });
  const through = response.data.data.lastReadMessageSeq;
  if (through != null) dismissReadChat(chatRoomSeq, through);
  return response.data.data;
}
export async function leaveChatRoom(chatRoomSeq: number) {
  const response = await chatClient.post<
    ApiResponse<{ lastHiddenMessageSeq: number | null; totalUnreadMessageCount: number }>
  >(`/chats/rooms/${chatRoomSeq}/leave`);
  return response.data.data;
}
export async function getUnreadChatCount() {
  const response = await chatClient.get<ApiResponse<{ totalUnreadMessageCount: number }>>(
    '/chats/unread-count',
  );
  return response.data.data;
}
export async function getChatCounterpartProfile(chatRoomSeq: number) {
  const response = await chatClient.get<ApiResponse<CounterpartProfile>>(
    `/chats/rooms/${chatRoomSeq}/counterpart`,
  );
  return response.data.data;
}
