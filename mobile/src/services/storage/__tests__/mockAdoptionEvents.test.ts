import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  receiveMockAdoptionEvent,
  takeMockAdoptionEvent,
  hasPendingTreatment,
  completeMockAdoptionTreatment,
} from '../mockAdoptionEvents';
import type { MockAdoptionSession } from '../mockAdoptionStorage';

const session: MockAdoptionSession = {
  version: 1,
  petId: 'dog-5',
  startedAt: 100,
  expiresAt: 200,
  weeklyBudget: 100000,
};
const event = {
  id: 'event-1',
  petId: session.petId,
  sessionStartedAt: session.startedAt,
  title: '{petName}가 아픕니다!',
  body: '{petName}를 동물병원에 데려가주세요!',
};

beforeEach(async () => {
  await AsyncStorage.clear();
});

it('질병 이벤트는 팝업 확인 후에도 치료 완료까지 유지된다', async () => {
  await receiveMockAdoptionEvent({ ...event, kind: 'illness' });
  expect(await hasPendingTreatment(session)).toBe(true);
  await takeMockAdoptionEvent(session);
  expect(await hasPendingTreatment(session)).toBe(true);
  await completeMockAdoptionTreatment(session);
  expect(await hasPendingTreatment(session)).toBe(false);
  await receiveMockAdoptionEvent({ ...event, kind: 'illness' });
  expect(await hasPendingTreatment(session)).toBe(false);
});

it('다른 이벤트와 다른 회차의 치료 상태는 구분한다', async () => {
  await receiveMockAdoptionEvent({ ...event, kind: 'other' });
  expect(await hasPendingTreatment(session)).toBe(false);
  await receiveMockAdoptionEvent({ ...event, id: 'illness-1', kind: 'illness' });
  await completeMockAdoptionTreatment({ ...session, startedAt: 999 });
  expect(await hasPendingTreatment(session)).toBe(true);
  expect(await hasPendingTreatment({ ...session, petId: 'cat-1' })).toBe(false);
});

it('치료 완료 저장 실패 시 질병 상태를 유지한다', async () => {
  await receiveMockAdoptionEvent({ ...event, kind: 'illness' });
  jest.spyOn(AsyncStorage, 'setItem').mockRejectedValueOnce(new Error('storage failed'));
  await expect(completeMockAdoptionTreatment(session)).rejects.toThrow('storage failed');
  expect(await hasPendingTreatment(session)).toBe(true);
  jest.restoreAllMocks();
});

it('중복 수신과 재진입에도 같은 이벤트를 한 번만 반환한다', async () => {
  await Promise.all([receiveMockAdoptionEvent(event), receiveMockAdoptionEvent(event)]);
  expect(await takeMockAdoptionEvent(session)).toEqual(event);
  await receiveMockAdoptionEvent(event);
  expect(await takeMockAdoptionEvent(session)).toBeNull();
});

it('다른 동물이나 다른 모의입양 회차에는 표시하지 않는다', async () => {
  await receiveMockAdoptionEvent(event);
  expect(await takeMockAdoptionEvent({ ...session, petId: 'cat-1' })).toBeNull();
  expect(await takeMockAdoptionEvent({ ...session, startedAt: 300 })).toBeNull();
  expect(await takeMockAdoptionEvent(session)).toEqual(event);
});

it('화면을 떠난 경우 대기 이벤트를 소비하지 않는다', async () => {
  await receiveMockAdoptionEvent(event);
  expect(await takeMockAdoptionEvent(session, () => false)).toBeNull();
  expect(await takeMockAdoptionEvent(session)).toEqual(event);
});

it('동시에 진입해도 한 번만 소비한다', async () => {
  await receiveMockAdoptionEvent(event);
  const results = await Promise.all([
    takeMockAdoptionEvent(session),
    takeMockAdoptionEvent(session),
  ]);
  expect(results).toEqual([event, null]);
});

it('저장에 실패하면 다음 진입에 다시 시도한다', async () => {
  await receiveMockAdoptionEvent(event);
  jest.spyOn(AsyncStorage, 'setItem').mockRejectedValueOnce(new Error('storage failed'));
  await expect(takeMockAdoptionEvent(session)).rejects.toThrow('storage failed');
  expect(await takeMockAdoptionEvent(session)).toEqual(event);
  jest.restoreAllMocks();
});
