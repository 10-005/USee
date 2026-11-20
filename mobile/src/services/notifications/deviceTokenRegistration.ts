import { getAccessToken } from '../auth/authSession';
import { deactivateDeviceToken, registerDeviceToken } from './deviceTokenApi';

export async function registerDeviceTokenIfSignedIn(
  fcmToken: string | null | undefined,
): Promise<void> {
  if (!fcmToken?.trim() || !getAccessToken()) {
    return;
  }
  await registerDeviceToken(fcmToken);
}

export async function deactivateDeviceTokenIfSignedIn(
  fcmToken: string | null | undefined,
): Promise<void> {
  if (!fcmToken?.trim() || !getAccessToken()) {
    return;
  }
  await deactivateDeviceToken(fcmToken);
}
