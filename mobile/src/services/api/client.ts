import {
  clearAuthSession,
  getAccessToken,
  getRefreshToken,
  saveAuthSession,
} from '../auth/authSession';
import { createApiClient } from './apiClient';
import type { ApiClientOptions } from './types';

export const API_ORIGIN = 'https://k15d106.p.ssafy.io';

export const API_BASE_URL = `${API_ORIGIN}/api/core/v1`;

const refresh: NonNullable<ApiClientOptions['refresh']> = {
  endpoint: `${API_BASE_URL}/auth/refresh`,
  getRefreshToken,
  onAuthenticationFailure: clearAuthSession,
  onTokensRefreshed: saveAuthSession,
};

export const apiClient = createApiClient({
  baseURL: API_BASE_URL,
  getAccessToken,
  refresh,
});

export const simulationClient = createApiClient({
  baseURL: `${API_ORIGIN}/api/simulation/v1`,
  getAccessToken,
  refresh,
});

export const chatClient = createApiClient({
  baseURL: `${API_ORIGIN}/api/chat/v1`,
  getAccessToken,
  refresh,
});
