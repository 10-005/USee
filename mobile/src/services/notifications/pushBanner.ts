import { NativeModules, Platform } from 'react-native';
import type { RemoteMessage } from '@react-native-firebase/messaging';

type PushBannerModule = {
  show: (title: string, body: string, payload: string) => Promise<void>;
};

export function showPushBanner(
  title: string,
  body: string,
  message?: RemoteMessage,
): Promise<void> {
  if (Platform.OS !== 'android') {
    return Promise.resolve();
  }
  const banner = NativeModules.PushBanner as PushBannerModule | undefined;
  if (!banner?.show) {
    return Promise.resolve();
  }
  return banner.show(
    title,
    body,
    JSON.stringify({ data: message?.data, notification: { title, body } }),
  );
}
