import axios, { AxiosError } from 'axios';
import type { AxiosInstance, InternalAxiosRequestConfig } from 'axios';

import { ApiClientError } from './errors';
import type { ApiClientOptions, ApiErrorResponse, ApiResponse, AuthTokens } from './types';

const DEFAULT_TIMEOUT_MS = 10_000;

export function createApiClient({
  baseURL,
  getAccessToken,
  refresh,
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

  let refreshPromise: Promise<AuthTokens> | null = null;

  client.interceptors.response.use(response => response, async error => {
    const request = error.config as (InternalAxiosRequestConfig & { _authRetry?: boolean }) | undefined;
    const responseData = error.response?.data as ApiErrorResponse | undefined;

    if (
      refresh &&
      request &&
      !request._authRetry &&
      responseData?.code === 'AUTH_TOKEN_EXPIRED'
    ) {
      request._authRetry = true;

      try {
        refreshPromise ??= refreshTokens(baseURL, timeout, refresh);
        const tokens = await refreshPromise;
        request.headers.set('Authorization', `Bearer ${tokens.accessToken}`);
        return await client.request(request);
      } catch (refreshError) {
        const apiError = toApiClientError(refreshError);
        if (apiError.status === 401 || apiError.status === 403) {
          await refresh.onAuthenticationFailure();
        }
        logApiError(apiError);
        return Promise.reject(apiError);
      } finally {
        refreshPromise = null;
      }
    }

    const apiError = toApiClientError(error);
    logApiError(apiError);
    return Promise.reject(apiError);
  });

  return client;
}

async function refreshTokens(
  baseURL: string,
  timeout: number,
  refresh: NonNullable<ApiClientOptions['refresh']>,
): Promise<AuthTokens> {
  const refreshToken = await refresh.getRefreshToken();
  if (!refreshToken) {
    throw new ApiClientError({ code: 'AUTH_INVALID_REFRESH_TOKEN', message: '로그인이 필요합니다.' });
  }

  const response = await axios.post<ApiResponse<AuthTokens>>(
    refresh.endpoint,
    { refreshToken },
    { baseURL, timeout },
  );
  await refresh.onTokensRefreshed(response.data.data);
  return response.data.data;
}

function toApiClientError(error: unknown): ApiClientError {
  if (error instanceof ApiClientError) {
    return error;
  }

  if (!(error instanceof AxiosError)) {
    return new ApiClientError({
      code: 'UNKNOWN_ERROR',
      message: '알 수 없는 오류가 발생했습니다.',
    });
  }

  const responseData = error.response?.data as ApiErrorResponse | undefined;

  return new ApiClientError({
    code: responseData?.code ?? error.code ?? 'NETWORK_ERROR',
    details: responseData?.errors,
    message: responseData?.message ?? error.message,
    status: error.response?.status,
    traceId: responseData?.traceId,
  });
}

function logApiError(error: ApiClientError): void {
  if (!__DEV__) {
    return;
  }

  // Tokens and request bodies are intentionally excluded from diagnostics.
  console.warn('[API_ERROR]', {
    code: error.code,
    details: error.details,
    message: error.message,
    status: error.status,
    traceId: error.traceId,
  });
}
