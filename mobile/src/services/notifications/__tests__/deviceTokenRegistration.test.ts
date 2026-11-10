jest.mock('../../auth/authSession', () => ({
  getAccessToken: jest.fn(),
}));

jest.mock('../deviceTokenApi', () => ({
  registerDeviceToken: jest.fn(),
}));

import { getAccessToken } from '../../auth/authSession';
import { registerDeviceToken } from '../deviceTokenApi';
import { registerDeviceTokenIfSignedIn } from '../deviceTokenRegistration';

const mockGetAccessToken = getAccessToken as jest.Mock;
const mockRegisterDeviceToken = registerDeviceToken as jest.Mock;

describe('registerDeviceTokenIfSignedIn', () => {
  beforeEach(() => {
    mockGetAccessToken.mockReset();
    mockRegisterDeviceToken.mockReset();
  });

  it('로그인 세션과 토큰이 있으면 등록한다', async () => {
    mockGetAccessToken.mockReturnValue('access-token');
    mockRegisterDeviceToken.mockResolvedValue({ active: true });

    await registerDeviceTokenIfSignedIn('fcm-device-token');

    expect(mockRegisterDeviceToken).toHaveBeenCalledWith('fcm-device-token');
  });

  it('세션이 없거나 토큰이 비어 있으면 등록하지 않는다', async () => {
    mockGetAccessToken.mockReturnValue(null);
    await registerDeviceTokenIfSignedIn('fcm-device-token');

    mockGetAccessToken.mockReturnValue('access-token');
    await registerDeviceTokenIfSignedIn('  ');

    expect(mockRegisterDeviceToken).not.toHaveBeenCalled();
  });
});
