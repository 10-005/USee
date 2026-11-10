import type { ImageSourcePropType } from 'react-native';
import { create } from 'zustand';

import type { AccountRole } from '../services/api';
import { getAccessToken } from '../services/auth';
import { getMyProfile, type MyProfile } from '../services/users';

const defaultProfileImage = require('../assets/images/mypage/profile-default.png');

export type UserProfile = {
  accountStatus: 'ACTIVE' | 'SUSPENDED';
  birthDate: string;
  image: ImageSourcePropType;
  imageSeq: number | null;
  name: string;
  nickname: string;
  phone: string;
  role: AccountRole;
  userSeq: number;
};

export const initialProfile: UserProfile = {
  accountStatus: 'ACTIVE',
  birthDate: '',
  image: defaultProfileImage,
  imageSeq: null,
  name: '',
  nickname: '',
  phone: '',
  role: 'USER',
  userSeq: 0,
};

type ProfileState = {
  error: string | null;
  isLoaded: boolean;
  isLoading: boolean;
  loadProfile: (force?: boolean) => Promise<void>;
  profile: UserProfile;
  resetProfile: () => void;
  updateProfile: (profile: MyProfile) => void;
};

export const useProfileStore = create<ProfileState>((set, get) => ({
  error: null,
  isLoaded: false,
  isLoading: false,
  loadProfile: async (force = false) => {
    if (!getAccessToken()) {
      return;
    }
    const state = get();
    if (state.isLoading || (state.isLoaded && !force)) {
      return;
    }
    set({ error: null, isLoading: true });
    try {
      const profile = await getMyProfile();
      set({ isLoaded: true, isLoading: false, profile: toUserProfile(profile) });
    } catch (error) {
      set({
        error: error instanceof Error ? error.message : '프로필을 불러오지 못했어요.',
        isLoading: false,
      });
    }
  },
  profile: initialProfile,
  resetProfile: () =>
    set({ error: null, isLoaded: false, isLoading: false, profile: initialProfile }),
  updateProfile: profile => set({ error: null, isLoaded: true, profile: toUserProfile(profile) }),
}));

function toUserProfile(profile: MyProfile): UserProfile {
  return {
    accountStatus: profile.accountStatus,
    birthDate: profile.birthDate.replaceAll('-', '.'),
    image: profile.profileImage ? { uri: profile.profileImage.imageUrl } : defaultProfileImage,
    imageSeq: profile.profileImage?.imageSeq ?? null,
    name: profile.name,
    nickname: profile.nickname,
    phone: formatProfilePhoneNumber(profile.phone),
    role: profile.role,
    userSeq: profile.userSeq,
  };
}

function formatProfilePhoneNumber(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 11);
  if (digits.length <= 3) {
    return digits;
  }
  if (digits.length <= 7) {
    return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  }
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
}
