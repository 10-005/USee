import { Linking, Platform } from 'react-native';
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
import { registerDeviceTokenIfSignedIn } from './deviceTokenRegistration';
import { handleRemoteMessage } from './remoteMessage';
import { initializeNotificationPermission } from './notificationSettings';
import { handleNotificationOpened, handleNotificationURL } from './notificationOpen';

const reportError = () => console.warn('[FCM] 알림 초기화 또는 저장에 실패했습니다.');
const saveToken = async (token: string) => {
  await setStoredValue(STORAGE_KEYS.FCM_TOKEN, token);
  await registerDeviceTokenIfSignedIn(token);
};

export const getStoredFcmToken = () => getStoredValue<string>(STORAGE_KEYS.FCM_TOKEN);

export function registerBackgroundMessaging() {
  if (Platform.OS !== 'android') {
    return;
  }
  setBackgroundMessageHandler(getMessaging(), message =>
    // FCM already displays notification payloads while the app is in the background.
    handleRemoteMessage(message, { displayNotification: !message.notification }),
  );
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
    handleNotificationOpened(message);
    handleRemoteMessage(message, { displayNotification: false }).catch(reportError);
  });
  const stopToken = onTokenRefresh(messaging, token => saveToken(token).catch(reportError));
  getInitialNotification(messaging)
    .then(message => {
      if (message) {
        handleNotificationOpened(message);
        return handleRemoteMessage(message, { displayNotification: false });
      }
    })
    .catch(reportError);
  const urlSubscription = Linking.addEventListener('url', ({ url }) => handleNotificationURL(url));
  Linking.getInitialURL().then(handleNotificationURL).catch(reportError);

  async function initialize() {
    await initializeNotificationPermission().catch(reportError);
    await saveToken(await getToken(messaging));
  }
  initialize().catch(reportError);
  return () => {
    stopMessage();
    stopOpened();
    stopToken();
    urlSubscription.remove();
  };
}
