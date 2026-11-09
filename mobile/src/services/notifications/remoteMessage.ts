import type { RemoteMessage } from '@react-native-firebase/messaging';
import { receiveMockAdoptionEvent, type MockAdoptionEvent } from '../storage/mockAdoptionEvents';

// App-side contract until the backend event payload is finalized.
export function parseMockAdoptionMessage(message: RemoteMessage): MockAdoptionEvent | null {
  const data = message.data;
  if (data?.type !== 'mock_adoption_event') {
    return null;
  }
  const id = data.eventId || message.messageId;
  const title = data.title || message.notification?.title;
  const body = data.body || message.notification?.body;
  const { petId, sessionStartedAt, kind } = data;
  if (
    typeof id !== 'string' ||
    !id.trim() ||
    typeof petId !== 'string' ||
    !petId.trim() ||
    typeof title !== 'string' ||
    !title.trim() ||
    typeof body !== 'string' ||
    !body.trim() ||
    typeof sessionStartedAt !== 'string' ||
    !sessionStartedAt.trim() ||
    !Number.isSafeInteger(Number(sessionStartedAt)) ||
    Number(sessionStartedAt) <= 0 ||
    (kind !== undefined && kind !== 'illness' && kind !== 'other')
  ) {
    return null;
  }
  return { id, petId, title, body, sessionStartedAt: Number(sessionStartedAt), kind };
}

export async function handleRemoteMessage(message: RemoteMessage) {
  const event = parseMockAdoptionMessage(message);
  if (event) {
    await receiveMockAdoptionEvent(event);
  }
}
