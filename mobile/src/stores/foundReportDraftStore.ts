import type { Asset } from 'react-native-image-picker';
import { create } from 'zustand';

export type FoundReportDraft = {
  age: string;
  breed: string;
  color: string;
  combinationSeq: number | null;
  contact: string;
  description: string;
  exposeContact: boolean;
  foundAt: string;
  foundSituation: string;
  gender: 'female' | 'male' | 'unknown' | null;
  images: Asset[];
  imageSeqs: number[];
  pendingImageKeys?: Record<string, string>;
  latitude: number | null;
  location: string;
  longitude: number | null;
  microchipNumber: string;
  microchipStatus: 'done' | 'not-done' | null;
  neutered: 'done' | 'not-done' | 'unknown' | null;
  species: string;
  terrain: string;
  weight: string;
};

const createDraft = (): FoundReportDraft => ({
  age: '',
  breed: '',
  color: '',
  combinationSeq: null,
  contact: '',
  description: '',
  exposeContact: false,
  foundAt: '',
  foundSituation: '',
  gender: null,
  images: [],
  imageSeqs: [],
  latitude: null,
  location: '',
  longitude: null,
  microchipNumber: '',
  microchipStatus: null,
  neutered: null,
  species: '',
  terrain: '',
  weight: '',
});

type FoundReportDraftState = {
  draft: FoundReportDraft;
  resetDraft: () => void;
  updateDraft: (values: Partial<FoundReportDraft>) => void;
};

export const useFoundReportDraftStore = create<FoundReportDraftState>(set => ({
  draft: createDraft(),
  resetDraft: () => set({ draft: createDraft() }),
  updateDraft: values => set(state => ({ draft: { ...state.draft, ...values } })),
}));
