import axios, { AxiosError } from 'axios';

import { createApiClient } from '../apiClient';

describe('createApiClient', () => {
  it('applies the base URL and default timeout', () => {
    const client = createApiClient({ baseURL: 'https://api.example.test' });

    expect(client.defaults.baseURL).toBe('https://api.example.test');
    expect(client.defaults.timeout).toBe(10_000);
  });

  it('applies a custom timeout', () => {
    const client = createApiClient({
      baseURL: 'https://api.example.test',
      timeout: 3_000,
    });

    expect(client.defaults.timeout).toBe(3_000);
  });

  it('shares one rotating refresh request across concurrent expired requests', async () => {
    let currentAccessToken = 'expired-access';
    const onTokensRefreshed = jest.fn(tokens => {
      currentAccessToken = tokens.accessToken;
    });
    const client = createApiClient({
      baseURL: 'https://api.example.test',
      getAccessToken: () => currentAccessToken,
      refresh: {
        endpoint: '/auth/refresh',
        getRefreshToken: () => 'refresh-token',
        onAuthenticationFailure: jest.fn(),
        onTokensRefreshed,
      },
    });
    const refreshPost = jest.spyOn(axios, 'post').mockResolvedValueOnce({
      data: {
        code: 'SUCCESS',
        data: { accessToken: 'new-access', refreshToken: 'new-refresh' },
        errors: [],
        message: 'ok',
        status: 200,
        traceId: 'trace',
      },
    });

    client.defaults.adapter = async config => {
      if (config.headers.get('Authorization') === 'Bearer expired-access') {
        throw new AxiosError('expired', 'ERR_BAD_REQUEST', config, undefined, {
          config,
          data: { code: 'AUTH_TOKEN_EXPIRED', errors: [], message: 'expired' },
          headers: {},
          status: 401,
          statusText: 'Unauthorized',
        });
      }
      return { config, data: {}, headers: {}, status: 200, statusText: 'OK' };
    };

    await Promise.all([client.get('/first'), client.get('/second')]);

    expect(refreshPost).toHaveBeenCalledTimes(1);
    expect(onTokensRefreshed).toHaveBeenCalledWith({
      accessToken: 'new-access',
      refreshToken: 'new-refresh',
    });
    refreshPost.mockRestore();
  });
});
