import { login } from '@react-native-seoul/kakao-login';

export async function getKakaoAccessToken(): Promise<string> {
  const token = await login();
  return token.accessToken;
}
