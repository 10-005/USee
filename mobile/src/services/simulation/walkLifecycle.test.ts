import AsyncStorage from '@react-native-async-storage/async-storage';
import { STORAGE_KEYS } from '../../constants/storageKeys';
import { ApiClientError } from '../api/errors';
import {
  getActiveMockAdoption,
  setMockAdoptionExpireHandler,
  startMockAdoption,
} from '../storage/mockAdoptionStorage';
import { invalidateSimulationReports } from './reports';
import { endActiveSimulationWalk, installMockAdoptionWalkExpiry } from './walkLifecycle';
import { endSimulationWalk } from './walk';

jest.mock('./reports', () => ({
  invalidateSimulationReports: jest.fn(),
}));

jest.mock('./walk', () => ({
  endSimulationWalk: jest.fn(),
  isFinishedWalkError: jest.requireActual('./walk').isFinishedWalkError,
}));

const endWalk = jest.mocked(endSimulationWalk);

describe('mock adoption walk expiry', () => {
  beforeEach(async () => {
    jest.spyOn(Date, 'now').mockReturnValue(1_800_000_000_000);
    endWalk.mockReset();
    endWalk.mockResolvedValue({
      walkSeq: 8001,
      status: 'ENDED',
      startedDttm: '2026-11-09T10:00:00',
      endedDttm: '2026-11-16T10:00:00',
      durationMinutes: 10080,
      statDelta: { energy: 25, calmness: 12, bond: 4, satiety: -6, hydration: -8 },
      stats: {},
      eventCount: 0,
    });
    installMockAdoptionWalkExpiry();
    jest.mocked(invalidateSimulationReports).mockClear();
    await AsyncStorage.clear();
  });

  afterEach(() => {
    setMockAdoptionExpireHandler(null);
    jest.restoreAllMocks();
  });

  it('ends the stored walk when seven days have passed', async () => {
    const session = await startMockAdoption('dog-1', 35000, '초코', 3001, 8001);
    jest.spyOn(Date, 'now').mockReturnValue(session.expiresAt);

    await expect(getActiveMockAdoption()).resolves.toBeNull();
    expect(endWalk).toHaveBeenCalledWith(3001, 8001);
    expect(invalidateSimulationReports).toHaveBeenCalledTimes(1);
  });

  it('clears an expired session when the walk is already gone', async () => {
    const session = await startMockAdoption('dog-1', 35000, '초코', 3001, 8001);
    endWalk.mockRejectedValue(
      new ApiClientError({ code: 'SIMULATION_WALK_NOT_FOUND', message: 'server' }),
    );
    jest.spyOn(Date, 'now').mockReturnValue(session.expiresAt);

    await expect(getActiveMockAdoption()).resolves.toBeNull();
    expect(invalidateSimulationReports).toHaveBeenCalledTimes(1);
  });

  it('ends the open walk and drops its id when the user leaves', async () => {
    await startMockAdoption('dog-1', 35000, '초코', 3001, 8001);

    await endActiveSimulationWalk();

    expect(endWalk).toHaveBeenCalledWith(3001, 8001);
    const session = await getActiveMockAdoption();
    expect(session).toMatchObject({ sessionSeq: 3001 });
    expect(session).not.toHaveProperty('walkSeq');
  });

  it('drops the walk id when the server walk is already finished', async () => {
    await startMockAdoption('dog-1', 35000, '초코', 3001, 8001);
    endWalk.mockRejectedValue(
      new ApiClientError({ code: 'SIMULATION_WALK_NOT_IN_PROGRESS', message: 'server' }),
    );

    await endActiveSimulationWalk();

    await expect(getActiveMockAdoption()).resolves.not.toHaveProperty('walkSeq');
  });

  it('keeps the walk id when ending fails', async () => {
    await startMockAdoption('dog-1', 35000, '초코', 3001, 8001);
    endWalk.mockRejectedValue(new Error('offline'));

    await expect(endActiveSimulationWalk()).rejects.toThrow('offline');
    await expect(getActiveMockAdoption()).resolves.toMatchObject({ walkSeq: 8001 });
  });

  it('keeps the expired session when the walk end can be retried', async () => {
    const session = await startMockAdoption('dog-1', 35000, '초코', 3001, 8001);
    endWalk.mockRejectedValue(new Error('offline'));
    jest.spyOn(Date, 'now').mockReturnValue(session.expiresAt);

    await expect(getActiveMockAdoption()).rejects.toThrow('offline');
    expect(invalidateSimulationReports).not.toHaveBeenCalled();
    await expect(AsyncStorage.getItem(STORAGE_KEYS.MOCK_ADOPTION_SESSION)).resolves.toContain(
      '"walkSeq":8001',
    );
  });
});
