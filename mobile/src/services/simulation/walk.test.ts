import { ApiClientError } from '../api/errors';
import { endSimulationWalk, getSimulationWalkEndErrorMessage } from './walk';

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

test('ends the walk and reads the summary', async () => {
  const post = jest.fn().mockResolvedValue(envelope(endedWalk));

  await expect(endSimulationWalk(3001, 8001, { post })).resolves.toEqual(endedWalk);
  expect(post).toHaveBeenCalledWith('/simulations/3001/walks/8001/end');
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
])('describes %s for walk end', (code, message) => {
  expect(getSimulationWalkEndErrorMessage(new ApiClientError({ code, message: 'server' }))).toBe(
    message,
  );
});

test('uses a generic message when the walk cannot be ended', () => {
  expect(getSimulationWalkEndErrorMessage(new Error('offline'))).toBe(
    '산책을 종료하지 못했어요. 다시 시도해주세요.',
  );
});
