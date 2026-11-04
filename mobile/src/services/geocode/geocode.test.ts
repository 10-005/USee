import { getCandidateAddress, searchGeocodeCandidates } from './geocode';

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

  it('입력한 주소로 로컬 후보를 검색한다', async () => {
    const result = await searchGeocodeCandidates('동성로');
    expect(result[0]?.roadAddress).toBe('대구광역시 중구 동성로 1');
  });

  it('로컬 미리보기 데이터에서 장소명으로 검색한다', async () => {
    const result = await searchGeocodeCandidates('인동초');

    expect(result).toEqual([
      expect.objectContaining({ roadAddress: '경상북도 구미시 여헌로3길 34' }),
    ]);
  });
});
