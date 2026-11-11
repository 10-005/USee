import type { RemoteMessage } from '@react-native-firebase/messaging';

import { showPushBanner } from './pushBanner';
import { parsePushMessage, type ParsedPushMessage } from './pushMessage';
import { isViewingChatRoom } from './visibleChatRoom';

export type PushMessageListener = (message: ParsedPushMessage) => void;

const listeners = new Set<PushMessageListener>();
let pending: ParsedPushMessage[] = [];

export function subscribePushMessages(listener: PushMessageListener): () => void {
  listeners.add(listener);
  const queued = pending;
  pending = [];
  for (const message of queued) {
    deliver(listener, message);
  }
  return () => {
    listeners.delete(listener);
  };
}

export async function handleRemoteMessage(
  message: RemoteMessage,
  { displayNotification = true }: { displayNotification?: boolean } = {},
): Promise<void> {
  const parsed = parsePushMessage(message);
  if (!parsed) {
    return;
  }
  const isVisibleChat =
    parsed.kind === 'server' &&
    parsed.notificationType === 'CHAT' &&
    isViewingChatRoom(parsed.roomSeq);
  if (displayNotification && !isVisibleChat) {
    await showPushBanner(parsed.title, parsed.body).catch(() => {
      console.warn('[FCM] 알림 배너를 표시하지 못했습니다.');
    });
  }
  if (listeners.size === 0) {
    pending.push(parsed);
    return;
  }
  for (const listener of [...listeners]) {
    deliver(listener, parsed);
  }
}

function deliver(listener: PushMessageListener, message: ParsedPushMessage) {
  try {
    listener(message);
  } catch {
    console.warn('[FCM] 알림 처리에 실패했습니다.');
  }
}
