import { dismissReadNotification } from './pushBanner';
import type { ApiResponse } from '../api';
import { notificationClient } from '../api/client';
import { isListedNotification } from './notificationListItem';

export const NOTIFICATION_LIST_TYPES = [
  'COMMENT',
  'COMMENT_REPLY',
  'AI_MATCH',
  'ADOPTION_SUBMITTED',
  'ADOPTION_APPROVED',
  'ADOPTION_REJECTED',
  'ADOPTION_COMPLETED',
  'SHELTER',
  'SYSTEM',
] as const;

export type NotificationListType = (typeof NOTIFICATION_LIST_TYPES)[number];

export type NotificationTargetType =
  | 'ADOPTION'
  | 'FOUND'
  | 'FOUND_COMMENT'
  | 'MISSING'
  | 'MISSING_COMMENT'
  | 'SHELTER_ANIMAL'
  | 'USER';

export type NotificationItem = {
  commentSeq: number | null;
  isRead: boolean;
  message: string;
  missingReportSeq: number | null;
  notificationSeq: number;
  notificationType: NotificationListType;
  parentCommentSeq: number | null;
  readDttm: string | null;
  regDttm: string | null;
  targetSeq: number | null;
  targetType: NotificationTargetType | null;
  title: string;
};

export type NotificationPage = {
  content: NotificationItem[];
  hasNext: boolean;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type NotificationListQuery = {
  isRead?: boolean;
  notificationType?: NotificationListType;
  page?: number;
  size?: number;
  sort?: string;
};

export type NotificationReadResult = {
  isRead: boolean;
  notificationSeq: number;
  readDttm: string | null;
};

export async function markNotificationAsRead(
  notificationSeq: number,
): Promise<NotificationReadResult> {
  const response = await notificationClient.patch<ApiResponse<NotificationReadResult>>(
    `/notifications/${notificationSeq}/read`,
  );
  if (response.data.data.isRead) dismissReadNotification(notificationSeq);
  return response.data.data;
}

export async function deleteNotification(notificationSeq: number): Promise<void> {
  await notificationClient.delete(`/notifications/${notificationSeq}`);
}

export async function deleteAllNotifications(): Promise<void> {
  await notificationClient.delete('/notifications');
}

export async function hasUnreadListNotification(): Promise<boolean> {
  const page = await getMyNotifications({ isRead: false, size: 20 });
  return page.content.some(item => isListedNotification(item));
}

export async function getMyNotifications(
  query: NotificationListQuery = {},
): Promise<NotificationPage> {
  const response = await notificationClient.get<ApiResponse<NotificationPage>>('/notifications', {
    params: {
      page: query.page ?? 0,
      size: query.size ?? 20,
      sort: query.sort ?? 'regDttm,desc',
      ...(query.isRead === undefined ? {} : { isRead: query.isRead }),
      ...(query.notificationType ? { notificationType: query.notificationType } : {}),
    },
  });
  return response.data.data;
}
