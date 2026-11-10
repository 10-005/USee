import type { FoundReport } from '../../screens/found-report/data';
import { mapFoundDetail } from '../../stores/foundReportStore';
import { getFoundReport, searchFoundReports } from './foundReportApi';

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

export async function getMapFoundReports(query: MapReportQuery = {}): Promise<FoundReport[]> {
  const page = await searchFoundReports({ size: 100 });
  const details = await Promise.all(
    page.content.map(report => getFoundReport(report.foundReportSeq)),
  );
  const keyword = query.keyword?.trim().toLowerCase() ?? '';

  return details
    .map(report => mapFoundDetail(report, false))
    .filter(report => {
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
