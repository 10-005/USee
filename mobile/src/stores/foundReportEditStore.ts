import { create } from 'zustand';

import type { FoundReport } from '../screens/found-report/data';

type FoundReportEditState = {
  deletedIds: string[];
  deleteReport: (reportId: string) => void;
  editsById: Record<string, Partial<FoundReport>>;
  updateReport: (reportId: string, values: Partial<FoundReport>) => void;
};

export const useFoundReportEditStore = create<FoundReportEditState>(set => ({
  deletedIds: [],
  deleteReport: reportId =>
    set(state => ({
      deletedIds: state.deletedIds.includes(reportId)
        ? state.deletedIds
        : [...state.deletedIds, reportId],
    })),
  editsById: {},
  updateReport: (reportId, values) =>
    set(state => ({
      editsById: {
        ...state.editsById,
        [reportId]: { ...state.editsById[reportId], ...values },
      },
    })),
}));
