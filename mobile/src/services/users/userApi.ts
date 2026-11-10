import type { ApiResponse } from '../api';
import { apiClient } from '../api/client';

export type NicknameAvailability = {
  available: boolean;
  nickname: string;
};

export async function checkNicknameAvailability(nickname: string): Promise<NicknameAvailability> {
  const response = await apiClient.get<ApiResponse<NicknameAvailability>>(
    '/users/nickname-availability',
    { params: { nickname } },
  );
  return response.data.data;
}
