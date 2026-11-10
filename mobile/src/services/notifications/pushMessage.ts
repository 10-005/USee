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
] as const;

export type ServerNotificationType = (typeof SERVER_NOTIFICATION_TYPES)[number];

const serverNotificationTypes = new Set<string>(SERVER_NOTIFICATION_TYPES);

export type SimulationPushMessage = {
  body: string;
  kind: 'simulation';
  title: string;
};

export type ServerPushMessage = {
  body: string;
  commentSeq?: string;
  kind: 'server';
  notificationSeq: string;
  notificationType: ServerNotificationType;
  targetSeq?: string;
  targetType?: string;
  title: string;
};

export type ParsedPushMessage = ServerPushMessage | SimulationPushMessage;

export function isServerNotificationType(value: string): value is ServerNotificationType {
  return serverNotificationTypes.has(value);
}

export function parsePushMessage(message: RemoteMessage): ParsedPushMessage | null {
  const title = readDisplayText(message, 'title');
  const body = readDisplayText(message, 'body');
  if (!title || !body) {
    return null;
  }

  if (readData(message, 'target') === SIMULATION_TARGET) {
    return { body, kind: 'simulation', title };
  }

  const notificationType = readData(message, 'notificationType');
  const notificationSeq = readData(message, 'notificationSeq');
  if (!notificationType || !notificationSeq || !isServerNotificationType(notificationType)) {
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
