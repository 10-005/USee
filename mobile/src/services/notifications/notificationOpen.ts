import type { RemoteMessage } from '@react-native-firebase/messaging';

import { parsePushMessage, type ParsedPushMessage } from './pushMessage';

let pendingOpen: ParsedPushMessage | null = null;
const listeners = new Set<() => void>();
const openedIds = new Set<string>();

export function handleNotificationOpened(message: RemoteMessage, id = message.messageId): void {
  const parsed = parsePushMessage(message);
  if (!parsed || (id && openedIds.has(id))) {
    return;
  }
  if (id) {
    openedIds.add(id);
    if (openedIds.size > 100) {
      openedIds.delete(openedIds.values().next().value!);
    }
  }
  // If several notifications are tapped before login, open the most recent one.
  pendingOpen = parsed;
  listeners.forEach(listener => listener());
}

export function handleNotificationURL(url: string | null | undefined): void {
  if (!url?.startsWith('usee://notification/')) {
    return;
  }
  const payloadIndex = url.indexOf('?payload=');
  if (payloadIndex < 0) {
    return;
  }
  try {
    const message = JSON.parse(decodeURIComponent(url.slice(payloadIndex + '?payload='.length)));
    if (message && typeof message === 'object') {
      handleNotificationOpened(message, url.slice(0, payloadIndex));
    }
  } catch {
    console.warn('[FCM] 알림 이동 정보를 읽지 못했습니다.');
  }
}

export function subscribeNotificationOpens(listener: () => void): () => void {
  listeners.add(listener);
  listener();
  return () => {
    listeners.delete(listener);
  };
}

export function takePendingNotificationOpen(): ParsedPushMessage | null {
  const message = pendingOpen;
  pendingOpen = null;
  return message;
}
