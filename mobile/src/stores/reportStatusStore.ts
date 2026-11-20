import { create } from 'zustand';
import { persist } from 'zustand/middleware';

import { createPersistStorage } from '../services/storage/persistStorage';

export type ReportProgressStatus = 'active' | 'completed';

type ReportStatusState = {
  statusById: Record<string, ReportProgressStatus>;
  setStatus: (reportId: string, status: ReportProgressStatus) => void;
};

export const useReportStatusStore = create<ReportStatusState>()(
  persist(
    set => ({
      statusById: {},
      setStatus: (reportId, status) =>
        set(state => ({
          statusById: { ...state.statusById, [reportId]: status },
        })),
    }),
    {
      name: 'usee-report-status',
      storage: createPersistStorage<ReportStatusState>(),
    },
  ),
);
