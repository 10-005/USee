import type { NotificationCategory } from '../../stores/notificationStore';
import { isServerNotificationType, type ServerNotificationType } from './pushMessage';
import type { NotificationItem } from './notificationApi';

export type NotificationListEntry = {
  body: string;
  category: NotificationCategory;
  createdAtLabel: string;
  id: string;
  isRead: boolean;
  notificationType: ServerNotificationType;
  targetCommentId?: string;
  targetId?: string;
  targetType?: string;
  title: string;
};

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

export function isListedNotification(item: { notificationType: string }): boolean {
  return item.notificationType !== 'CHAT';
}

export function toNotificationListEntry(
  item: NotificationItem,
  now = new Date(),
): NotificationListEntry | null {
  if (!isListedNotification(item) || !isServerNotificationType(item.notificationType)) {
    return null;
  }

  return {
    body: item.message,
    category: categoryFor(item.notificationType),
    createdAtLabel: formatNotificationTime(item.regDttm, now),
    id: String(item.notificationSeq),
    isRead: item.isRead,
    notificationType: item.notificationType,
    title: item.title,
    ...(item.commentSeq === null ? {} : { targetCommentId: String(item.commentSeq) }),
    ...(item.targetSeq === null ? {} : { targetId: String(item.targetSeq) }),
    ...(item.targetType ? { targetType: item.targetType } : {}),
  };
}

export function formatNotificationTime(regDttm: string | null, now = new Date()): string {
  if (!regDttm) {
    return '';
  }
  const date = new Date(regDttm);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  const diff = now.getTime() - date.getTime();
  if (diff >= 0 && diff < MINUTE) {
    return '방금';
  }
  if (diff >= 0 && diff < HOUR) {
    return `${Math.floor(diff / MINUTE)}분 전`;
  }
  if (diff >= 0 && diff < DAY) {
    return `${Math.floor(diff / HOUR)}시간 전`;
  }
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}.${month}.${day}`;
}

function categoryFor(type: ServerNotificationType): NotificationCategory {
  switch (type) {
    case 'COMMENT':
    case 'COMMENT_REPLY':
      return 'reply';
    case 'AI_MATCH':
      return 'aiMatching';
    case 'ADOPTION_SUBMITTED':
    case 'ADOPTION_APPROVED':
    case 'ADOPTION_REJECTED':
    case 'ADOPTION_COMPLETED':
      return 'adoption';
    case 'CHAT':
    case 'SHELTER':
    case 'SYSTEM':
      return 'report';
  }
}
