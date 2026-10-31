import AsyncStorage from '@react-native-async-storage/async-storage';

import { STORAGE_KEYS } from '../../../constants/storageKeys';
import { getStoredValue, removeStoredValue, setStoredValue } from '../jsonStorage';

describe('jsonStorage', () => {
  beforeEach(async () => {
    await AsyncStorage.clear();
  });

  it('stores and reads a JSON value', async () => {
    const value = { ready: true };

    await setStoredValue(STORAGE_KEYS.APP_STATE, value);

    await expect(getStoredValue(STORAGE_KEYS.APP_STATE)).resolves.toEqual(value);
  });

  it('removes a stored value', async () => {
    await setStoredValue(STORAGE_KEYS.APP_STATE, { ready: true });
    await removeStoredValue(STORAGE_KEYS.APP_STATE);

    await expect(getStoredValue(STORAGE_KEYS.APP_STATE)).resolves.toBeNull();
  });
});
