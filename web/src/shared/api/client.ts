import { env } from '@/shared/config/env';
import type { ApiResponse, FieldError } from './contracts';

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}
function fieldErrors(value: unknown): FieldError[] {
  if (!Array.isArray(value)) return [];
  return value.filter(
    (item): item is FieldError =>
      isRecord(item) && typeof item.field === 'string' && typeof item.reason === 'string',
  );
}
export class ApiError extends Error {
  readonly status: number;
  readonly body: unknown;
  readonly code?: string;
  readonly errors: FieldError[];
  readonly traceId?: string;

  constructor(status: number, body: unknown, headerTraceId?: string | null) {
    const payload = isRecord(body) ? body : undefined;
    const message =
      typeof payload?.message === 'string' && payload.message.trim()
        ? payload.message
        : `API 요청 실패 (HTTP ${status})`;
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
    this.code = typeof payload?.code === 'string' ? payload.code : undefined;
    this.errors = fieldErrors(payload?.errors);
    this.traceId =
      headerTraceId || (typeof payload?.traceId === 'string' ? payload.traceId : undefined);
  }
}

type RequestOptions = Omit<RequestInit, 'body'> & { json?: unknown; timeoutMs?: number };
type RawResponse = { status: number; body: unknown; traceId: string | null };

async function request(path: string, options: RequestOptions): Promise<RawResponse> {
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
  const traceId = response.headers.get('X-Trace-Id');
  if (response.status === 204) return { status: response.status, body: undefined, traceId };
  const text = await response.text();
  let body: unknown;
  if (text) {
    try {
      body = JSON.parse(text) as unknown;
    } catch {
      if (!response.ok) throw new ApiError(response.status, text, traceId);
      throw new Error('API에서 유효한 JSON 응답을 받지 못했습니다.');
    }
  }
  if (!response.ok) throw new ApiError(response.status, body, traceId);
  return { status: response.status, body, traceId };
}

/** 기존 호출자를 위한 응답 원문 반환. 타입 인자는 런타임 검증을 대신하지 않습니다. */
export async function requestJson<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T | undefined> {
  return (await request(path, options)).body as T | undefined;
}

export type ApiResult<T> =
  | { status: 204; data: undefined; traceId: string | null }
  | { status: number; data: T; traceId: string; response: ApiResponse<T> };

/** 공통 봉투를 확인하고 data와 추적 정보를 반환합니다. data 자체는 엔드포인트에서 검증해야 합니다. */
export async function requestApi<T>(
  path: string,
  options: RequestOptions = {},
): Promise<ApiResult<T>> {
  const result = await request(path, options);
  if (result.status === 204) return { status: 204, data: undefined, traceId: result.traceId };
  const body = result.body;
  if (
    !isRecord(body) ||
    typeof body.status !== 'number' ||
    body.status !== result.status ||
    typeof body.code !== 'string' ||
    typeof body.message !== 'string' ||
    !('data' in body) ||
    !Array.isArray(body.errors) ||
    fieldErrors(body.errors).length !== body.errors.length ||
    typeof body.traceId !== 'string'
  ) {
    throw new Error('API 응답이 공통 응답 형식과 일치하지 않습니다.');
  }
  if (body.code !== 'SUCCESS') throw new ApiError(result.status, body, result.traceId);
  const response = body as unknown as ApiResponse<T>;
  return {
    status: result.status,
    data: response.data,
    traceId: result.traceId || response.traceId,
    response,
  };
}
