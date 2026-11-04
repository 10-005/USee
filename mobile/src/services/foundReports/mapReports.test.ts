import { getMapFoundReports } from './mapReports';

describe('getMapFoundReports', () => {
  it('지역 키워드에 맞는 신고만 반환한다', async () => {
    const reports = await getMapFoundReports({ keyword: '고아읍' });

    expect(reports).toHaveLength(1);
    expect(reports[0].id).toBe('found-3');
  });

  it('지도 영역 안의 신고만 반환한다', async () => {
    const reports = await getMapFoundReports({
      bounds: {
        maxLatitude: 36.12,
        maxLongitude: 128.44,
        minLatitude: 36.09,
        minLongitude: 128.4,
      },
    });

    expect(reports.map(report => report.id)).toEqual(['found-1', 'found-2', 'found-4']);
  });
});
