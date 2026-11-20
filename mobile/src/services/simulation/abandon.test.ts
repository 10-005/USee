import { ApiClientError } from '../api/errors';
import { abandonSimulation, getSimulationAbandonErrorMessage } from './abandon';

const abandoned = {
  sessionSeq: 3001,
  status: 'ABANDONED',
  endedDttm: '2026-11-09T18:00:00',
  report: { summary: null, sessionSeq: 3001 },
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

test('abandons the session and keeps the report', async () => {
  const post = jest.fn().mockResolvedValue(envelope(abandoned));

  await expect(abandonSimulation(3001, { post })).resolves.toEqual(abandoned);
  expect(post).toHaveBeenCalledWith('/simulations/3001/abandon');
});

test('rejects a success body that is not abandoned', async () => {
  const post = jest.fn().mockResolvedValue(
    envelope({
      sessionSeq: 3001,
      status: 'IN_PROGRESS',
      endedDttm: '2026-11-09T18:00:00',
      report: { summary: null },
    }),
  );

  await expect(abandonSimulation(3001, { post })).rejects.toMatchObject({
    code: 'UNKNOWN_ERROR',
  });
});

test('describes SIMULATION_NOT_IN_PROGRESS for abandon', () => {
  expect(
    getSimulationAbandonErrorMessage(
      new ApiClientError({ code: 'SIMULATION_NOT_IN_PROGRESS', message: 'server' }),
    ),
  ).toBe('진행 중인 모의입양이 없어요.');
});

test('uses a generic message when the session cannot be abandoned', () => {
  expect(getSimulationAbandonErrorMessage(new Error('offline'))).toBe(
    '모의입양을 그만두지 못했어요. 다시 시도해주세요.',
  );
});
