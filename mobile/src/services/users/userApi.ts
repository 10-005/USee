import type { AccountRole, ApiResponse } from '../api';
import { apiClient } from '../api/client';

export type ProfileImage = {
  imageSeq: number;
  imageUrl: string;
};

export type MyProfile = {
  accountStatus: 'ACTIVE' | 'SUSPENDED';
  birthDate: string;
  name: string;
  nickname: string;
  phone: string;
  profileImage: ProfileImage | null;
  role: AccountRole;
  userSeq: number;
};

export type UpdateMyProfileRequest = Partial<{
  birthDate: string;
  name: string;
  nickname: string;
  phone: string;
  profileImageSeq: number | null;
}>;

export type PublicProfile = {
  displayName: string;
  profileImage: ProfileImage | null;
  role: AccountRole;
  userSeq: number;
};

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

export async function getMyProfile(): Promise<MyProfile> {
  const response = await apiClient.get<ApiResponse<MyProfile>>('/users/me');
  return response.data.data;
}

export async function updateMyProfile(request: UpdateMyProfileRequest): Promise<MyProfile> {
  const response = await apiClient.patch<ApiResponse<MyProfile>>('/users/me', request);
  return response.data.data;
}

export async function getPublicProfile(userSeq: number): Promise<PublicProfile> {
  const response = await apiClient.get<ApiResponse<PublicProfile>>(`/users/${userSeq}/profile`);
  return response.data.data;
}
