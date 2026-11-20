import type { RemoteMessage } from '@react-native-firebase/messaging';

export const SIMULATION_TARGET = 'SIMULATION';

export const SERVER_NOTIFICATION_TYPES = [
  'COMMENT',
  'COMMENT_REPLY',
  'AI_MATCH',
  'ADOPTION_SUBMITTED',
  'ADOPTION_APPROVED',
  'ADOPTION_REJECTED',
  'ADOPTION_COMPLETED',
  'SHELTER',
  'SYSTEM',
  'CHAT',
] as const;

export type ServerNotificationType = (typeof SERVER_NOTIFICATION_TYPES)[number];

const serverNotificationTypes = new Set<string>(SERVER_NOTIFICATION_TYPES);

export type SimulationPushMessage = {
  body: string;
  kind: 'simulation';
  title: string;
};

export type ChatPushMessage = {
  body: string;
  kind: 'server';
  notificationType: 'CHAT';
  roomSeq: string;
  messageSeq: string;
  senderDisplayName: string;
  preview: string;
  title: string;
};

export type ServerPushMessage =
  | ChatPushMessage
  | {
      body: string;
      commentSeq?: string;
      kind: 'server';
      notificationSeq: string;
      notificationType: Exclude<ServerNotificationType, 'CHAT'>;
      targetSeq?: string;
      targetType?: string;
      title: string;
    };

export type ParsedPushMessage = ServerPushMessage | SimulationPushMessage;

export function isServerNotificationType(value: string): value is ServerNotificationType {
  return serverNotificationTypes.has(value);
}

export function parsePushMessage(message: RemoteMessage): ParsedPushMessage | null {
  const notificationType = readData(message, 'notificationType');
  const title =
    readDisplayText(message, 'title') ??
    (notificationType === 'CHAT' ? readData(message, 'senderDisplayName') : undefined);
  const body =
    readDisplayText(message, 'body') ??
    (notificationType === 'CHAT' ? readData(message, 'preview') : undefined);
  if (!title || !body) {
    return null;
  }

  if (readData(message, 'target') === SIMULATION_TARGET) {
    return { body, kind: 'simulation', title };
  }

  if (!notificationType || !isServerNotificationType(notificationType)) {
    return null;
  }

  if (notificationType === 'CHAT') {
    const roomSeq = readData(message, 'roomSeq');
    const messageSeq = readData(message, 'messageSeq');
    if (!roomSeq || !messageSeq) {
      return null;
    }
    return {
      body,
      kind: 'server',
      notificationType,
      roomSeq,
      messageSeq,
      senderDisplayName: readData(message, 'senderDisplayName') ?? title,
      preview: readData(message, 'preview') ?? body,
      title,
    };
  }

  const notificationSeq = readData(message, 'notificationSeq');
  if (!notificationSeq) {
    return null;
  }

  const targetType = readData(message, 'targetType');
  const targetSeq = readData(message, 'targetSeq');
  const commentSeq = readData(message, 'commentSeq');

  return {
    body,
    kind: 'server',
    notificationSeq,
    notificationType,
    title,
    ...(commentSeq ? { commentSeq } : {}),
    ...(targetSeq ? { targetSeq } : {}),
    ...(targetType ? { targetType } : {}),
  };
}

function readDisplayText(message: RemoteMessage, key: 'body' | 'title'): string | undefined {
  const notificationText = message.notification?.[key]?.trim();
  if (notificationText) {
    return notificationText;
  }
  return readData(message, key);
}

function readData(message: RemoteMessage, key: string): string | undefined {
  const value = message.data?.[key];
  if (typeof value !== 'string') {
    return undefined;
  }
  const text = value.trim();
  return text || undefined;
}
