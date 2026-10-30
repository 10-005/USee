import axios, { AxiosError } from 'axios';
import type { AxiosInstance } from 'axios';

import { ApiClientError } from './errors';
import type { ApiClientOptions, ApiErrorResponse } from './types';

const DEFAULT_TIMEOUT_MS = 10_000;

export function createApiClient({
  baseURL,
  getAccessToken,
  timeout = DEFAULT_TIMEOUT_MS,
}: ApiClientOptions): AxiosInstance {
  const client = axios.create({
    baseURL,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    timeout,
  });

  client.interceptors.request.use(async config => {
    const accessToken = await getAccessToken?.();

    if (accessToken) {
      config.headers.set('Authorization', `Bearer ${accessToken}`);
    }

    return config;
  });

  client.interceptors.response.use(
    response => response,
    error => Promise.reject(toApiClientError(error)),
  );

  return client;
}

function toApiClientError(error: unknown): ApiClientError {
  if (!(error instanceof AxiosError)) {
    return new ApiClientError({
      code: 'UNKNOWN_ERROR',
      message: '알 수 없는 오류가 발생했습니다.',
    });
  }

  const responseData = error.response?.data as ApiErrorResponse | undefined;

  return new ApiClientError({
    code: responseData?.code ?? error.code ?? 'NETWORK_ERROR',
    details: responseData?.details,
    message: responseData?.message ?? error.message,
    status: error.response?.status,
  });
}
