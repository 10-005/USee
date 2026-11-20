jest.mock('../../api/client', () => ({
  apiClient: { get: jest.fn() },
}));

import { apiClient } from '../../api/client';
import { getDistricts } from '../catalogApi';

const mockGet = apiClient.get as jest.Mock;

describe('catalogApi', () => {
  beforeEach(() => {
    mockGet.mockReset();
  });

  it('전체 지역 기준정보를 조회한다', async () => {
    const districts = [{ districtCode: '11680', sidoName: '서울특별시', sigunguName: '강남구' }];
    mockGet.mockResolvedValue({ data: { data: districts } });

    await expect(getDistricts()).resolves.toEqual(districts);
    expect(mockGet).toHaveBeenCalledWith('/districts', { params: undefined });
  });

  it('시도 이름으로 지역 기준정보를 조회한다', async () => {
    mockGet.mockResolvedValue({ data: { data: [] } });

    await getDistricts('서울특별시');
    expect(mockGet).toHaveBeenCalledWith('/districts', {
      params: { sidoName: '서울특별시' },
    });
  });
});
