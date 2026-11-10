import { create } from 'zustand';

import {
  clearAuthSession,
  getKakaoAccessToken,
  getRefreshToken,
  logoutFromServer,
  registerKakao,
  restoreAuthSession,
  saveAuthSession,
  subscribeAuthSessionCleared,
  verifyKakaoToken,
  withdrawFromServer,
  type AuthUser,
  type KakaoRegisterRequest,
} from '../services/auth';
import { getStoredFcmToken } from '../services/notifications/firebaseMessaging';

type RegistrationInput = Omit<KakaoRegisterRequest, 'registrationToken'>;

type AuthState = {
  completeRegistration: () => void;
  error: string | null;
  initialize: () => Promise<void>;
  isAuthenticated: boolean;
  isInitializing: boolean;
  isLoading: boolean;
  loginWithKakao: () => Promise<'authenticated' | 'registrationRequired'>;
  logout: () => Promise<void>;
  register: (input: RegistrationInput) => Promise<void>;
  registrationToken: string | null;
  user: AuthUser | null;
  withdraw: () => Promise<void>;
};

export const useAuthStore = create<AuthState>((set, get) => ({
  completeRegistration: () => set({ isAuthenticated: true, registrationToken: null }),
  error: null,
  initialize: async () => {
    try {
      const tokens = await restoreAuthSession();
      set({ isAuthenticated: Boolean(tokens), isInitializing: false });
    } catch {
      await clearAuthSession();
      set({ isAuthenticated: false, isInitializing: false });
    }
  },
  isAuthenticated: false,
  isInitializing: true,
  isLoading: false,
  loginWithKakao: async () => {
    set({ error: null, isLoading: true });
    try {
      const kakaoAccessToken = await getKakaoAccessToken();
      const result = await verifyKakaoToken(kakaoAccessToken);

      if (result.requiresRegistration) {
        if (!result.registrationToken) {
          throw new Error('회원가입 정보가 없습니다. 카카오 로그인을 다시 시도해 주세요.');
        }
        set({ isLoading: false, registrationToken: result.registrationToken });
        return 'registrationRequired';
      }

      if (!result.accessToken || !result.refreshToken || !result.user) {
        throw new Error('로그인 응답이 올바르지 않습니다.');
      }

      await saveAuthSession({
        accessToken: result.accessToken,
        refreshToken: result.refreshToken,
      });
      set({ isAuthenticated: true, isLoading: false, user: result.user });
      return 'authenticated';
    } catch (error) {
      const message = error instanceof Error ? error.message : '카카오 로그인에 실패했습니다.';
      set({ error: message, isLoading: false });
      throw error;
    }
  },
  logout: async () => {
    set({ error: null, isLoading: true });
    const refreshToken = getRefreshToken();
    try {
      if (refreshToken) {
        const fcmToken = await getStoredFcmToken();
        await logoutFromServer(refreshToken, fcmToken ?? undefined);
      }
    } finally {
      await clearAuthSession();
      set({
        isAuthenticated: false,
        isLoading: false,
        registrationToken: null,
        user: null,
      });
    }
  },
  register: async input => {
    const registrationToken = get().registrationToken;
    if (!registrationToken) {
      throw new Error('회원가입 정보가 만료되었습니다. 카카오 로그인을 다시 해 주세요.');
    }

    set({ error: null, isLoading: true });
    try {
      const result = await registerKakao({ ...input, registrationToken });
      await saveAuthSession({ accessToken: result.accessToken, refreshToken: result.refreshToken });
      set({
        isLoading: false,
        user: { nickname: result.nickname, role: result.role, userSeq: result.userSeq },
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : '회원가입에 실패했습니다.';
      set({ error: message, isLoading: false });
      throw error;
    }
  },
  registrationToken: null,
  user: null,
  withdraw: async () => {
    set({ error: null, isLoading: true });
    try {
      await withdrawFromServer();
      await clearAuthSession();
    } catch (error) {
      const message = error instanceof Error ? error.message : '회원 탈퇴에 실패했습니다.';
      set({ error: message, isLoading: false });
      throw error;
    }
  },
}));

subscribeAuthSessionCleared(() => {
  useAuthStore.setState({
    isAuthenticated: false,
    isLoading: false,
    registrationToken: null,
    user: null,
  });
});
