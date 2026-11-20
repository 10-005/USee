import { PermissionsAndroid, Platform } from 'react-native';

export async function readAppNotificationPermission(): Promise<boolean | null> {
  if (Platform.OS !== 'android') {
    return null;
  }
  if (Number(Platform.Version) < 33) {
    return true;
  }
  try {
    return await PermissionsAndroid.check(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
  } catch {
    return false;
  }
}
