import { PermissionsAndroid, Platform } from 'react-native';
import {
  getInitialNotification,
  getToken,
  onMessage,
  onNotificationOpenedApp,
  onTokenRefresh,
  setBackgroundMessageHandler,
} from '@react-native-firebase/messaging';
import {
  getStoredFcmToken,
  registerBackgroundMessaging,
  startForegroundMessaging,
} from '../firebaseMessaging';
import { handleRemoteMessage } from '../remoteMessage';

jest.mock('../remoteMessage', () => ({
  handleRemoteMessage: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../notificationSettings', () => ({
  initializeNotificationPermission: jest.fn().mockResolvedValue(undefined),
}));

afterEach(() => jest.restoreAllMocks());

it('백그라운드 및 포그라운드 수신기를 등록하고 구독을 해제한다', async () => {
  jest.replaceProperty(Platform, 'OS', 'android');
  jest.spyOn(Platform, 'Version', 'get').mockReturnValue(33);
  jest.spyOn(PermissionsAndroid, 'request').mockResolvedValue(PermissionsAndroid.RESULTS.DENIED);
  registerBackgroundMessaging();
  expect(setBackgroundMessageHandler).toHaveBeenCalledWith(expect.anything(), expect.any(Function));
  const stop = startForegroundMessaging();
  for (let i = 0; i < 10; i++) {
    await Promise.resolve();
  }
  expect(getToken).toHaveBeenCalled();
  expect(await getStoredFcmToken()).toBe('test-fcm-token');
  const refresh = jest.mocked(onTokenRefresh).mock.calls.at(-1)![1];
  await refresh('renewed-token');
  expect(await getStoredFcmToken()).toBe('renewed-token');
  stop();
  for (const listener of [onMessage, onNotificationOpenedApp, onTokenRefresh]) {
    expect(jest.mocked(listener).mock.results.at(-1)!.value).toHaveBeenCalledTimes(1);
  }
});

it('포그라운드 수신은 시스템 알림을 표시하고 알림을 눌러 열 때는 재표시하지 않는다', async () => {
  jest.replaceProperty(Platform, 'OS', 'android');
  jest.spyOn(Platform, 'Version', 'get').mockReturnValue(32);
  const message = {
    data: {},
    messageId: 'foreground',
    notification: { title: '제목', body: '본문' },
  };
  jest.mocked(getInitialNotification).mockResolvedValueOnce(message);

  const stop = startForegroundMessaging();
  await Promise.resolve();
  expect(handleRemoteMessage).toHaveBeenCalledWith(message, { displayNotification: false });

  jest.mocked(handleRemoteMessage).mockClear();
  await jest.mocked(onMessage).mock.calls.at(-1)![1](message);
  expect(handleRemoteMessage).toHaveBeenCalledWith(message);

  jest.mocked(handleRemoteMessage).mockClear();
  jest.mocked(onNotificationOpenedApp).mock.calls.at(-1)![1](message);
  expect(handleRemoteMessage).toHaveBeenCalledWith(message, { displayNotification: false });
  stop();
});

it('백그라운드에서는 FCM 자동 알림과 중복하지 않고 데이터 메시지만 직접 표시한다', async () => {
  jest.replaceProperty(Platform, 'OS', 'android');
  registerBackgroundMessaging();
  const receive = jest.mocked(setBackgroundMessageHandler).mock.calls.at(-1)![1];
  const notificationMessage = { data: {}, notification: { title: '제목', body: '본문' } };
  const dataMessage = { data: { title: '제목', body: '본문' } };

  await receive(notificationMessage);
  expect(handleRemoteMessage).toHaveBeenCalledWith(notificationMessage, {
    displayNotification: false,
  });
  await receive(dataMessage);
  expect(handleRemoteMessage).toHaveBeenCalledWith(dataMessage, { displayNotification: true });
});
