import type { ApiError } from './types';

export class ApiClientError extends Error {
  readonly code: string;
  readonly details?: unknown;
  readonly status?: number;
  readonly traceId?: string;

  constructor({ code, details, message, status, traceId }: ApiError) {
    super(message);
    this.name = 'ApiClientError';
    this.code = code;
    this.details = details;
    this.status = status;
    this.traceId = traceId;
  }
}

export function getApiErrorDisplayMessage(error: unknown, fallback: string): string {
  const message = error instanceof Error ? error.message : fallback;
  if (!__DEV__ || !(error instanceof ApiClientError)) {
    return message;
  }

  const fieldDetails = Array.isArray(error.details)
    ? error.details
        .map(detail => {
          if (!detail || typeof detail !== 'object') {
            return null;
          }
          const { field, reason } = detail as { field?: unknown; reason?: unknown };
          return typeof field === 'string' && typeof reason === 'string'
            ? `${field}: ${reason}`
            : null;
        })
        .filter(Boolean)
        .join('\n')
    : '';
  const diagnostics = [error.code, error.status ? `HTTP ${error.status}` : null, error.traceId]
    .filter(Boolean)
    .join(' · ');
  return [message, fieldDetails, diagnostics].filter(Boolean).join('\n\n');
}

export function getApiFieldError(error: unknown, targetField: string): string | null {
  if (!(error instanceof ApiClientError) || !Array.isArray(error.details)) {
    return null;
  }

  for (const detail of error.details) {
    if (!detail || typeof detail !== 'object') {
      continue;
    }
    const { field, reason } = detail as { field?: unknown; reason?: unknown };
    if (field === targetField && typeof reason === 'string') {
      return reason;
    }
  }
  return null;
}
