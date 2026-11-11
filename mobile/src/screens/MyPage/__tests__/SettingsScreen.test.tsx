import AsyncStorage from '@react-native-async-storage/async-storage';
import { Alert } from 'react-native';
import renderer, { act } from 'react-test-renderer';

import { readAppNotificationPermission } from '../../../services/notifications/appNotificationPermission';
import {
  getMyNotificationSettings,
  updateMyNotificationSettings,
} from '../../../services/notifications/notificationSettingsApi';
import { useNotificationSettingsStore } from '../../../stores/notificationSettingsStore';
import { SettingsScreen } from '../SettingsScreen';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (effect: () => void) => require('react').useEffect(effect, [effect]),
}));
jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);
jest.mock('../../../services/notifications/appNotificationPermission', () => ({
  readAppNotificationPermission: jest.fn(),
}));
jest.mock('../../../services/notifications/notificationSettingsApi', () => ({
  getMyNotificationSettings: jest.fn(),
  updateMyNotificationSettings: jest.fn(),
}));
jest.mock('../../../services/notifications/firebaseMessaging', () => ({
  getStoredFcmToken: jest.fn().mockResolvedValue(null),
}));
jest.mock('../../../services/notifications/deviceTokenRegistration', () => ({
  registerDeviceTokenIfSignedIn: jest.fn(),
  deactivateDeviceTokenIfSignedIn: jest.fn(),
}));

const serverSettings = {
  simulationEnabled: false,
  chatEnabled: true,
  commentEnabled: false,
  aiMatchEnabled: true,
  adoptionEnabled: false,
  quietEnabled: false,
  quietStart: '22:00',
  quietEnd: '08:00',
};
let screen: renderer.ReactTestRenderer;

beforeEach(async () => {
  jest.clearAllMocks();
  await AsyncStorage.clear();
  useNotificationSettingsStore.getState().resetSettings();
  jest.mocked(readAppNotificationPermission).mockResolvedValue(true);
  jest.mocked(getMyNotificationSettings).mockResolvedValue(serverSettings);
  jest
    .mocked(updateMyNotificationSettings)
    .mockImplementation(async changes => ({ ...serverSettings, ...changes }));
});

afterEach(async () => {
  await act(async () => screen?.unmount());
  jest.restoreAllMocks();
});

async function renderSettings() {
  await act(async () => {
    screen = renderer.create(
      <SettingsScreen
        navigation={{ goBack: jest.fn() } as never}
        route={{ name: 'Settings', key: 'settings' }}
      />,
    );
  });
}

it('환경설정에 진입하면 서버에서 조회한 5종 설정을 표시한다', async () => {
  await renderSettings();
  expect(getMyNotificationSettings).toHaveBeenCalledTimes(1);
  for (const label of [
    '모의입양 알림 켜기',
    '채팅 알림 끄기',
    '댓글 알림 켜기',
    'AI 매칭 알림 끄기',
    '입양 관련 알림 켜기',
  ]) {
    expect(screen.root.findByProps({ accessibilityLabel: label }).props.disabled).toBe(false);
  }
});

it('채팅 스위치를 바꾸면 채팅 필드만 저장하고 응답을 표시한다', async () => {
  await renderSettings();
  await act(async () =>
    screen.root.findByProps({ accessibilityLabel: '채팅 알림 끄기' }).props.onPress(),
  );
  expect(updateMyNotificationSettings).toHaveBeenCalledWith({ chatEnabled: false });
  expect(
    screen.root.findByProps({ accessibilityLabel: '채팅 알림 켜기' }).props.accessibilityState
      .checked,
  ).toBe(false);
  expect(
    screen.root.findByProps({ accessibilityLabel: 'AI 매칭 알림 끄기' }).props.accessibilityState
      .checked,
  ).toBe(true);
});

it('OS 권한 거절 시 5종 모두 꺼진 상태로 표시한다', async () => {
  jest.mocked(readAppNotificationPermission).mockResolvedValue(false);
  await renderSettings();
  for (const label of [
    '모의입양 알림 켜기',
    '채팅 알림 켜기',
    '댓글 알림 켜기',
    'AI 매칭 알림 켜기',
    '입양 관련 알림 켜기',
  ]) {
    expect(screen.root.findByProps({ accessibilityLabel: label }).props.accessibilityState).toEqual(
      { checked: false, disabled: true },
    );
  }
});

it('변경 실패 시 기존 스위치 값을 유지하고 오류를 알린다', async () => {
  const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => {});
  jest.mocked(updateMyNotificationSettings).mockRejectedValueOnce(new Error('network'));
  await renderSettings();
  await act(async () =>
    screen.root.findByProps({ accessibilityLabel: '채팅 알림 끄기' }).props.onPress(),
  );
  expect(
    screen.root.findByProps({ accessibilityLabel: '채팅 알림 끄기' }).props.accessibilityState
      .checked,
  ).toBe(true);
  expect(alert).toHaveBeenCalled();
});

it('조회 실패 시 변경을 막고 재시도로 서버 설정을 표시한다', async () => {
  jest.mocked(getMyNotificationSettings).mockRejectedValueOnce(new Error('network'));
  await renderSettings();
  expect(screen.root.findByProps({ accessibilityLabel: '채팅 알림 켜기' }).props.disabled).toBe(
    true,
  );
  await act(async () =>
    screen.root.findByProps({ accessibilityLabel: '알림 설정 다시 불러오기' }).props.onPress(),
  );
  expect(getMyNotificationSettings).toHaveBeenCalledTimes(2);
  expect(screen.root.findByProps({ accessibilityLabel: '채팅 알림 끄기' }).props.disabled).toBe(
    false,
  );
});

it('저장 중 연속 클릭은 한 번만 전송한다', async () => {
  let completeSave!: (settings: typeof serverSettings) => void;
  jest.mocked(updateMyNotificationSettings).mockReturnValueOnce(
    new Promise(resolve => {
      completeSave = resolve;
    }),
  );
  await renderSettings();
  const toggle = screen.root.findByProps({ accessibilityLabel: '채팅 알림 끄기' }).props.onPress;
  await act(async () => {
    toggle();
    toggle();
  });
  expect(updateMyNotificationSettings).toHaveBeenCalledTimes(1);
  expect(screen.root.findByProps({ accessibilityLabel: '채팅 알림 끄기' }).props.disabled).toBe(
    true,
  );
  await act(async () => completeSave({ ...serverSettings, chatEnabled: false }));
  expect(screen.root.findByProps({ accessibilityLabel: '채팅 알림 켜기' }).props.disabled).toBe(
    false,
  );
});
