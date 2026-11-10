import { getAccessToken } from '../auth/authSession';
import { registerDeviceToken } from './deviceTokenApi';

export async function registerDeviceTokenIfSignedIn(
  fcmToken: string | null | undefined,
): Promise<void> {
  if (!fcmToken?.trim() || !getAccessToken()) {
    return;
  }
  await registerDeviceToken(fcmToken);
}
