jest.mock('../../api/client', () => ({
  apiClient: { get: jest.fn() },
}));

import { apiClient } from '../../api/client';
import { checkNicknameAvailability } from '../userApi';

const mockGet = apiClient.get as jest.Mock;

describe('userApi', () => {
  beforeEach(() => {
    mockGet.mockReset();
  });

  it('닉네임을 query parameter로 보내고 사용 가능 여부를 반환한다', async () => {
    const data = { available: true, nickname: '반려가족' };
    mockGet.mockResolvedValue({ data: { data } });

    await expect(checkNicknameAvailability('반려가족')).resolves.toEqual(data);
    expect(mockGet).toHaveBeenCalledWith('/users/nickname-availability', {
      params: { nickname: '반려가족' },
    });
  });
});
