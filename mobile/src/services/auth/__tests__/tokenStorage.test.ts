import * as Keychain from 'react-native-keychain';

import { clearStoredTokens, getStoredTokens, setStoredTokens } from '../tokenStorage';

const tokens = { accessToken: 'access', refreshToken: 'refresh' };

describe('tokenStorage', () => {
  beforeEach(() => jest.clearAllMocks());

  it('stores both service tokens in the OS secure storage', async () => {
    await setStoredTokens(tokens);

    expect(Keychain.setGenericPassword).toHaveBeenCalledWith(
      'usee-auth',
      JSON.stringify(tokens),
      expect.objectContaining({ service: 'com.usee.animal.auth' }),
    );
  });

  it('restores stored tokens', async () => {
    jest.mocked(Keychain.getGenericPassword).mockResolvedValueOnce({
      password: JSON.stringify(tokens),
      service: 'com.usee.animal.auth',
      storage: 'keychain',
      username: 'usee-auth',
    });

    await expect(getStoredTokens()).resolves.toEqual(tokens);
  });

  it('clears only the authentication credential', async () => {
    await clearStoredTokens();
    expect(Keychain.resetGenericPassword).toHaveBeenCalledWith({
      service: 'com.usee.animal.auth',
    });
  });
});
