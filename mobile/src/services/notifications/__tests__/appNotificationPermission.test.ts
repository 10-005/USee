import { PermissionsAndroid, Platform } from 'react-native';

import { readAppNotificationPermission } from '../appNotificationPermission';

afterEach(() => {
  jest.restoreAllMocks();
});

it('Android 13 이상에서는 알림 권한 허용 여부를 확인한다', async () => {
  jest.replaceProperty(Platform, 'OS', 'android');
  jest.spyOn(Platform, 'Version', 'get').mockReturnValue(33);
  const check = jest.spyOn(PermissionsAndroid, 'check').mockResolvedValue(true);

  await expect(readAppNotificationPermission()).resolves.toBe(true);
  expect(check).toHaveBeenCalledWith(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
});

it('Android 13 이상에서 권한이 거부되면 차단으로 본다', async () => {
  jest.replaceProperty(Platform, 'OS', 'android');
  jest.spyOn(Platform, 'Version', 'get').mockReturnValue(34);
  jest.spyOn(PermissionsAndroid, 'check').mockResolvedValue(false);

  await expect(readAppNotificationPermission()).resolves.toBe(false);
});

it('Android 13 미만은 알림이 기본 허용이다', async () => {
  jest.replaceProperty(Platform, 'OS', 'android');
  jest.spyOn(Platform, 'Version', 'get').mockReturnValue(32);
  const check = jest.spyOn(PermissionsAndroid, 'check');

  await expect(readAppNotificationPermission()).resolves.toBe(true);
  expect(check).not.toHaveBeenCalled();
});

it('iOS는 권한을 조회하지 않는다', async () => {
  jest.replaceProperty(Platform, 'OS', 'ios');
  const check = jest.spyOn(PermissionsAndroid, 'check');

  await expect(readAppNotificationPermission()).resolves.toBeNull();
  expect(check).not.toHaveBeenCalled();
});
