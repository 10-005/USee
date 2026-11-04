import { create } from 'zustand';
import { persist } from 'zustand/middleware';

import { createPersistStorage } from '../services/storage/persistStorage';

export type ContentReportTargetType = 'post' | 'comment' | 'reply' | 'user';

export type ContentReportRecord = {
  reason: string;
  targetId: string;
  targetType: ContentReportTargetType;
};

type ContentReportState = {
  reports: ContentReportRecord[];
  addReport: (report: ContentReportRecord) => void;
};

export const useContentReportStore = create<ContentReportState>()(
  persist(
    set => ({
      reports: [],
      addReport: report =>
        set(state => ({
          reports: state.reports.some(item => item.targetId === report.targetId)
            ? state.reports
            : [...state.reports, report],
        })),
    }),
    {
      name: 'usee-content-reports',
      storage: createPersistStorage<ContentReportState>(),
    },
  ),
);
