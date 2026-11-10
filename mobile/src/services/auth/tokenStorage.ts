import * as Keychain from 'react-native-keychain';

import type { AuthTokens } from '../api';

const AUTH_SERVICE = 'com.usee.animal.auth';

export async function getStoredTokens(): Promise<AuthTokens | null> {
  const credentials = await Keychain.getGenericPassword({ service: AUTH_SERVICE });
  if (!credentials) {
    return null;
  }

  try {
    return JSON.parse(credentials.password) as AuthTokens;
  } catch {
    await clearStoredTokens();
    return null;
  }
}

export async function setStoredTokens(tokens: AuthTokens): Promise<void> {
  await Keychain.setGenericPassword('usee-auth', JSON.stringify(tokens), {
    accessible: Keychain.ACCESSIBLE.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
    service: AUTH_SERVICE,
  });
}

export async function clearStoredTokens(): Promise<void> {
  await Keychain.resetGenericPassword({ service: AUTH_SERVICE });
}
