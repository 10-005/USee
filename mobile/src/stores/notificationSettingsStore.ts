import { create } from 'zustand';
import { persist } from 'zustand/middleware';

import { createPersistStorage } from '../services/storage/persistStorage';

export type NotificationSettingKey =
  | 'mockAdoption'
  | 'chat'
  | 'comment'
  | 'aiMatching'
  | 'adoption';

type NotificationSettingsState = {
  adoption: boolean;
  aiMatching: boolean;
  chat: boolean;
  comment: boolean;
  doNotDisturbEnd: string;
  doNotDisturbStart: string;
  mockAdoption: boolean;
  resetSettings: () => void;
  setDoNotDisturbEnd: (time: string) => void;
  setDoNotDisturbStart: (time: string) => void;
  toggleSetting: (key: NotificationSettingKey) => void;
};

const initialSettings = {
  adoption: false,
  aiMatching: false,
  chat: false,
  comment: true,
  doNotDisturbEnd: '',
  doNotDisturbStart: '',
  mockAdoption: false,
};

export const useNotificationSettingsStore = create<NotificationSettingsState>()(
  persist(
    set => ({
      ...initialSettings,
      resetSettings: () => set(initialSettings),
      setDoNotDisturbEnd: doNotDisturbEnd => set({ doNotDisturbEnd }),
      setDoNotDisturbStart: doNotDisturbStart => set({ doNotDisturbStart }),
      toggleSetting: key => set(state => ({ [key]: !state[key] })),
    }),
    {
      name: 'usee-notification-settings',
      storage: createPersistStorage<NotificationSettingsState>(),
    },
  ),
);
