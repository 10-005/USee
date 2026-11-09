import type { ApiError, ApiFieldError } from './types';

export class ApiClientError extends Error {
  readonly code: string;
  readonly errors: ApiFieldError[];
  readonly status?: number;
  readonly traceId?: string;

  constructor({ code, errors = [], message, status, traceId }: ApiError) {
    super(message);
    this.name = 'ApiClientError';
    this.code = code;
    this.errors = errors;
    this.status = status;
    this.traceId = traceId;
  }
}
