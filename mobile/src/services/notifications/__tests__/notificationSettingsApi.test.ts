import { notificationClient } from '../../api/client';
import {
  getMyNotificationSettings,
  updateMyNotificationSettings,
} from '../notificationSettingsApi';

jest.mock('../../api/client', () => ({ notificationClient: { get: jest.fn(), patch: jest.fn() } }));

it('내 알림 설정을 조회한다', async () => {
  const settings = { chatEnabled: false };
  jest.mocked(notificationClient.get).mockResolvedValue({ data: { data: settings } });
  await expect(getMyNotificationSettings()).resolves.toEqual(settings);
  expect(notificationClient.get).toHaveBeenCalledWith('/notifications/settings');
});

it('개별 변경 필드를 PATCH하고 서버의 전체 설정을 반환한다', async () => {
  const settings = { chatEnabled: false, commentEnabled: true };
  jest.mocked(notificationClient.patch).mockResolvedValue({ data: { data: settings } });
  await expect(updateMyNotificationSettings({ chatEnabled: false })).resolves.toEqual(settings);
  expect(notificationClient.patch).toHaveBeenCalledWith('/notifications/settings', {
    chatEnabled: false,
  });
});
