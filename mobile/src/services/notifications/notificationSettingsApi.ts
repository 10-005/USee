import type { ApiResponse } from '../api';
import { notificationClient } from '../api/client';

export type NotificationSettings = {
  simulationEnabled: boolean;
  chatEnabled: boolean;
  commentEnabled: boolean;
  aiMatchEnabled: boolean;
  adoptionEnabled: boolean;
  quietEnabled: boolean;
  quietStart: string;
  quietEnd: string;
};

export async function getMyNotificationSettings(): Promise<NotificationSettings> {
  const response = await notificationClient.get<ApiResponse<NotificationSettings>>(
    '/notifications/settings',
  );
  return response.data.data;
}

export async function updateMyNotificationSettings(
  changes: Partial<NotificationSettings>,
): Promise<NotificationSettings> {
  const response = await notificationClient.patch<ApiResponse<NotificationSettings>>(
    '/notifications/settings',
    changes,
  );
  return response.data.data;
}
