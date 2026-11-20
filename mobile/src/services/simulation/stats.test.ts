import { ApiClientError } from '../api/errors';
import { getSimulationStats, getSimulationStatsErrorMessage } from './stats';

const stats = {
  satiety: 64,
  hydration: 58,
  cleanliness: 77,
  bond: 31,
  calmness: 46,
  health: 90,
  energy: 55,
  calculatedDttm: '2026-11-01T17:00:00+09:00',
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

test('reads the seven stats for the requested session', async () => {
  const get = jest.fn().mockResolvedValue(
    envelope({
      sessionSeq: 3001,
      status: 'IN_PROGRESS',
      stats,
    }),
  );

  await expect(getSimulationStats(3001, { get })).resolves.toEqual({
    sessionSeq: 3001,
    status: 'IN_PROGRESS',
    stats,
  });
  expect(get).toHaveBeenCalledWith('/simulations/3001/stats');
});

test('keeps an ended session status so the screen can leave for the report', async () => {
  const get = jest.fn().mockResolvedValue(
    envelope({
      sessionSeq: 3001,
      status: 'FAILED',
      stats: { ...stats, satiety: 0, hydration: 0, health: 0, energy: 5 },
    }),
  );

  await expect(getSimulationStats(3001, { get })).resolves.toMatchObject({ status: 'FAILED' });
});

test('rejects a stats body that does not match the session', async () => {
  const get = jest
    .fn()
    .mockResolvedValue(envelope({ sessionSeq: 3002, status: 'IN_PROGRESS', stats }));

  await expect(getSimulationStats(3001, { get })).rejects.toMatchObject({
    message: '상태를 불러오지 못했어요. 다시 시도해주세요.',
  });
});

test('describes a missing simulation', () => {
  expect(
    getSimulationStatsErrorMessage(
      new ApiClientError({ code: 'SIMULATION_NOT_FOUND', message: 'server' }),
    ),
  ).toBe('진행 중인 모의입양을 찾지 못했어요.');
});
