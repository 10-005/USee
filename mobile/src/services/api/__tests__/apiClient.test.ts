import type { InternalAxiosRequestConfig } from 'axios';
import { AxiosError } from 'axios';

import { createApiClient } from '../apiClient';
import { API_SERVICES } from '../types';
import { serviceBaseUrl } from '../client';
import { readApiData } from '../response';

const success = <T>(data: T) => ({
  status: 200,
  code: 'SUCCESS',
  message: '요청이 성공했습니다.',
  data,
  errors: [],
  traceId: '4f2c9e1a7b3d4c5e',
});

describe('createApiClient', () => {
  it('applies the base URL and default timeout', () => {
    const client = createApiClient({ baseURL: 'https://api.example.test/api/core/v1' });

    expect(client.defaults.baseURL).toBe('https://api.example.test/api/core/v1');
    expect(client.defaults.timeout).toBe(10_000);
  });

  it('applies a custom timeout', () => {
    const client = createApiClient({
      baseURL: 'https://api.example.test/api/core/v1',
      timeout: 3_000,
    });

    expect(client.defaults.timeout).toBe(3_000);
  });

  it('sends the access token and a trace id', async () => {
    const client = createApiClient({
      baseURL: 'https://api.example.test/api/simulation/v1',
      getAccessToken: async () => 'access-token',
    });
    let request: InternalAxiosRequestConfig | undefined;
    client.defaults.adapter = async config => {
      request = config;
      return { config, data: success({ ok: true }), headers: {}, status: 200, statusText: 'OK' };
    };

    await client.get('/simulations/current');

    expect(request?.headers.get('Authorization')).toBe('Bearer access-token');
    expect(request?.headers.get('X-Trace-Id')).toMatch(/^[0-9a-f]{16}$/);
  });

  it('keeps a caller supplied trace id and omits an empty token', async () => {
    const client = createApiClient({
      baseURL: 'https://api.example.test/api/simulation/v1',
      getAccessToken: () => null,
    });
    let request: InternalAxiosRequestConfig | undefined;
    client.defaults.adapter = async config => {
      request = config;
      return { config, data: success(null), headers: {}, status: 200, statusText: 'OK' };
    };

    await client.get('/simulations/current', { headers: { 'X-Trace-Id': 'given-trace' } });

    expect(request?.headers.get('Authorization')).toBeUndefined();
    expect(request?.headers.get('X-Trace-Id')).toBe('given-trace');
  });

  it('reads the common error envelope', async () => {
    const client = createApiClient({ baseURL: 'https://api.example.test/api/simulation/v1' });
    client.defaults.adapter = async config => {
      throw new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, undefined, {
        config,
        data: {
          status: 400,
          code: 'COMMON_INVALID_INPUT',
          message: '입력값이 올바르지 않습니다.',
          data: null,
          errors: [{ field: 'animalName', reason: '이름은 6자 이하여야 합니다.' }],
          traceId: '4f2c9e1a7b3d4c5e',
        },
        headers: {},
        status: 400,
        statusText: 'Bad Request',
      });
    };

    await expect(client.post('/simulations', {})).rejects.toMatchObject({
      code: 'COMMON_INVALID_INPUT',
      errors: [{ field: 'animalName', reason: '이름은 6자 이하여야 합니다.' }],
      message: '입력값이 올바르지 않습니다.',
      status: 400,
      traceId: '4f2c9e1a7b3d4c5e',
    });
  });

  it('hides a non-envelope server failure behind the common code', async () => {
    const client = createApiClient({ baseURL: 'https://api.example.test/api/core/v1' });
    client.defaults.adapter = async config => {
      throw new AxiosError('Request failed', 'ERR_BAD_RESPONSE', config, undefined, {
        config,
        data: '<html>internal</html>',
        headers: { 'x-trace-id': 'header-trace' },
        status: 500,
        statusText: 'Error',
      });
    };

    await expect(client.get('/pets')).rejects.toMatchObject({
      code: 'COMMON_INTERNAL_ERROR',
      message: '요청을 처리하지 못했습니다.',
      status: 500,
      traceId: 'header-trace',
    });
  });
});

describe('serviceBaseUrl', () => {
  it.each(API_SERVICES)('builds the %s service path', service => {
    expect(serviceBaseUrl(service, 'https://api.example.test/')).toBe(
      `https://api.example.test/api/${service}/v1`,
    );
  });
});

describe('readApiData', () => {
  it('returns data, including an explicit null', () => {
    expect(readApiData(success({ sessionSeq: 3001 }))).toEqual({ sessionSeq: 3001 });
    expect(readApiData(success(null))).toBeNull();
  });

  it('rejects an envelope whose code is not SUCCESS', () => {
    expect(() =>
      readApiData({
        status: 409,
        code: 'SIMULATION_ACTIVE_ALREADY_EXISTS',
        message: '이미 진행 중인 모의입양이 있습니다.',
        data: null,
        errors: [],
        traceId: '4f2c9e1a7b3d4c5e',
      }),
    ).toThrow(expect.objectContaining({ code: 'SIMULATION_ACTIVE_ALREADY_EXISTS' }));
  });
});
