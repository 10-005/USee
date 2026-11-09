import axios, { AxiosError } from 'axios';
import type { AxiosInstance, AxiosResponse } from 'axios';

import { ApiClientError } from './errors';
import { isApiResponse } from './response';
import type { ApiClientOptions } from './types';

const DEFAULT_TIMEOUT_MS = 10_000;
const TRACE_HEADER = 'X-Trace-Id';

const FALLBACK_ERRORS: Record<number, { code: string; message: string }> = {
  400: { code: 'COMMON_INVALID_INPUT', message: '입력값이 올바르지 않습니다.' },
  401: { code: 'AUTH_UNAUTHORIZED', message: '인증이 필요합니다.' },
  403: { code: 'AUTH_FORBIDDEN', message: '요청 권한이 없습니다.' },
  404: { code: 'COMMON_NOT_FOUND', message: '요청한 대상을 찾지 못했습니다.' },
  405: { code: 'COMMON_METHOD_NOT_ALLOWED', message: '지원하지 않는 요청입니다.' },
  500: { code: 'COMMON_INTERNAL_ERROR', message: '요청을 처리하지 못했습니다.' },
  503: { code: 'COMMON_SERVICE_UNAVAILABLE', message: '서비스를 일시적으로 사용할 수 없습니다.' },
};

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
    if (!config.headers.get(TRACE_HEADER)) {
      config.headers.set(TRACE_HEADER, createTraceId());
    }
    return config;
  });

  client.interceptors.response.use(
    (response: AxiosResponse) => response,
    error => Promise.reject(toApiClientError(error)),
  );

  return client;
}

function toApiClientError(error: unknown): ApiClientError {
  if (!(error instanceof AxiosError)) {
    return new ApiClientError({
      code: 'NETWORK_ERROR',
      message: '서버에 연결하지 못했습니다.',
    });
  }

  const body = error.response?.data;
  if (isApiResponse(body)) {
    return new ApiClientError({
      code: body.code,
      errors: body.errors,
      message: body.message,
      status: error.response?.status ?? body.status,
      traceId: body.traceId,
    });
  }

  const status = error.response?.status;
  const fallback = status ? FALLBACK_ERRORS[status] : undefined;
  return new ApiClientError({
    code: fallback?.code ?? 'NETWORK_ERROR',
    message: fallback?.message ?? '서버에 연결하지 못했습니다.',
    status,
    traceId: headerValue(error.response?.headers?.[TRACE_HEADER.toLowerCase()]),
  });
}

function createTraceId(): string {
  return Array.from({ length: 16 }, () => Math.floor(Math.random() * 16).toString(16)).join('');
}

function headerValue(value: unknown): string | undefined {
  return typeof value === 'string' && value ? value : undefined;
}
