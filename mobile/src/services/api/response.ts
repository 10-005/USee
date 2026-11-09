import { ApiClientError } from './errors';
import type { ApiFieldError, ApiResponse } from './types';

export function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const body = value as ApiResponse<unknown>;
  return (
    typeof body.status === 'number' &&
    typeof body.code === 'string' &&
    typeof body.message === 'string' &&
    'data' in body &&
    Array.isArray(body.errors) &&
    body.errors.every(isFieldError) &&
    typeof body.traceId === 'string'
  );
}

export function readApiData<T>(body: unknown): T {
  if (!isApiResponse(body) || body.code !== 'SUCCESS') {
    throw new ApiClientError({
      code: isApiResponse(body) ? body.code : 'UNKNOWN_ERROR',
      errors: isApiResponse(body) ? body.errors : [],
      message: isApiResponse(body) ? body.message : '응답을 해석하지 못했습니다.',
      status: isApiResponse(body) ? body.status : undefined,
      traceId: isApiResponse(body) ? body.traceId : undefined,
    });
  }
  return body.data as T;
}

function isFieldError(value: unknown): value is ApiFieldError {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const error = value as ApiFieldError;
  return typeof error.field === 'string' && typeof error.reason === 'string';
}
