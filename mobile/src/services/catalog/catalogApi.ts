import type { ApiResponse } from '../api';
import { apiClient } from '../api/client';

export type District = {
  districtCode: string;
  sidoName: string;
  sigunguName: string;
};

export async function getDistricts(sidoName?: string): Promise<District[]> {
  const response = await apiClient.get<ApiResponse<District[]>>('/districts', {
    params: sidoName ? { sidoName } : undefined,
  });
  return response.data.data;
}
