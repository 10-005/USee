import AsyncStorage from '@react-native-async-storage/async-storage';
import { STORAGE_KEYS } from '../../../constants/storageKeys';
import { getActiveMockAdoption, startMockAdoption } from '../mockAdoptionStorage';

describe('mock adoption weekly session', () => {
  beforeEach(async () => {
    jest.spyOn(Date, 'now').mockReturnValue(1_800_000_000_000);
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
    // Returning to setup must never reset the active week's amount or expiry.
    await expect(startMockAdoption('cat-2', 25000)).resolves.toEqual(session);
    jest.spyOn(Date, 'now').mockReturnValue(session.expiresAt);
    await expect(getActiveMockAdoption()).resolves.toBeNull();
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

  it('does not return an invalid stored session', async () => {
    await AsyncStorage.setItem(
      STORAGE_KEYS.MOCK_ADOPTION_SESSION,
      JSON.stringify({ version: 1, petId: 'dog-1', weeklyBudget: -1 }),
    );
    await expect(getActiveMockAdoption()).resolves.toBeNull();
  });
});
