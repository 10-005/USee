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

    logApiCall(config);
    return config;
  });

  let refreshPromise: Promise<AuthTokens> | null = null;

  client.interceptors.response.use(
    response => {
      logApiCall(response.config, response.status);
      return response;
    },
    async error => {
      const request = error.config as
        | (InternalAxiosRequestConfig & { _authRetry?: boolean })
        | undefined;
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
          logApiError(apiError, request);
          return Promise.reject(apiError);
        } finally {
          refreshPromise = null;
        }
      }

      if (
        refresh &&
        (responseData?.code === 'AUTH_TOKEN_INVALID' || responseData?.code === 'AUTH_UNAUTHORIZED')
      ) {
        await refresh.onAuthenticationFailure();
      }

      const apiError = toApiClientError(error);
      logApiError(apiError, request);
      return Promise.reject(apiError);
    },
  );

  return client;
}

async function refreshTokens(
  baseURL: string,
  timeout: number,
  refresh: NonNullable<ApiClientOptions['refresh']>,
): Promise<AuthTokens> {
  const refreshToken = await refresh.getRefreshToken();
  if (!refreshToken) {
    throw new ApiClientError({
      code: 'AUTH_INVALID_REFRESH_TOKEN',
      message: '로그인이 필요합니다.',
    });
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

function describeApiCall(config?: InternalAxiosRequestConfig): string {
  if (!config) {
    return '';
  }
  const method = (config.method ?? 'get').toUpperCase();
  const base = (config.baseURL ?? '').replace(/\/$/, '');
  const path = config.url ?? '';
  const url = /^https?:\/\//.test(path)
    ? path
    : `${base}${path.startsWith('/') || path.length === 0 ? path : `/${path}`}`;
  const params =
    config.params && typeof config.params === 'object' ? ` ${JSON.stringify(config.params)}` : '';
  return `${method} ${url}${params}`;
}

function logApiCall(config: InternalAxiosRequestConfig | undefined, status?: number): void {
  if (!__DEV__) {
    return;
  }
  // Tokens and request bodies are intentionally excluded from diagnostics.
  // eslint-disable-next-line no-console
  console.log(`[API] ${status ?? '->'} ${describeApiCall(config)}`);
}

function logApiError(error: ApiClientError, config?: InternalAxiosRequestConfig): void {
  if (!__DEV__) {
    return;
  }

  console.warn('[API_ERROR]', {
    call: describeApiCall(config),
    code: error.code,
    details: error.details,
    message: error.message,
    status: error.status,
    traceId: error.traceId,
  });
}
