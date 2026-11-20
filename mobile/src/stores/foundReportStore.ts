import type { ImageSourcePropType } from 'react-native';
import { create } from 'zustand';

import type { FoundReport } from '../screens/found-report/data';
import { formatKoreanAddress } from '../utils/address';
import { formatReportDateTime } from '../utils/dateTime';
import { getApiErrorDisplayMessage } from '../services/api';
import {
  changeFoundReportStatus,
  deleteFoundReport,
  getFoundReport,
  getMyFoundReports,
  searchFoundReports,
  type FoundReportDetail,
  type FoundReportSearchParams,
  type FoundReportSummary,
} from '../services/foundReports/foundReportApi';

type FoundReportState = {
  deleteReport: (reportId: string) => Promise<void>;
  detailsById: Record<string, FoundReportDetail>;
  error: string | null;
  fetchReport: (reportId: string) => Promise<void>;
  fetchReports: (params?: FoundReportSearchParams) => Promise<void>;
  isDetailLoading: boolean;
  isLoading: boolean;
  reportById: Record<string, FoundReport>;
  reports: FoundReport[];
  setStatus: (reportId: string, status: 'active' | 'completed') => Promise<void>;
};

const imageSources = (urls: Array<string | null>): ImageSourcePropType[] =>
  urls.filter((url): url is string => Boolean(url)).map(uri => ({ uri }));

const genderLabel = (gender: FoundReportSummary['gender']): FoundReport['gender'] =>
  gender === 'MALE' ? '수컷' : gender === 'FEMALE' ? '암컷' : '모름';

const speciesLabel = (speciesCode?: string): FoundReport['species'] =>
  speciesCode === 'DOG' ? '개' : speciesCode === 'CAT' ? '고양이' : '기타';

export const mapFoundSummary = (report: FoundReportSummary, isMine: boolean): FoundReport => ({
  age: report.age ?? '모름',
  color: report.classification?.colorName ?? '',
  date: (report.regDttm ?? report.occurredDttm).slice(0, 10),
  description: '',
  gender: genderLabel(report.gender),
  id: String(report.foundReportSeq),
  images: imageSources([report.representativeImageUrl]),
  isMine,
  latitude: 0,
  location: formatKoreanAddress(report.address),
  longitude: 0,
  name: report.classification?.breedName ?? '발견 동물',
  neutered: report.isNeutered,
  species: speciesLabel(report.classification?.speciesCode),
  status: report.status === 'ACTIVE' ? '진행 중' : '완료',
  weight: report.weight ?? '모름',
});

export const mapFoundDetail = (report: FoundReportDetail, isMine: boolean): FoundReport => ({
  ...mapFoundSummary(report, isMine),
  contact: report.reporterPhone ?? undefined,
  date: formatReportDateTime(report.regDttm ?? report.occurredDttm),
  description: report.description ?? '',
  images: imageSources(report.images.map(image => image.imageUrl)),
  latitude: report.latitude,
  longitude: report.longitude,
});

export const useFoundReportStore = create<FoundReportState>(set => ({
  deleteReport: async reportId => {
    await deleteFoundReport(Number(reportId));
    set(state => ({
      detailsById: Object.fromEntries(
        Object.entries(state.detailsById).filter(([id]) => id !== reportId),
      ),
      reports: state.reports.filter(report => report.id !== reportId),
      reportById: Object.fromEntries(
        Object.entries(state.reportById).filter(([id]) => id !== reportId),
      ),
    }));
  },
  detailsById: {},
  error: null,
  fetchReport: async reportId => {
    set({ error: null, isDetailLoading: true });
    try {
      const [detail, mine] = await Promise.all([
        getFoundReport(Number(reportId)),
        getMyFoundReports(),
      ]);
      const isMine = mine.some(report => String(report.foundReportSeq) === reportId);
      set(state => ({
        detailsById: { ...state.detailsById, [reportId]: detail },
        reportById: { ...state.reportById, [reportId]: mapFoundDetail(detail, isMine) },
      }));
    } catch (error) {
      set({ error: getApiErrorDisplayMessage(error, '발견 신고를 불러오지 못했어요.') });
    } finally {
      set({ isDetailLoading: false });
    }
  },
  fetchReports: async params => {
    set({ error: null, isLoading: true });
    try {
      const [page, mine] = await Promise.all([searchFoundReports(params), getMyFoundReports()]);
      const mineIds = new Set(mine.map(report => report.foundReportSeq));
      set({
        reports: page.content.map(report =>
          mapFoundSummary(report, mineIds.has(report.foundReportSeq)),
        ),
      });
    } catch (error) {
      set({ error: getApiErrorDisplayMessage(error, '발견 신고 목록을 불러오지 못했어요.') });
    } finally {
      set({ isLoading: false });
    }
  },
  isDetailLoading: false,
  isLoading: false,
  reportById: {},
  reports: [],
  setStatus: async (reportId, status) => {
    await changeFoundReportStatus(Number(reportId), status === 'active' ? 'ACTIVE' : 'CLOSED');
    set(state => {
      const nextStatus = status === 'active' ? '진행 중' : '완료';
      return {
        reports: state.reports.map(report =>
          report.id === reportId ? { ...report, status: nextStatus } : report,
        ),
        reportById: state.reportById[reportId]
          ? {
              ...state.reportById,
              [reportId]: { ...state.reportById[reportId], status: nextStatus },
            }
          : state.reportById,
      };
    });
  },
}));
