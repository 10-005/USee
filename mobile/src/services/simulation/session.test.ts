import { ApiClientError } from '../api/errors';
import { mockAdoptionPets } from '../../screens/MockAdoption/data';
import { getSimulationStartErrorMessage, startSimulationSession } from './session';

const startedSession = {
  sessionSeq: 3001,
  animalName: '초코',
  expectedCost: 150000,
  balance: 150000,
  status: 'IN_PROGRESS',
};

test('keeps the catalog breed code for every selectable pet', () => {
  expect(mockAdoptionPets.map(pet => [pet.id, pet.name, pet.breedCode])).toEqual([
    ['dog-1', '비숑 프리제', '000018'],
    ['dog-2', '도베르만', '000041'],
    ['dog-3', '푸들', '000128'],
    ['dog-4', '골든 리트리버', '000054'],
    ['dog-5', '시바', '000100'],
    ['dog-6', '시츄', '000101'],
    ['cat-1', '브리티시 쇼트헤어', '000181'],
    ['cat-2', '러시안 블루', '000172'],
    ['cat-3', '사바나캣', '000182'],
    ['cat-4', '페르시안', '00214'],
    ['cat-5', '스핑크스', '000189'],
    ['cat-6', '샴', '000184'],
  ]);
});

test('starts a simulation with the numeric server breed id and reads the session id', async () => {
  const post = jest.fn().mockResolvedValue({
    data: {
      status: 201,
      code: 'SUCCESS',
      message: '요청이 성공했습니다.',
      data: startedSession,
      errors: [],
      traceId: '4f2c9e1a7b3d4c5e',
    },
  });

  await expect(
    startSimulationSession({ breedSeq: 501, animalName: '초코', expectedCost: 150000 }, { post }),
  ).resolves.toEqual({ sessionSeq: 3001 });
  expect(post).toHaveBeenCalledWith('/simulations', {
    breedSeq: 501,
    animalName: '초코',
    expectedCost: 150000,
  });
});

test('rejects a success body that does not include a session id', async () => {
  const post = jest.fn().mockResolvedValue({
    data: {
      status: 201,
      code: 'SUCCESS',
      message: '요청이 성공했습니다.',
      data: { animalName: '초코' },
      errors: [],
      traceId: '4f2c9e1a7b3d4c5e',
    },
  });

  await expect(
    startSimulationSession({ breedSeq: 602, animalName: '나비', expectedCost: 20000 }, { post }),
  ).rejects.toMatchObject({ code: 'UNKNOWN_ERROR' });
});

test.each(['000100', 0, -1, 1.5, NaN])(
  'rejects an invalid server breed id %s before sending',
  async value => {
    const post = jest.fn();
    await expect(
      startSimulationSession(
        {
          breedSeq: value as number,
          animalName: '초코',
          expectedCost: 150000,
        },
        { post },
      ),
    ).rejects.toMatchObject({ code: 'COMMON_INVALID_INPUT' });
    expect(post).not.toHaveBeenCalled();
  },
);

test.each([
  ['SIMULATION_ACTIVE_ALREADY_EXISTS', '이미 진행 중인 모의입양이 있어요.'],
  ['SIMULATION_SPECIES_NOT_SUPPORTED', '아직 지원하지 않는 품종이에요.'],
  ['SIMULATION_BREED_NOT_FOUND', '선택한 품종을 찾지 못했어요.'],
  ['COMMON_INVALID_INPUT', '이름 또는 예산을 확인해주세요.'],
])('describes %s for the start screen', (code, message) => {
  expect(getSimulationStartErrorMessage(new ApiClientError({ code, message: 'server' }))).toBe(
    message,
  );
});

test('uses a generic message when the start request cannot be completed', () => {
  expect(getSimulationStartErrorMessage(new Error('offline'))).toBe(
    '모의입양을 시작하지 못했어요. 다시 시도해주세요.',
  );
});
