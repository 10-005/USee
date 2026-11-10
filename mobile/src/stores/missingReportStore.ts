import type { ImageSourcePropType } from 'react-native';
import { create } from 'zustand';

import type { LostReport } from '../screens/lost-report/data';
import { getApiErrorDisplayMessage } from '../services/api';
import {
  changeMissingReportStatus,
  deleteMissingReport,
  getMissingReport,
  getMyMissingReports,
  searchMissingReports,
  type MissingReportDetail,
  type MissingReportSummary,
  type MissingReportSearchParams,
} from '../services/missingReports';

type MissingReportState = {
  deleteReport: (reportId: string) => Promise<void>;
  detailsById: Record<string, MissingReportDetail>;
  error: string | null;
  fetchReport: (reportId: string) => Promise<void>;
  fetchReports: (params?: MissingReportSearchParams) => Promise<void>;
  isDetailLoading: boolean;
  isLoading: boolean;
  reportById: Record<string, LostReport>;
  reports: LostReport[];
  setStatus: (reportId: string, status: 'active' | 'completed') => Promise<void>;
};

const imageSources = (urls: Array<string | null>): ImageSourcePropType[] =>
  urls.filter((url): url is string => Boolean(url)).map(uri => ({ uri }));

const genderLabel = (gender: MissingReportSummary['gender']): LostReport['gender'] =>
  gender === 'MALE' ? '수컷' : gender === 'FEMALE' ? '암컷' : '모름';

const speciesLabel = (speciesCode?: string): LostReport['species'] =>
  speciesCode === 'DOG' ? '개' : speciesCode === 'CAT' ? '고양이' : '기타';

export const mapMissingSummary = (report: MissingReportSummary, isMine: boolean): LostReport => ({
  age: report.age ?? '모름',
  color: report.classification?.colorName ?? '',
  date: report.occurredDttm.slice(0, 10),
  description: '',
  gender: genderLabel(report.gender),
  id: String(report.missingReportSeq),
  images: imageSources([report.representativeImageUrl]),
  isMine,
  location: report.address,
  name: report.classification?.breedName ?? '실종 동물',
  neutered: report.isNeutered,
  species: speciesLabel(report.classification?.speciesCode),
  status: report.status === 'ACTIVE' ? '실종' : '종료',
  weight: report.weight ?? '모름',
});

export const mapMissingDetail = (report: MissingReportDetail, isMine: boolean): LostReport => ({
  ...mapMissingSummary(report, isMine),
  contact: report.contactPhone ?? undefined,
  description: report.description ?? '',
  images: imageSources(report.images.map(image => image.imageUrl)),
});

export const useMissingReportStore = create<MissingReportState>(set => ({
  deleteReport: async reportId => {
    await deleteMissingReport(Number(reportId));
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
  error: null,
  detailsById: {},
  fetchReport: async reportId => {
    set({ error: null, isDetailLoading: true });
    try {
      const [detail, mine] = await Promise.all([
        getMissingReport(Number(reportId)),
        getMyMissingReports(),
      ]);
      const isMine = mine.some(report => String(report.missingReportSeq) === reportId);
      const mapped = mapMissingDetail(detail, isMine);
      set(state => ({
        detailsById: { ...state.detailsById, [reportId]: detail },
        reportById: { ...state.reportById, [reportId]: mapped },
      }));
    } catch (error) {
      set({ error: getApiErrorDisplayMessage(error, '실종 신고를 불러오지 못했어요.') });
    } finally {
      set({ isDetailLoading: false });
    }
  },
  fetchReports: async params => {
    set({ error: null, isLoading: true });
    try {
      const [page, mine] = await Promise.all([searchMissingReports(params), getMyMissingReports()]);
      const mineIds = new Set(mine.map(report => report.missingReportSeq));
      set({
        reports: page.content.map(report =>
          mapMissingSummary(report, mineIds.has(report.missingReportSeq)),
        ),
      });
    } catch (error) {
      set({ error: getApiErrorDisplayMessage(error, '실종 신고 목록을 불러오지 못했어요.') });
    } finally {
      set({ isLoading: false });
    }
  },
  isDetailLoading: false,
  isLoading: false,
  reportById: {},
  reports: [],
  setStatus: async (reportId, status) => {
    await changeMissingReportStatus(Number(reportId), status === 'active' ? 'ACTIVE' : 'CLOSED');
    set(state => {
      const nextStatus = status === 'active' ? '실종' : '종료';
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
