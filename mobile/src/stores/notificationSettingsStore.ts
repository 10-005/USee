import { create } from 'zustand';
import { persist } from 'zustand/middleware';

import { createPersistStorage } from '../services/storage/persistStorage';
import type { NotificationSettings } from '../services/notifications/notificationSettingsApi';

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
  quietEnabled: boolean;
  applyServerSettings: (settings: NotificationSettings) => void;
  resetSettings: () => void;
  setDoNotDisturbEnd: (time: string) => void;
  setDoNotDisturbStart: (time: string) => void;
  toggleSetting: (key: NotificationSettingKey) => void;
};

const initialSettings = {
  adoption: false,
  aiMatching: false,
  chat: false,
  comment: false,
  doNotDisturbEnd: '08:00',
  doNotDisturbStart: '22:00',
  mockAdoption: false,
  quietEnabled: false,
};

export const useNotificationSettingsStore = create<NotificationSettingsState>()(
  persist(
    set => ({
      ...initialSettings,
      applyServerSettings: settings =>
        set({
          adoption: settings.adoptionEnabled,
          aiMatching: settings.aiMatchEnabled,
          chat: settings.chatEnabled,
          comment: settings.commentEnabled,
          mockAdoption: settings.simulationEnabled,
          quietEnabled: settings.quietEnabled,
          doNotDisturbStart: settings.quietStart,
          doNotDisturbEnd: settings.quietEnd,
        }),
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
