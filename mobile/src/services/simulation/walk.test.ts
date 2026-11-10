import { ApiClientError } from '../api/errors';
import { SimulationWalkAction } from './actions';
import {
  endSimulationWalk,
  getSimulationWalkActionErrorMessage,
  getSimulationWalkEndErrorMessage,
  getSimulationWalkStartErrorMessage,
  isWalkPoopEvent,
  performSimulationWalkAction,
  sendSimulationWalkHeartbeat,
  startSimulationWalk,
} from './walk';

const endedWalk = {
  walkSeq: 8001,
  status: 'ENDED',
  startedDttm: '2026-11-09T10:00:00',
  endedDttm: '2026-11-09T10:35:00',
  durationMinutes: 35,
  statDelta: { energy: 25, calmness: 12, bond: 4, satiety: -6, hydration: -8 },
  stats: { energy: 80 },
  eventCount: 2,
};

const envelope = (data: unknown) => ({
  data: {
    status: 200,
    code: 'SUCCESS',
    message: '요청이 성공했습니다.',
    data,
    errors: [],
    traceId: '4f2c9e1a7b3d4c5e',
  },
});

const startedWalk = {
  walkSeq: 8001,
  status: 'IN_PROGRESS',
  startedDttm: '2026-11-09T10:00:00',
  heartbeatIntervalSeconds: 60,
  stats: { energy: 80 },
};

test('starts a walk for the active simulation', async () => {
  const post = jest.fn().mockResolvedValue(envelope(startedWalk));

  await expect(startSimulationWalk(3001, { post })).resolves.toEqual(startedWalk);
  expect(post).toHaveBeenCalledWith('/simulations/3001/walks');
});

test('rejects a walk start without the walk number', async () => {
  const post = jest.fn().mockResolvedValue(envelope({ ...startedWalk, walkSeq: 0 }));

  await expect(startSimulationWalk(3001, { post })).rejects.toMatchObject({
    code: 'UNKNOWN_ERROR',
    message: '산책을 시작하지 못했어요. 다시 시도해주세요.',
  });
});

test.each([
  ['SIMULATION_WALK_ALREADY_IN_PROGRESS', '이미 진행 중인 산책이 있어요.'],
  ['SIMULATION_WALK_NOT_FOR_SPECIES', '이 동물은 산책할 수 없어요.'],
])('describes %s for walk start', (code, message) => {
  expect(getSimulationWalkStartErrorMessage(new ApiClientError({ code, message: 'server' }))).toBe(
    message,
  );
});

test('ends the walk and reads the summary', async () => {
  const post = jest.fn().mockResolvedValue(envelope(endedWalk));

  await expect(endSimulationWalk(3001, 8001, { post })).resolves.toEqual(endedWalk);
  expect(post).toHaveBeenCalledWith('/simulations/3001/walks/8001/end');
});

test('ends a short walk when the effect delta is empty', async () => {
  const shortWalk = { ...endedWalk, durationMinutes: 3, statDelta: {} };
  const post = jest.fn().mockResolvedValue(envelope(shortWalk));

  await expect(endSimulationWalk(3001, 8001, { post })).resolves.toEqual(shortWalk);
});

test('rejects a success body that is not an ended walk', async () => {
  const post = jest.fn().mockResolvedValue(envelope({ walkSeq: 8001, status: 'IN_PROGRESS' }));

  await expect(endSimulationWalk(3001, 8001, { post })).rejects.toMatchObject({
    code: 'UNKNOWN_ERROR',
  });
});

test.each([
  ['SIMULATION_WALK_ALREADY_IN_PROGRESS', '이미 진행 중인 산책이 있어요.'],
  ['SIMULATION_WALK_NOT_IN_PROGRESS', '진행 중인 산책이 없어요.'],
  ['SIMULATION_ITEM_NOT_IN_INVENTORY', '필요한 아이템이 없어요.'],
  ['SIMULATION_NOT_IN_PROGRESS', '진행 중인 모의입양이 없어요.'],
  ['SIMULATION_WALK_NOT_FOUND', '산책을 찾지 못했어요.'],
  ['SIMULATION_POOP_NOT_FOUND', '치울 배변이 없어요.'],
])('describes %s for walk end', (code, message) => {
  expect(getSimulationWalkEndErrorMessage(new ApiClientError({ code, message: 'server' }))).toBe(
    message,
  );
});

const heartbeatStats = {
  satiety: 64,
  hydration: 58,
  cleanliness: 77,
  bond: 31,
  calmness: 46,
  health: 90,
  energy: 55,
  calculatedDttm: '2026-11-01T17:00:00+09:00',
};

const poopEvent = {
  occurrenceSeq: 90010,
  scenario: {
    scenarioSeq: 16,
    title: '길에서 똥을 쌌어요',
    description: '산책하다 길 한가운데서 볼일을 봤어요.',
    triggerContext: 'WALK',
    triggerType: 'RANDOM',
  },
  vetVisit: null,
  walkSeq: 8001,
  status: 'PENDING',
  occurredDttm: '2026-11-01T17:12:00+09:00',
  expiresDttm: '2026-11-01T17:17:00+09:00',
  resolvedDttm: null,
  resolvedActionCode: null,
  resultText: null,
};

test('reads a heartbeat and treats a poop title as the cleanup event', async () => {
  const post = jest.fn().mockResolvedValue(
    envelope({
      walkSeq: 8001,
      elapsedMinutes: 12,
      stats: heartbeatStats,
      newEvent: poopEvent,
      pendingEvent: null,
    }),
  );

  const beat = await sendSimulationWalkHeartbeat(3001, 8001, { post });
  expect(post).toHaveBeenCalledWith('/simulations/3001/walks/8001/heartbeat');
  expect(beat.newEvent?.occurrenceSeq).toBe(90010);
  expect(beat.newEvent && isWalkPoopEvent(beat.newEvent)).toBe(true);
  expect(
    isWalkPoopEvent({
      ...beat.newEvent!,
      scenario: {
        ...beat.newEvent!.scenario,
        title: '목이 말라요',
        description: '물을 찾고 있어요.',
      },
    }),
  ).toBe(false);
});

const walkAction = (actionCode: string, extra: Record<string, unknown> = {}) => ({
  actionLogSeq: 50010,
  actionType: 'WALK',
  actionCode,
  walkSeq: 8001,
  consumedItem: null,
  statDelta: { hydration: 20 },
  stats: heartbeatStats,
  balance: 118000,
  resolvedEvent: null,
  ...extra,
});

test('gives water without an item and a snack with its item', async () => {
  const post = jest
    .fn()
    .mockResolvedValueOnce(envelope(walkAction('GIVE_WATER')))
    .mockResolvedValueOnce(
      envelope(
        walkAction('GIVE_SNACK', {
          consumedItem: { itemSeq: 41, itemName: '간식', remainingQuantity: 4 },
          statDelta: {},
        }),
      ),
    );

  await performSimulationWalkAction(
    3001,
    8001,
    { actionCode: SimulationWalkAction.GiveWater },
    { post },
  );
  await performSimulationWalkAction(
    3001,
    8001,
    { actionCode: SimulationWalkAction.GiveSnack, itemSeq: 41 },
    { post },
  );
  expect(post).toHaveBeenNthCalledWith(1, '/simulations/3001/walks/8001/actions', {
    actionCode: 'GIVE_WATER',
  });
  expect(post).toHaveBeenNthCalledWith(2, '/simulations/3001/walks/8001/actions', {
    actionCode: 'GIVE_SNACK',
    itemSeq: 41,
  });
});

test('cleans up poop with the walk action and no item', async () => {
  const post = jest.fn().mockResolvedValue(envelope(walkAction('CLEAN_UP_POOP')));
  await performSimulationWalkAction(
    3001,
    8001,
    { actionCode: SimulationWalkAction.CleanUpPoop },
    { post },
  );
  expect(post).toHaveBeenCalledWith('/simulations/3001/walks/8001/actions', {
    actionCode: 'CLEAN_UP_POOP',
  });
});

test('describes a missing walk item', () => {
  expect(
    getSimulationWalkActionErrorMessage(
      new ApiClientError({ code: 'SIMULATION_ITEM_NOT_IN_INVENTORY', message: 'server' }),
    ),
  ).toBe('필요한 아이템이 없어요.');
});

test('uses a generic message when the walk cannot be ended', () => {
  expect(getSimulationWalkEndErrorMessage(new Error('offline'))).toBe(
    '산책을 종료하지 못했어요. 다시 시도해주세요.',
  );
});
