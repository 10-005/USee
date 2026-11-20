jest.mock('../../api/client', () => ({
  apiClient: { delete: jest.fn(), get: jest.fn(), patch: jest.fn(), post: jest.fn() },
}));

import { apiClient } from '../../api/client';
import {
  changeFoundReportStatus,
  createFoundReport,
  deleteFoundReport,
  getFoundReport,
  getMyFoundReports,
  searchFoundReports,
  updateFoundReport,
  type CreateFoundReportRequest,
} from '../foundReportApi';

const mockDelete = apiClient.delete as jest.Mock;
const mockGet = apiClient.get as jest.Mock;
const mockPatch = apiClient.patch as jest.Mock;
const mockPost = apiClient.post as jest.Mock;

describe('foundReportApi', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('발견 신고 목록·내 목록·상세를 조회한다', async () => {
    mockGet.mockResolvedValue({ data: { data: [] } });

    await searchFoundReports({ status: 'ACTIVE' });
    await getMyFoundReports();
    await getFoundReport(210);

    expect(mockGet).toHaveBeenNthCalledWith(1, '/found-reports', {
      params: { page: 0, size: 100, sort: 'regDttm,desc', status: 'ACTIVE' },
    });
    expect(mockGet).toHaveBeenNthCalledWith(2, '/found-reports/me');
    expect(mockGet).toHaveBeenNthCalledWith(3, '/found-reports/210');
  });

  it('발견 신고를 등록·수정·종료·삭제한다', async () => {
    const request: CreateFoundReportRequest = {
      address: '대구광역시 중구 동인동1가',
      age: null,
      combinationSeq: 88,
      description: '빨간 목줄',
      gender: 'UNKNOWN',
      imageSeqs: [1201],
      isNeutered: null,
      isPhonePublic: false,
      landmarkDescription: '공원 정문 앞',
      latitude: 35.87,
      longitude: 128.6,
      microchipNumber: null,
      occurredDttm: '2026-10-29T10:00:00+09:00',
      reporterPhone: '01012345678',
      weight: '약 3kg',
    };
    mockPost.mockResolvedValue({ data: { data: { foundReportSeq: 210, status: 'ACTIVE' } } });
    mockPatch.mockResolvedValue({ data: { data: { foundReportSeq: 210 } } });
    mockDelete.mockResolvedValue({ status: 204 });

    await createFoundReport(request);
    await updateFoundReport(210, request);
    await changeFoundReportStatus(210, 'CLOSED', '보호자에게 인계');
    await deleteFoundReport(210);

    expect(mockPost).toHaveBeenCalledWith('/found-reports', request);
    expect(mockPatch).toHaveBeenNthCalledWith(1, '/found-reports/210', request);
    expect(mockPatch).toHaveBeenNthCalledWith(2, '/found-reports/210/status', {
      closedReason: '보호자에게 인계',
      status: 'CLOSED',
    });
    expect(mockDelete).toHaveBeenCalledWith('/found-reports/210');
  });
});
