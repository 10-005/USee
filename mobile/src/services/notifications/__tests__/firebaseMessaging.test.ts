import { PermissionsAndroid, Platform } from 'react-native';
import {
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

afterEach(() => jest.restoreAllMocks());

it('백그라운드 및 포그라운드 수신기를 등록하고 구독을 해제한다', async () => {
  jest.replaceProperty(Platform, 'OS', 'android');
  jest.spyOn(Platform, 'Version', 'get').mockReturnValue(33);
  jest.spyOn(PermissionsAndroid, 'request').mockResolvedValue(PermissionsAndroid.RESULTS.DENIED);
  registerBackgroundMessaging();
  expect(setBackgroundMessageHandler).toHaveBeenCalledWith(expect.anything(), handleRemoteMessage);
  const stop = startForegroundMessaging();
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
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
