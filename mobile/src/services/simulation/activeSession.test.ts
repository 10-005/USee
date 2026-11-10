import AsyncStorage from '@react-native-async-storage/async-storage';

import { ApiClientError } from '../api/errors';
import { getActiveMockAdoption, startMockAdoption } from '../storage/mockAdoptionStorage';
import {
  applyActiveSimulation,
  clearActiveSimulationRefresh,
  getActiveSimulation,
  invalidateActiveSimulation,
  isActiveSimulationStale,
  refreshActiveSimulationAfterPurchase,
  type ActiveSimulation,
} from './activeSession';
import { invalidateSimulationReports } from './reports';

jest.mock('./reports', () => ({
  invalidateSimulationReports: jest.fn(),
}));

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

const equipment = {
  foodBowl: true,
  waterBowl: true,
  litterBox: false,
  toy: false,
  leash: false,
};

function session(patch: Partial<ActiveSimulation> = {}): ActiveSimulation {
  return {
    sessionSeq: 3001,
    userSeq: 17,
    animalName: '초코',
    breed: {
      breedSeq: '000018',
      breedName: '비숑 프리제',
      speciesType: 'DOG',
      representativeImageUrl: null,
    },
    speciesType: 'DOG',
    personality: 'FRIENDLY',
    status: 'IN_PROGRESS',
    expectedCost: 150000,
    balance: 118000,
    startedDttm: '2026-11-01T14:00:00+09:00',
    endsDttm: '2026-11-08T14:00:00+09:00',
    endedDttm: null,
    stats,
    lastActionDttm: '2026-11-01T16:40:00+09:00',
    equipment,
    pendingEvent: null,
    activeWalkSeq: null,
    ...patch,
  };
}

function envelope(data: unknown) {
  return {
    data: {
      status: 200,
      code: 'SUCCESS',
      message: '요청이 성공했습니다.',
      data,
      errors: [],
      traceId: '4f2c9e1a7b3d4c5e',
    },
  };
}

describe('active simulation', () => {
  beforeEach(async () => {
    jest.spyOn(Date, 'now').mockReturnValue(1_800_000_000_000);
    await AsyncStorage.clear();
    await applyActiveSimulation(null);
    clearActiveSimulationRefresh();
    jest.mocked(invalidateSimulationReports).mockClear();
  });

  afterEach(async () => {
    await applyActiveSimulation(null);
    jest.restoreAllMocks();
  });

  test('reads the in-progress session and an empty result', async () => {
    const get = jest
      .fn()
      .mockResolvedValueOnce(
        envelope({
          ...session(),
          breed: {
            breedSeq: 18,
            breedName: '비숑 프리제',
            speciesType: 'DOG',
            representativeImageUrl: null,
          },
        }),
      )
      .mockResolvedValueOnce(envelope(null));

    await expect(getActiveSimulation({ get })).resolves.toMatchObject({
      sessionSeq: 3001,
      breed: { breedSeq: '18' },
      activeWalkSeq: null,
      pendingEvent: null,
    });
    expect(get).toHaveBeenCalledWith('/simulations/me/active');
    await expect(getActiveSimulation({ get })).resolves.toBeNull();
  });

  test('rejects a session whose stats are missing', async () => {
    const get = jest.fn().mockResolvedValue(envelope({ ...session(), stats: null }));
    await expect(getActiveSimulation({ get })).rejects.toBeInstanceOf(ApiClientError);
  });

  test('clears the device session when the server has no active adoption', async () => {
    await startMockAdoption('dog-1', 35000, '초코', 3001, 8001);
    await expect(applyActiveSimulation(null)).resolves.toBeNull();
    await expect(getActiveMockAdoption()).resolves.toBeNull();
    expect(invalidateSimulationReports).toHaveBeenCalledTimes(1);
  });

  test('clears the device session when the server session has ended', async () => {
    await startMockAdoption('dog-1', 35000, '초코', 3001);
    await expect(
      applyActiveSimulation(
        session({ status: 'COMPLETED', endedDttm: '2026-11-08T14:00:00+09:00' }),
      ),
    ).resolves.toBeNull();
    await expect(getActiveMockAdoption()).resolves.toBeNull();
    expect(invalidateSimulationReports).toHaveBeenCalledTimes(1);
  });

  test('stores the server walk on the matching device session', async () => {
    await startMockAdoption('dog-1', 35000, '초코', 3001);
    await expect(
      applyActiveSimulation(
        session({ breed: { ...session().breed, breedSeq: '21' }, activeWalkSeq: 8001 }),
      ),
    ).resolves.toMatchObject({
      petId: 'dog-1',
      sessionSeq: 3001,
      walkSeq: 8001,
      weeklyBudget: 35000,
    });
    await expect(
      applyActiveSimulation(session({ activeWalkSeq: null })),
    ).resolves.not.toHaveProperty('walkSeq');
  });

  test('refetches the active session once after a purchase', async () => {
    await startMockAdoption('dog-1', 35000, '초코', 3001);
    await applyActiveSimulation(session({ equipment: { ...equipment, foodBowl: false } }));
    invalidateActiveSimulation();
    const get = jest
      .fn()
      .mockResolvedValue(
        envelope(session({ equipment: { ...equipment, foodBowl: true, toy: true } })),
      );

    await expect(refreshActiveSimulationAfterPurchase({ get })).resolves.toMatchObject({
      equipment: { foodBowl: true, toy: true },
    });
    expect(get).toHaveBeenCalledTimes(1);
    expect(get).toHaveBeenCalledWith('/simulations/me/active');
    await expect(refreshActiveSimulationAfterPurchase({ get })).resolves.toMatchObject({
      equipment: { foodBowl: true, toy: true },
    });
    expect(get).toHaveBeenCalledTimes(1);
  });

  test('keeps the active session stale when refreshing fails', async () => {
    invalidateActiveSimulation();
    const get = jest.fn().mockRejectedValue(new Error('offline'));
    await expect(refreshActiveSimulationAfterPurchase({ get })).rejects.toThrow('offline');
    expect(isActiveSimulationStale()).toBe(true);
  });

  test('starts from the server session when the device has none', async () => {
    await expect(applyActiveSimulation(session({ activeWalkSeq: 8001 }))).resolves.toMatchObject({
      petId: 'dog-1',
      petName: '초코',
      sessionSeq: 3001,
      walkSeq: 8001,
      weeklyBudget: 150000,
    });
  });
});
