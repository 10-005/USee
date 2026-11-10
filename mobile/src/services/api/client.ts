import { clearAuthSession, getAccessToken, getRefreshToken, saveAuthSession } from '../auth/authSession';
import { createApiClient } from './apiClient';

export const API_BASE_URL = 'https://k15d106.p.ssafy.io/api/core/v1';

export const apiClient = createApiClient({
  baseURL: API_BASE_URL,
  getAccessToken,
  refresh: {
    endpoint: '/auth/refresh',
    getRefreshToken,
    onAuthenticationFailure: clearAuthSession,
    onTokensRefreshed: saveAuthSession,
  },
});
