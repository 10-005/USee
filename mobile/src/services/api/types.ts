export type AccessTokenProvider = () => Promise<string | null> | string | null;

export type ApiClientOptions = {
  baseURL: string;
  getAccessToken?: AccessTokenProvider;
  timeout?: number;
};

export type ApiError = {
  code: string;
  details?: unknown;
  message: string;
  status?: number;
};

export type ApiErrorResponse = {
  code?: string;
  details?: unknown;
  message?: string;
};

export type ApiResponse<T> = {
  data: T;
  message?: string;
};
