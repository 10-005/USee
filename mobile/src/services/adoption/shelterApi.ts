import type { ApiResponse } from '../api';
import { apiClient } from '../api/client';

type ShelterImage = {
  imageSeq: number;
  imageUrl: string;
};

export type ShelterDetail = {
  address: string;
  backgroundImage: ShelterImage | null;
  closedDays: string | null;
  description: string | null;
  districtCode: string | null;
  latitude: number;
  longitude: number;
  operatingHours: string | null;
  phone: string;
  profileImage: ShelterImage | null;
  shelterName: string;
  shelterSeq: number;
};

export async function getShelter(shelterSeq: number): Promise<ShelterDetail> {
  const response = await apiClient.get<ApiResponse<ShelterDetail>>(`/shelters/${shelterSeq}`);
  return response.data.data;
}
