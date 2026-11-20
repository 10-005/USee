import { NativeModules } from 'react-native';
import type { ApiResponse } from '../api';
import { apiClient } from '../api/client';

export type GeocodeCandidate = {
  placeName?: string | null;
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
  const addresses = response.data.data;
  if (addresses.length) {
    return addresses;
  }
  const geocoder = NativeModules.LocationGeocoder as
    | { searchPlaces?: (name: string) => Promise<GeocodeCandidate[]> }
    | undefined;
  const places = await geocoder?.searchPlaces?.(normalized);
  return (places ?? addresses).map(place => {
    const name = place.placeName?.trim();
    if (
      name &&
      (/^(?:산\s*)?\d+(?:\s*-\s*\d+)?(?:번지)?$/.test(name) || name === getCandidateAddress(place))
    ) {
      return { ...place, placeName: null };
    }
    return place;
  });
}
