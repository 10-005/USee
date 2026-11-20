jest.mock('../../api/client', () => ({
  apiClient: { delete: jest.fn(), get: jest.fn(), patch: jest.fn(), post: jest.fn() },
}));

import { apiClient } from '../../api/client';
import {
  changeMissingReportStatus,
  createMissingReport,
  deleteMissingReport,
  getMissingReport,
  getMyMissingReports,
  searchMissingReports,
  updateMissingReport,
} from '../missingReportApi';

const mockDelete = apiClient.delete as jest.Mock;
const mockGet = apiClient.get as jest.Mock;
const mockPatch = apiClient.patch as jest.Mock;
const mockPost = apiClient.post as jest.Mock;

describe('missingReportApi', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('실종 신고 목록·내 목록·상세를 조회한다', async () => {
    mockGet.mockResolvedValue({ data: { data: [] } });

    await searchMissingReports({ status: 'ACTIVE' });
    await getMyMissingReports();
    await getMissingReport(101);

    expect(mockGet).toHaveBeenNthCalledWith(1, '/missing-reports', {
      params: { page: 0, size: 100, sort: 'regDttm,desc', status: 'ACTIVE' },
    });
    expect(mockGet).toHaveBeenNthCalledWith(2, '/missing-reports/me');
    expect(mockGet).toHaveBeenNthCalledWith(3, '/missing-reports/101');
  });

  it('실종 신고를 등록·수정·종료·삭제한다', async () => {
    const request = {
      age: '3살',
      address: '대구광역시 중구',
      combinationSeq: 88,
      contactPhone: '01012345678',
      description: '파란 목줄',
      gender: 'MALE' as const,
      imageSeqs: [1001],
      isNeutered: true,
      isPhonePublic: true,
      latitude: 35.87,
      longitude: 128.6,
      microchipNumber: null,
      occurredDttm: '2026-10-28T18:00:00+09:00',
      weight: '4.5kg',
    };
    mockPost.mockResolvedValue({ data: { data: { missingReportSeq: 101 } } });
    mockPatch.mockResolvedValue({ data: { data: { missingReportSeq: 101 } } });
    mockDelete.mockResolvedValue({ status: 204 });

    await createMissingReport(request);
    await updateMissingReport(101, request);
    await changeMissingReportStatus(101, 'CLOSED');
    await deleteMissingReport(101);

    expect(mockPost).toHaveBeenCalledWith('/missing-reports', request);
    expect(mockPatch).toHaveBeenNthCalledWith(1, '/missing-reports/101', request);
    expect(mockPatch).toHaveBeenNthCalledWith(2, '/missing-reports/101/status', {
      status: 'CLOSED',
    });
    expect(mockDelete).toHaveBeenCalledWith('/missing-reports/101');
  });
});
