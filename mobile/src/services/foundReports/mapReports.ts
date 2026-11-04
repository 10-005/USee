import { foundReports, type FoundReport } from '../../screens/found-report/data';

export type MapBounds = {
  maxLatitude: number;
  maxLongitude: number;
  minLatitude: number;
  minLongitude: number;
};

export type MapReportQuery = {
  bounds?: MapBounds;
  keyword?: string;
};

/**
 * 지도 조회 API의 프론트엔드 경계다. 서버 엔드포인트가 확정되면
 * 이 함수 내부만 API client 호출로 교체하고 화면은 그대로 유지한다.
 */
export async function getMapFoundReports(query: MapReportQuery = {}): Promise<FoundReport[]> {
  const keyword = query.keyword?.trim().toLowerCase() ?? '';

  return foundReports.filter(report => {
    const matchesKeyword =
      !keyword ||
      `${report.location} ${report.name} ${report.description}`.toLowerCase().includes(keyword);
    const matchesBounds =
      !query.bounds ||
      (report.latitude >= query.bounds.minLatitude &&
        report.latitude <= query.bounds.maxLatitude &&
        report.longitude >= query.bounds.minLongitude &&
        report.longitude <= query.bounds.maxLongitude);

    return matchesKeyword && matchesBounds;
  });
}
