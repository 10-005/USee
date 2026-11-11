import type { ParsedPushMessage, ServerPushMessage } from './pushMessage';

export const MISSING_TARGET_TYPE = 'MISSING';

export type NotificationDestination =
  | {
      applicationSeq: number;
      name: 'AdoptionApplicationHistory';
    }
  | {
      highlightCommentId?: string;
      name: 'FoundReportDetail' | 'LostReportDetail';
      reportId: string;
    }
  | { roomId: string; name: 'ChatRoom' }
  | { name: 'Simulation' };

const commentTypes = new Set<ServerPushMessage['notificationType']>(['COMMENT', 'COMMENT_REPLY']);
const adoptionTypes = new Set<ServerPushMessage['notificationType']>([
  'ADOPTION_SUBMITTED',
  'ADOPTION_APPROVED',
  'ADOPTION_REJECTED',
  'ADOPTION_COMPLETED',
]);

export function resolveNotificationDestination(
  message: ParsedPushMessage,
): NotificationDestination | null {
  if (message.kind === 'simulation') {
    return { name: 'Simulation' };
  }

  if (message.notificationType === 'CHAT') {
    return { roomId: message.roomSeq, name: 'ChatRoom' };
  }

  if (!message.targetSeq) {
    return null;
  }

  if (message.notificationType === 'SHELTER') {
    return { name: 'FoundReportDetail', reportId: message.targetSeq };
  }

  if (commentTypes.has(message.notificationType)) {
    return {
      name: message.targetType === MISSING_TARGET_TYPE ? 'LostReportDetail' : 'FoundReportDetail',
      reportId: message.targetSeq,
      ...(message.commentSeq ? { highlightCommentId: message.commentSeq } : {}),
    };
  }

  if (adoptionTypes.has(message.notificationType)) {
    const applicationSeq = Number(message.targetSeq);
    return Number.isSafeInteger(applicationSeq) && applicationSeq > 0
      ? { applicationSeq, name: 'AdoptionApplicationHistory' }
      : null;
  }

  return null;
}

export function appearsInNotificationList(message: ParsedPushMessage): boolean {
  return message.kind === 'server' && message.notificationType !== 'CHAT';
}
