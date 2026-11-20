import AsyncStorage from '@react-native-async-storage/async-storage';
import { PermissionsAndroid, Platform } from 'react-native';

import { STORAGE_KEYS } from '../../../constants/storageKeys';
import { useNotificationSettingsStore } from '../../../stores/notificationSettingsStore';
import { getAccessToken } from '../../auth/authSession';
import {
  initializeNotificationPermission,
  loadNotificationSettings,
  saveNotificationSettings,
  syncInitialNotificationSettings,
} from '../notificationSettings';
import {
  getMyNotificationSettings,
  updateMyNotificationSettings,
} from '../notificationSettingsApi';

jest.mock('../../auth/authSession', () => ({ getAccessToken: jest.fn() }));
jest.mock('../notificationSettingsApi', () => ({
  getMyNotificationSettings: jest.fn(),
  updateMyNotificationSettings: jest.fn(),
}));

const serverSettings = {
  simulationEnabled: true,
  chatEnabled: true,
  commentEnabled: true,
  aiMatchEnabled: true,
  adoptionEnabled: true,
  quietEnabled: false,
  quietStart: '22:00',
  quietEnd: '08:00',
};

beforeEach(async () => {
  jest.clearAllMocks();
  await AsyncStorage.clear();
  useNotificationSettingsStore.getState().resetSettings();
  jest.replaceProperty(Platform, 'OS', 'android');
  jest.spyOn(Platform, 'Version', 'get').mockReturnValue(33);
  jest.spyOn(PermissionsAndroid, 'request').mockResolvedValue(PermissionsAndroid.RESULTS.GRANTED);
  jest.mocked(getAccessToken).mockReturnValue('access-token');
  jest.mocked(getMyNotificationSettings).mockResolvedValue(serverSettings);
  jest
    .mocked(updateMyNotificationSettings)
    .mockImplementation(async changes => ({ ...serverSettings, ...changes }));
});

afterEach(() => jest.restoreAllMocks());

it.each([true, false])('최초 권한 결과 %s를 5종 모두에 한 번만 적용한다', async allowed => {
  jest
    .mocked(PermissionsAndroid.request)
    .mockResolvedValue(
      allowed ? PermissionsAndroid.RESULTS.GRANTED : PermissionsAndroid.RESULTS.DENIED,
    );
  await initializeNotificationPermission();
  expect(updateMyNotificationSettings).toHaveBeenCalledWith({
    simulationEnabled: allowed,
    chatEnabled: allowed,
    commentEnabled: allowed,
    aiMatchEnabled: allowed,
    adoptionEnabled: allowed,
  });
  const state = useNotificationSettingsStore.getState();
  expect([state.mockAdoption, state.chat, state.comment, state.aiMatching, state.adoption]).toEqual(
    Array(5).fill(allowed),
  );

  await initializeNotificationPermission();
  expect(PermissionsAndroid.request).toHaveBeenCalledTimes(1);
  expect(updateMyNotificationSettings).toHaveBeenCalledTimes(1);
});

it('로그인 전 거절 결과를 보관하고 로그인 후 동기화한다', async () => {
  jest.mocked(getAccessToken).mockReturnValue(null);
  jest.mocked(PermissionsAndroid.request).mockResolvedValue(PermissionsAndroid.RESULTS.DENIED);
  await initializeNotificationPermission();
  expect(updateMyNotificationSettings).not.toHaveBeenCalled();

  jest.mocked(getAccessToken).mockReturnValue('signed-in-token');
  await Promise.all([syncInitialNotificationSettings(), syncInitialNotificationSettings()]);
  expect(updateMyNotificationSettings).toHaveBeenCalledTimes(1);
  expect(updateMyNotificationSettings).toHaveBeenCalledWith(
    expect.objectContaining({ chatEnabled: false, simulationEnabled: false }),
  );
});

it('서버 저장 실패는 재시도하고 성공 후 개별 설정을 덮어쓰지 않는다', async () => {
  jest.mocked(updateMyNotificationSettings).mockRejectedValueOnce(new Error('network'));
  await expect(initializeNotificationPermission()).rejects.toThrow('network');
  await syncInitialNotificationSettings();
  await saveNotificationSettings({ chatEnabled: false });
  await initializeNotificationPermission();
  expect(PermissionsAndroid.request).toHaveBeenCalledTimes(1);
  expect(updateMyNotificationSettings).toHaveBeenCalledTimes(3);
  expect(useNotificationSettingsStore.getState().chat).toBe(false);
});

it('조회한 서버 설정을 표시하고 변경 시에는 보낸 필드만 저장한다', async () => {
  jest
    .mocked(getMyNotificationSettings)
    .mockResolvedValue({
      ...serverSettings,
      chatEnabled: false,
      quietEnabled: true,
      quietStart: '23:00',
    });
  await loadNotificationSettings();
  expect(getMyNotificationSettings).toHaveBeenCalledTimes(1);
  expect(useNotificationSettingsStore.getState()).toMatchObject({
    chat: false,
    comment: true,
    quietEnabled: true,
    doNotDisturbStart: '23:00',
  });
  await saveNotificationSettings({ chatEnabled: true });
  expect(updateMyNotificationSettings).toHaveBeenCalledWith({ chatEnabled: true });
  expect(useNotificationSettingsStore.getState().chat).toBe(true);
});

it('변경 실패 시 표시하던 값을 유지한다', async () => {
  await loadNotificationSettings();
  jest.mocked(updateMyNotificationSettings).mockRejectedValueOnce(new Error('network'));
  await expect(saveNotificationSettings({ chatEnabled: false })).rejects.toThrow('network');
  expect(useNotificationSettingsStore.getState().chat).toBe(true);
});

it('Android 13 미만에서는 권한 팝업 없이 모두 허용으로 초기화한다', async () => {
  jest.spyOn(Platform, 'Version', 'get').mockReturnValue(32);
  await initializeNotificationPermission();
  expect(PermissionsAndroid.request).not.toHaveBeenCalled();
  expect(updateMyNotificationSettings).toHaveBeenCalledWith(
    expect.objectContaining({ chatEnabled: true }),
  );
  expect(
    JSON.parse((await AsyncStorage.getItem(STORAGE_KEYS.INITIAL_NOTIFICATION_PERMISSION))!),
  ).toEqual({ allowed: true, synced: true });
});
