import type { ApiResponse } from '../api';
import { notificationClient } from '../api/client';

export type DeviceTokenResult = {
  active: boolean;
};

export async function registerDeviceToken(fcmToken: string): Promise<DeviceTokenResult> {
  const response = await notificationClient.post<ApiResponse<DeviceTokenResult>>(
    '/notifications/device-tokens',
    { fcmToken },
  );
  return response.data.data;
}

export async function deactivateDeviceToken(fcmToken: string): Promise<void> {
  await notificationClient.delete('/notifications/device-tokens/current', {
    data: { fcmToken },
  });
}
