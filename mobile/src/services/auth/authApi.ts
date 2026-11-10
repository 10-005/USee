import type { AccountRole, ApiResponse, AuthTokens } from '../api';
import { apiClient } from '../api/client';

export type AuthUser = {
  nickname: string;
  role: AccountRole;
  userSeq: number;
};

export type KakaoVerifyResponse = {
  accessToken: string | null;
  expiresIn: number | null;
  refreshToken: string | null;
  registrationToken: string | null;
  requiresRegistration: boolean;
  tokenType: 'Bearer' | null;
  user: AuthUser | null;
};

export type KakaoRegisterRequest = {
  birthDate: string;
  name: string;
  nickname: string;
  phone: string;
  registrationToken: string;
};

export type KakaoRegisterResponse = AuthTokens & AuthUser & { tokenType: 'Bearer' };

export async function verifyKakaoToken(kakaoAccessToken: string): Promise<KakaoVerifyResponse> {
  const response = await apiClient.post<ApiResponse<KakaoVerifyResponse>>('/auth/kakao/verify', {
    kakaoAccessToken,
  });
  return response.data.data;
}

export async function registerKakao(request: KakaoRegisterRequest): Promise<KakaoRegisterResponse> {
  const response = await apiClient.post<ApiResponse<KakaoRegisterResponse>>(
    '/auth/kakao/register',
    request,
  );
  return response.data.data;
}

export async function logoutFromServer(refreshToken: string, fcmToken?: string): Promise<void> {
  await apiClient.post('/auth/logout', { fcmToken, refreshToken });
}

export async function withdrawFromServer(): Promise<void> {
  await apiClient.delete('/users/me');
}
