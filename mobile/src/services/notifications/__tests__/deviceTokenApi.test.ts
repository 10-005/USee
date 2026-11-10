jest.mock('../../api/client', () => ({
  notificationClient: { delete: jest.fn(), post: jest.fn() },
}));

import { notificationClient } from '../../api/client';
import { deactivateDeviceToken, registerDeviceToken } from '../deviceTokenApi';

const mockDelete = notificationClient.delete as jest.Mock;
const mockPost = notificationClient.post as jest.Mock;

describe('registerDeviceToken', () => {
  beforeEach(() => {
    mockDelete.mockReset();
    mockPost.mockReset();
  });

  it('FCM 기기 토큰을 등록하고 활성 여부를 반환한다', async () => {
    mockPost.mockResolvedValue({ data: { data: { active: true } } });

    await expect(registerDeviceToken('fcm-device-token')).resolves.toEqual({ active: true });
    expect(mockPost).toHaveBeenCalledWith('/notifications/device-tokens', {
      fcmToken: 'fcm-device-token',
    });
  });

  it('FCM 기기 토큰을 해제한다', async () => {
    mockDelete.mockResolvedValue({ status: 204 });

    await expect(deactivateDeviceToken('fcm-device-token')).resolves.toBeUndefined();
    expect(mockDelete).toHaveBeenCalledWith('/notifications/device-tokens/current', {
      data: { fcmToken: 'fcm-device-token' },
    });
  });
});
