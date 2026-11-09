import AsyncStorage from '@react-native-async-storage/async-storage';
import { handleRemoteMessage, parseMockAdoptionMessage } from '../remoteMessage';
import { hasPendingTreatment, takeMockAdoptionEvent } from '../../storage/mockAdoptionEvents';

const session = {
  version: 1 as const,
  petId: 'dog-5',
  startedAt: 1700000000000,
  expiresAt: 1700604800000,
  weeklyBudget: 100000,
};
const message = {
  messageId: 'message-1',
  data: {
    type: 'mock_adoption_event',
    eventId: 'illness-1',
    petId: session.petId,
    sessionStartedAt: String(session.startedAt),
    kind: 'illness',
    title: '{petName}가 아픕니다!',
    body: '동물병원에 데려가주세요.',
  },
};

beforeEach(() => AsyncStorage.clear());

it('일반 알림이나 잘못된 회차 데이터는 이벤트로 저장하지 않는다', async () => {
  expect(parseMockAdoptionMessage({ data: { type: 'chat' } })).toBeNull();
  for (const value of ['', 'NaN', '-1', '1.5']) {
    await handleRemoteMessage({ ...message, data: { ...message.data, sessionStartedAt: value } });
  }
  expect(await takeMockAdoptionEvent(session)).toBeNull();
});

it('수신과 알림 클릭이 중복되어도 이벤트는 한 번만 표시하고 치료 상태는 유지한다', async () => {
  await handleRemoteMessage(message);
  await handleRemoteMessage(message);
  expect((await takeMockAdoptionEvent(session))?.id).toBe('illness-1');
  await handleRemoteMessage(message);
  expect(await takeMockAdoptionEvent(session)).toBeNull();
  expect(await hasPendingTreatment(session)).toBe(true);
});

it('notification의 제목과 본문 및 FCM 메시지 ID를 대체값으로 사용한다', () => {
  const { eventId: _eventId, title, body, ...data } = message.data;
  expect(
    parseMockAdoptionMessage({ messageId: 'fallback', data, notification: { title, body } }),
  ).toMatchObject({ id: 'fallback', title, body });
});
