import AsyncStorage from '@react-native-async-storage/async-storage';

import type { StorageKey } from '../../constants/storageKeys';

export async function getStoredValue<T>(key: StorageKey): Promise<T | null> {
  const value = await AsyncStorage.getItem(key);

  if (value === null) {
    return null;
  }

  return JSON.parse(value) as T;
}

export async function setStoredValue<T>(key: StorageKey, value: T): Promise<void> {
  await AsyncStorage.setItem(key, JSON.stringify(value));
}

export async function removeStoredValue(key: StorageKey): Promise<void> {
  await AsyncStorage.removeItem(key);
}
