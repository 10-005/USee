jest.mock('./foundReportApi', () => ({
  getFoundReport: jest.fn(),
  searchFoundReports: jest.fn(),
}));

import { getFoundReport, searchFoundReports } from './foundReportApi';
import { getMapFoundReports } from './mapReports';

const mockSearch = searchFoundReports as jest.Mock;
const mockGetDetail = getFoundReport as jest.Mock;

const summary = {
  age: null,
  address: '경상북도 구미시 고아읍',
  classification: null,
  districtCode: '47190',
  foundReportSeq: 210,
  gender: 'UNKNOWN',
  isNeutered: null,
  occurredDttm: '2026-10-29T10:00:00+09:00',
  regDttm: '2026-10-29T12:00:00+09:00',
  representativeImageUrl: null,
  shelterSeq: 7,
  status: 'ACTIVE',
  weight: null,
};

const detail = {
  ...summary,
  closedDttm: null,
  closedReason: null,
  commentCount: 0,
  description: '논길에서 발견',
  images: [],
  isBookmarked: false,
  isPhonePublic: false,
  landmarkDescription: null,
  latitude: 36.1,
  longitude: 128.42,
  microchipNumber: null,
  reporter: null,
  reporterPhone: null,
  shelter: null,
};

describe('getMapFoundReports', () => {
  beforeEach(() => {
    mockSearch.mockResolvedValue({ content: [summary] });
    mockGetDetail.mockResolvedValue(detail);
  });

  it('지역 키워드에 맞는 신고만 반환한다', async () => {
    const reports = await getMapFoundReports({ keyword: '고아읍' });

    expect(reports).toHaveLength(1);
    expect(reports[0].id).toBe('210');
  });

  it('지도 영역 안의 신고만 반환한다', async () => {
    const reports = await getMapFoundReports({
      bounds: {
        maxLatitude: 35,
        maxLongitude: 128.44,
        minLatitude: 34,
        minLongitude: 128.4,
      },
    });

    expect(reports).toEqual([]);
  });
});
