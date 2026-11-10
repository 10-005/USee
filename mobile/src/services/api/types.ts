export type AccessTokenProvider = () => Promise<string | null> | string | null;

export type ApiClientOptions = {
  baseURL: string;
  getAccessToken?: AccessTokenProvider;
  refresh?: {
    endpoint: string;
    getRefreshToken: AccessTokenProvider;
    onAuthenticationFailure: () => Promise<void> | void;
    onTokensRefreshed: (tokens: AuthTokens) => Promise<void> | void;
  };
  timeout?: number;
};

export type ApiError = {
  code: string;
  details?: unknown;
  message: string;
  status?: number;
  traceId?: string;
};

export type ApiErrorResponse = {
  code?: string;
  errors?: Array<{ field?: string; reason?: string }>;
  message?: string;
  traceId?: string;
};

export type ApiResponse<T> = {
  code: string;
  data: T;
  errors: Array<{ field?: string; reason?: string }>;
  message: string;
  status: number;
  traceId: string;
};

export type AuthTokens = {
  accessToken: string;
  refreshToken: string;
};

export type AccountRole = 'USER' | 'SHELTER' | 'ADMIN';
