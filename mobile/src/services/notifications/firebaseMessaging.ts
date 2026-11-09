import { PermissionsAndroid, Platform } from 'react-native';
import {
  getMessaging,
  getToken,
  getInitialNotification,
  onMessage,
  onNotificationOpenedApp,
  onTokenRefresh,
  setBackgroundMessageHandler,
} from '@react-native-firebase/messaging';
import { STORAGE_KEYS } from '../../constants/storageKeys';
import { getStoredValue, setStoredValue } from '../storage/jsonStorage';
import { handleRemoteMessage } from './remoteMessage';

const reportError = () => console.warn('[FCM] 알림 초기화 또는 저장에 실패했습니다.');
const saveToken = (token: string) => setStoredValue(STORAGE_KEYS.FCM_TOKEN, token);

// The backend can read this token when its device-registration API is connected.
export const getStoredFcmToken = () => getStoredValue<string>(STORAGE_KEYS.FCM_TOKEN);

export function registerBackgroundMessaging() {
  if (Platform.OS !== 'android') {
    return;
  }
  setBackgroundMessageHandler(getMessaging(), handleRemoteMessage);
}

export function startForegroundMessaging(): () => void {
  // iOS requires its own Firebase plist and APNs setup before enabling messaging.
  if (Platform.OS !== 'android') {
    return () => {};
  }
  const messaging = getMessaging();
  const stopMessage = onMessage(messaging, message =>
    handleRemoteMessage(message).catch(reportError),
  );
  const stopOpened = onNotificationOpenedApp(messaging, message => {
    handleRemoteMessage(message).catch(reportError);
  });
  const stopToken = onTokenRefresh(messaging, token => saveToken(token).catch(reportError));
  getInitialNotification(messaging)
    .then(message => message && handleRemoteMessage(message))
    .catch(reportError);

  async function initialize() {
    if (Number(Platform.Version) >= 33) {
      await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
    }
    await saveToken(await getToken(messaging));
  }
  initialize().catch(reportError);
  return () => {
    stopMessage();
    stopOpened();
    stopToken();
  };
}
