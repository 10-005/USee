import { create } from 'zustand';

import type { LostReport } from '../screens/lost-report/data';

type LostReportEditState = {
  deletedIds: string[];
  deleteReport: (reportId: string) => void;
  editsById: Record<string, Partial<LostReport>>;
  updateReport: (reportId: string, values: Partial<LostReport>) => void;
};

export const useLostReportEditStore = create<LostReportEditState>(set => ({
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
