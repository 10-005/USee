jest.mock('../api/client', () => ({ apiClient: { get: jest.fn() } }));

import { apiClient } from '../api/client';
import { getCandidateAddress, searchGeocodeCandidates } from './geocode';

const mockGet = apiClient.get as jest.Mock;

describe('geocode', () => {
  it('도로명 주소를 우선 사용한다', () => {
    expect(
      getCandidateAddress({
        jibunAddress: '지번 주소',
        latitude: 1,
        longitude: 2,
        roadAddress: '도로명 주소',
      }),
    ).toBe('도로명 주소');
  });

  it('입력한 주소로 실서버 후보를 검색한다', async () => {
    const candidates = [
      {
        jibunAddress: '대구광역시 중구 동성로2가 1',
        latitude: 35.8714,
        longitude: 128.6014,
        roadAddress: '대구광역시 중구 동성로 1',
      },
    ];
    mockGet.mockResolvedValue({ data: { data: candidates } });
    const result = await searchGeocodeCandidates('  동성로   1  ');

    expect(mockGet).toHaveBeenCalledWith('/geocode', { params: { query: '동성로 1' } });
    expect(result).toEqual(candidates);
  });
});
