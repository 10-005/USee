import { NativeModules, Platform } from 'react-native';

type PushBannerModule = {
  show: (title: string, body: string) => Promise<void>;
};

export function showPushBanner(title: string, body: string): Promise<void> {
  if (Platform.OS !== 'android') {
    return Promise.resolve();
  }
  const banner = NativeModules.PushBanner as PushBannerModule | undefined;
  if (!banner?.show) {
    return Promise.resolve();
  }
  return banner.show(title, body);
}
