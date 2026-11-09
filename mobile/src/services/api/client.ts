import { TEMP_ACCESS_TOKEN } from './accessToken';
import { createApiClient } from './apiClient';
import type { AccessTokenProvider, ApiService } from './types';
import { API_SERVICES } from './types';

export const API_ORIGIN = 'https://k15d106.p.ssafy.io';

const accessTokenProvider: { current: AccessTokenProvider } = {
  current: () => TEMP_ACCESS_TOKEN,
};

export function setAccessTokenProvider(provider: AccessTokenProvider) {
  accessTokenProvider.current = provider;
}

export function serviceBaseUrl(service: ApiService, origin = API_ORIGIN): string {
  return `${origin.replace(/\/$/, '')}/api/${service}/v1`;
}

export function createServiceClients(origin = API_ORIGIN) {
  return Object.fromEntries(
    API_SERVICES.map(service => [
      service,
      createApiClient({
        baseURL: serviceBaseUrl(service, origin),
        getAccessToken: () => accessTokenProvider.current(),
      }),
    ]),
  ) as Record<ApiService, ReturnType<typeof createApiClient>>;
}

export const apiClients = createServiceClients();

export const simulationClient = apiClients.simulation;
