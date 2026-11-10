import { infiniteQueryOptions, queryOptions } from '@tanstack/react-query';
import { shelterRequest } from '@/shared/api/session';
import type { adoptionStatusLabels } from '@/entities/shelter/data/applications';

export type Room = {
  chatRoomSeq: number;
  counterpart: {
    userSeq: number | null;
    displayName: string;
    profileImage: { imageUrl: string | null } | null;
  };
  lastMessage: { messageType: string; body: string | null } | null;
  lastMessageDttm: string | null;
  unreadCount: number;
};
type CounterpartProfile = {
  nickname: string;
  realName: string | null;
  profileImage: Room['counterpart']['profileImage'];
  applications: {
    applicationSeq: number;
    speciesName: string;
    breedName: string;
    status: keyof typeof adoptionStatusLabels;
    submittedDttm: string | null;
  }[];
};
export function counterpartOptions(roomId: number) {
  return queryOptions({
    queryKey: ['chat', 'counterpart', roomId],
    queryFn: ({ signal }) =>
      chatRequest<CounterpartProfile>('/rooms/' + roomId + '/counterpart', { signal }),
  });
}
export type Message = {
  chatRoomSeq: number;
  chatMessageSeq: number;
  clientMessageId: string | null;
  senderUserSeq: number;
  messageType: 'TEXT' | 'IMAGE' | 'SHARED_POST';
  body: string | null;
  imageUrl: string | null;
  sharedTargetType: string | null;
  sharedTargetSeq: number | null;
  sharedPostUnavailable: boolean;
  regDttm: string | null;
};
export async function chatRequest<T>(
  path: string,
  options: Parameters<typeof shelterRequest>[1] = {},
) {
  const data = await shelterRequest<T>('/chats' + path, options, 'chat');
  if (data == null) throw new Error('채팅 응답을 확인할 수 없습니다.');
  return data;
}
export const unreadOptions = queryOptions({
  queryKey: ['chat', 'unread'],
  queryFn: ({ signal }) =>
    chatRequest<{ totalUnreadMessageCount: number }>('/unread-count', { signal }),
});
export function roomsOptions(unreadOnly: boolean) {
  return infiniteQueryOptions({
    queryKey: ['chat', 'rooms', unreadOnly],
    initialPageParam: 0,
    queryFn: ({ pageParam, signal }) =>
      chatRequest<{ content: Room[]; nextBeforeLastMessageSeq: number | null; hasNext: boolean }>(
        '/rooms?unreadOnly=' + unreadOnly + (pageParam ? '&beforeLastMessageSeq=' + pageParam : ''),
        { signal },
      ),
    getNextPageParam: (page) => (page.hasNext ? page.nextBeforeLastMessageSeq : undefined),
  });
}
export function historyOptions(id: number) {
  return infiniteQueryOptions({
    queryKey: ['chat', 'history', id],
    initialPageParam: 0,
    queryFn: ({ pageParam, signal }) =>
      chatRequest<{ content: Message[]; nextBeforeMessageSeq: number | null; hasNext: boolean }>(
        '/rooms/' + id + '/messages' + (pageParam ? '?beforeMessageSeq=' + pageParam : ''),
        { signal },
      ),
    getNextPageParam: (page) => (page.hasNext ? page.nextBeforeMessageSeq : undefined),
  });
}
export function timeLabel(value: string | null) {
  return value
    ? new Date(value).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })
    : '';
}
