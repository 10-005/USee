import type { ImageSourcePropType } from 'react-native';
import { create } from 'zustand';

export type UserProfile = {
  birthDate: string;
  image: ImageSourcePropType;
  name: string;
  nickname: string;
  phone: string;
};

export const initialProfile: UserProfile = {
  birthDate: '2001.05.27',
  image: require('../assets/images/mypage/profile-default.png'),
  name: '박범준',
  nickname: '치이카와',
  phone: '010-1234-5678',
};

type ProfileState = {
  profile: UserProfile;
  resetProfile: () => void;
  updateProfile: (profile: UserProfile) => void;
};

export const useProfileStore = create<ProfileState>(set => ({
  profile: initialProfile,
  resetProfile: () => set({ profile: initialProfile }),
  updateProfile: profile => set({ profile }),
}));
