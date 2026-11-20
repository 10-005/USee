import AsyncStorage from '@react-native-async-storage/async-storage';

import { useNotificationSettingsStore } from '../notificationSettingsStore';

beforeEach(async () => {
  await AsyncStorage.clear();
  useNotificationSettingsStore.getState().resetSettings();
});

describe('notificationSettingsStore', () => {
  it('개별 알림 설정을 변경한다', () => {
    expect(useNotificationSettingsStore.getState().chat).toBe(false);
    useNotificationSettingsStore.getState().toggleSetting('chat');
    expect(useNotificationSettingsStore.getState().chat).toBe(true);
  });

  it('방해금지 시작 및 종료 시간을 저장한다', () => {
    useNotificationSettingsStore.getState().setDoNotDisturbStart('23:00');
    useNotificationSettingsStore.getState().setDoNotDisturbEnd('08:00');

    expect(useNotificationSettingsStore.getState().doNotDisturbStart).toBe('23:00');
    expect(useNotificationSettingsStore.getState().doNotDisturbEnd).toBe('08:00');
  });
});
