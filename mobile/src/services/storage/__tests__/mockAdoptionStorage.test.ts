import AsyncStorage from '@react-native-async-storage/async-storage';
import { STORAGE_KEYS } from '../../../constants/storageKeys';
import { invalidateSimulationReports } from '../../simulation/reports';
import { getActiveMockAdoption, startMockAdoption } from '../mockAdoptionStorage';

jest.mock('../../simulation/reports', () => ({
  invalidateSimulationReports: jest.fn(),
}));

describe('mock adoption weekly session', () => {
  beforeEach(async () => {
    jest.spyOn(Date, 'now').mockReturnValue(1_800_000_000_000);
    jest.mocked(invalidateSimulationReports).mockClear();
    await AsyncStorage.clear();
  });
  afterEach(() => jest.restoreAllMocks());

  it('persists the pet and budget across reads until exactly seven days after starting', async () => {
    const session = await startMockAdoption('dog-1', 35000);
    expect(session.expiresAt - session.startedAt).toBe(7 * 24 * 60 * 60 * 1000);
    expect(JSON.parse((await AsyncStorage.getItem(STORAGE_KEYS.MOCK_ADOPTION_SESSION))!)).toEqual(
      session,
    );
    jest.spyOn(Date, 'now').mockReturnValue(session.expiresAt - 1);
    await expect(getActiveMockAdoption()).resolves.toEqual(session);
    expect(invalidateSimulationReports).not.toHaveBeenCalled();
    // Returning to setup must never reset the active week's amount or expiry.
    await expect(startMockAdoption('cat-2', 25000)).resolves.toEqual(session);
    jest.spyOn(Date, 'now').mockReturnValue(session.expiresAt);
    await expect(getActiveMockAdoption()).resolves.toBeNull();
    expect(invalidateSimulationReports).toHaveBeenCalledTimes(1);
    expect(await AsyncStorage.getItem(STORAGE_KEYS.MOCK_ADOPTION_SESSION)).toBeNull();
    await expect(startMockAdoption('cat-2', 25000)).resolves.toMatchObject({
      petId: 'cat-2',
      weeklyBudget: 25000,
      startedAt: session.expiresAt,
    });
  });

  it.each([9999, 100000001, NaN, 10000.5])('rejects invalid stored budget %s', async amount => {
    await expect(startMockAdoption('dog-1', amount)).rejects.toThrow();
    expect(await AsyncStorage.getItem(STORAGE_KEYS.MOCK_ADOPTION_SESSION)).toBeNull();
  });

  it('persists the server walk with the session', async () => {
    const session = await startMockAdoption('dog-1', 35000, '초코', 3001, 8001);
    expect(session.walkSeq).toBe(8001);
    await expect(getActiveMockAdoption()).resolves.toMatchObject({
      sessionSeq: 3001,
      walkSeq: 8001,
    });
  });

  it('replaces the saved week when the server creates a new session', async () => {
    await startMockAdoption('dog-1', 35000, '초코', 3001);
    const next = await startMockAdoption('cat-2', 40000, '나비', 3002);
    expect(next).toMatchObject({
      petId: 'cat-2',
      petName: '나비',
      weeklyBudget: 40000,
      sessionSeq: 3002,
    });
    await expect(getActiveMockAdoption()).resolves.toEqual(next);
  });

  it('persists a selected name across restarts without overwriting the active session', async () => {
    await startMockAdoption('dog-5', 35000, '초코');
    await expect(getActiveMockAdoption()).resolves.toMatchObject({
      petId: 'dog-5',
      petName: '초코',
    });
    await startMockAdoption('cat-1', 40000, '나비');
    await expect(getActiveMockAdoption()).resolves.toMatchObject({
      petId: 'dog-5',
      petName: '초코',
    });
  });

  it.each(['ㄱ', '초코1', '초 코', '초코야잘지내렴'])(
    'does not store an invalid name %s',
    async name => {
      await expect(startMockAdoption('dog-1', 35000, name)).rejects.toThrow('Invalid pet name');
      expect(await AsyncStorage.getItem(STORAGE_KEYS.MOCK_ADOPTION_SESSION)).toBeNull();
    },
  );

  it('does not return an invalid stored session', async () => {
    await AsyncStorage.setItem(
      STORAGE_KEYS.MOCK_ADOPTION_SESSION,
      JSON.stringify({ version: 1, petId: 'dog-1', weeklyBudget: -1 }),
    );
    await expect(getActiveMockAdoption()).resolves.toBeNull();
  });
});
