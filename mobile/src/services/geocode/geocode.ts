import type { ApiResponse } from '../api';
import { apiClient } from '../api/client';

export type GeocodeCandidate = {
  jibunAddress: string | null;
  latitude: number;
  longitude: number;
  roadAddress: string | null;
};

export function getCandidateAddress(candidate: GeocodeCandidate) {
  return candidate.roadAddress || candidate.jibunAddress || '';
}

export async function searchGeocodeCandidates(query: string): Promise<GeocodeCandidate[]> {
  const normalized = query.trim().replace(/\s+/g, ' ');
  if (!normalized || normalized.length > 100) {
    return [];
  }
  const response = await apiClient.get<ApiResponse<GeocodeCandidate[]>>('/geocode', {
    params: { query: normalized },
  });
  return response.data.data;
}
