jest.mock('../../api/client', () => ({
  apiClient: { get: jest.fn(), patch: jest.fn() },
}));

import { apiClient } from '../../api/client';
import { checkNicknameAvailability, getMyProfile, updateMyProfile } from '../userApi';

const mockGet = apiClient.get as jest.Mock;
const mockPatch = apiClient.patch as jest.Mock;

describe('userApi', () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPatch.mockReset();
  });

  it('내 프로필을 조회한다', async () => {
    const data = {
      accountStatus: 'ACTIVE',
      birthDate: '1997-05-12',
      name: '김가람',
      nickname: '반려가족',
      phone: '01012345678',
      profileImage: null,
      role: 'USER',
      userSeq: 17,
    };
    mockGet.mockResolvedValue({ data: { data } });

    await expect(getMyProfile()).resolves.toEqual(data);
    expect(mockGet).toHaveBeenCalledWith('/users/me');
  });

  it('변경한 필드로 내 프로필을 수정한다', async () => {
    const data = { nickname: '새닉네임' };
    mockPatch.mockResolvedValue({ data: { data } });

    await expect(updateMyProfile(data)).resolves.toEqual(data);
    expect(mockPatch).toHaveBeenCalledWith('/users/me', data);
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
