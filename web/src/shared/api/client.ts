import { env } from '@/shared/config/env';

export class ApiError extends Error {
  readonly status: number;
  readonly body: unknown;

  constructor(status: number, body: unknown) {
    super(`API 요청 실패 (HTTP ${status})`);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

type RequestOptions = Omit<RequestInit, 'body'> & {
  json?: unknown;
  timeoutMs?: number;
};

/** 응답 원문을 반환합니다. 타입 인자는 런타임 응답 검증을 대신하지 않습니다. */
export async function requestJson<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T | undefined> {
  if (
    !path.startsWith('/') ||
    path.startsWith('//') ||
    path.includes('\\') ||
    path.includes('#') ||
    /(^|\/)\.\.(\/|$)/.test(path)
  ) {
    throw new Error('API 경로는 /로 시작하는 상대 경로여야 합니다.');
  }
  const { json, timeoutMs = 15_000, signal, headers: customHeaders, ...init } = options;
  const headers = new Headers(customHeaders);
  if (!headers.has('Accept')) headers.set('Accept', 'application/json');
  if (json !== undefined) headers.set('Content-Type', 'application/json');
  const timeout = AbortSignal.timeout(timeoutMs);
  const response = await fetch(`${env.apiBaseUrl}${path}`, {
    ...init,
    headers,
    body: json === undefined ? undefined : JSON.stringify(json),
    signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
  });
  const text = await response.text();
  let body: unknown;
  if (text) {
    try {
      body = JSON.parse(text) as unknown;
    } catch {
      if (!response.ok) throw new ApiError(response.status, text);
      throw new Error('API에서 유효한 JSON 응답을 받지 못했습니다.');
    }
  }
  if (!response.ok) throw new ApiError(response.status, body);
  return body as T | undefined;
}
