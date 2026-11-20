import { ApiClientError } from '../api/errors';
import { SimulationCareAction } from './actions';
import { careRequest, getCareActionErrorMessage, performCareAction } from './care';

const stats = {
  satiety: 94,
  hydration: 58,
  cleanliness: 77,
  bond: 32,
  calmness: 46,
  health: 90,
  energy: 55,
  calculatedDttm: '2026-11-01T15:10:00+09:00',
};

const fed = {
  actionLogSeq: 50001,
  actionType: 'CARE',
  actionCode: 'FEED',
  consumedItem: { itemSeq: 11, itemName: '건식 사료 1kg', remainingQuantity: 4 },
  statDelta: { satiety: 30, bond: 1 },
  stats,
  balance: 118000,
  resolvedEvent: null,
};

const treated = {
  actionLogSeq: 50002,
  actionType: 'CARE',
  actionCode: 'VET_VISIT',
  consumedItem: null,
  statDelta: { health: 30, calmness: 10 },
  stats: {
    ...stats,
    satiety: 40,
    hydration: 35,
    cleanliness: 60,
    calmness: 40,
    health: 68,
    energy: 40,
    calculatedDttm: '2026-11-02T10:12:00+09:00',
  },
  balance: 80000,
  resolvedEvent: {
    occurrenceSeq: 90001,
    scenarioTitle: '축 처져 있어요',
    resultText: '진단서: 가벼운 장염 소견. 처방식과 휴식을 권장합니다.',
    eventStatDelta: { health: 30, calmness: 10 },
    eventCost: 30000,
  },
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

test('feeds with the selected item and reads the care result', async () => {
  const post = jest.fn().mockResolvedValue(envelope(fed));

  await expect(
    performCareAction(3001, { actionCode: SimulationCareAction.Feed, itemSeq: 11 }, { post }),
  ).resolves.toEqual(fed);
  expect(post).toHaveBeenCalledWith('/simulations/3001/actions', {
    actionCode: 'FEED',
    itemSeq: 11,
  });
});

test('omits the item when water or a vet visit does not consume one', async () => {
  const post = jest
    .fn()
    .mockResolvedValue(envelope({ ...fed, actionCode: 'WATER', consumedItem: null }));

  await performCareAction(3001, { actionCode: SimulationCareAction.Water }, { post });
  expect(post).toHaveBeenCalledWith('/simulations/3001/actions', { actionCode: 'WATER' });

  post.mockResolvedValue(envelope(treated));
  await expect(
    performCareAction(3001, { actionCode: SimulationCareAction.VetVisit }, { post }),
  ).resolves.toMatchObject({ resolvedEvent: { occurrenceSeq: 90001, eventCost: 30000 } });
  expect(post).toHaveBeenLastCalledWith('/simulations/3001/actions', { actionCode: 'VET_VISIT' });
});

test('lets the server choose the toy when play omits the item', async () => {
  const post = jest
    .fn()
    .mockResolvedValue(envelope({ ...fed, actionCode: 'PLAY', consumedItem: null }));

  await performCareAction(3001, { actionCode: SimulationCareAction.Play }, { post });
  expect(post).toHaveBeenCalledWith('/simulations/3001/actions', { actionCode: 'PLAY' });
});

test('rejects a success body that is not a care action', async () => {
  const post = jest.fn().mockResolvedValue(envelope({ ...fed, actionType: 'WALK' }));

  await expect(
    performCareAction(3001, { actionCode: SimulationCareAction.Feed, itemSeq: 11 }, { post }),
  ).rejects.toMatchObject({ code: 'UNKNOWN_ERROR' });
});

test('builds a request only when the action has the item it requires', () => {
  expect(careRequest(SimulationCareAction.Feed)).toBeNull();
  expect(careRequest(SimulationCareAction.Feed, 11)).toEqual({ actionCode: 'FEED', itemSeq: 11 });
  expect(careRequest(SimulationCareAction.Water)).toEqual({ actionCode: 'WATER' });
  expect(careRequest(SimulationCareAction.Bathe)).toEqual({ actionCode: 'BATHE' });
  expect(careRequest(SimulationCareAction.Bathe, 11)).toEqual({ actionCode: 'BATHE' });
  expect(careRequest(SimulationCareAction.Play)).toEqual({ actionCode: 'PLAY' });
});

test.each([
  ['SIMULATION_NOT_FOUND', '진행 중인 모의입양을 찾지 못했어요.'],
  ['SIMULATION_EQUIPMENT_MISSING', '필요한 기구가 없어요.'],
  ['SIMULATION_ITEM_NOT_IN_INVENTORY', '필요한 아이템이 없어요.'],
  ['SIMULATION_NOT_IN_PROGRESS', '진행 중인 모의입양이 없어요.'],
  ['SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES', '이 동물에게 맞지 않는 물품이에요.'],
  ['SIMULATION_VET_NOT_NEEDED', '지금은 병원 진료가 필요하지 않아요.'],
  ['COMMON_INVALID_INPUT', '돌봄 행동을 확인해주세요.'],
  ['SIMULATION_SHOP_ITEM_NOT_FOUND', '물품을 찾지 못했어요.'],
])('describes %s for a care action', (code, message) => {
  expect(getCareActionErrorMessage(new ApiClientError({ code, message: 'server' }))).toBe(message);
});

test('uses a generic message when the care action cannot be completed', () => {
  expect(getCareActionErrorMessage(new Error('offline'))).toBe(
    '돌봄을 진행하지 못했어요. 다시 시도해주세요.',
  );
});
