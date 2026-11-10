jest.mock('../../api/client', () => ({
  notificationClient: { post: jest.fn() },
}));

import { notificationClient } from '../../api/client';
import { registerDeviceToken } from '../deviceTokenApi';

const mockPost = notificationClient.post as jest.Mock;

describe('registerDeviceToken', () => {
  beforeEach(() => {
    mockPost.mockReset();
  });

  it('FCM 기기 토큰을 등록하고 활성 여부를 반환한다', async () => {
    mockPost.mockResolvedValue({ data: { data: { active: true } } });

    await expect(registerDeviceToken('fcm-device-token')).resolves.toEqual({ active: true });
    expect(mockPost).toHaveBeenCalledWith('/notifications/device-tokens', {
      fcmToken: 'fcm-device-token',
    });
  });
});
