import { create } from 'zustand';

export type AdoptionApplicant = {
  birthDate: string;
  name: string;
  phone: string;
};

type AdoptionApplicantState = {
  draft: AdoptionApplicant;
  result: AdoptionApplicant & { id: number };
  beginEditing: (applicant: AdoptionApplicant) => void;
  save: (applicant: AdoptionApplicant) => void;
};

const emptyApplicant: AdoptionApplicant = { birthDate: '', name: '', phone: '' };

export const useAdoptionApplicantStore = create<AdoptionApplicantState>(set => ({
  beginEditing: applicant => set({ draft: applicant }),
  draft: emptyApplicant,
  result: { ...emptyApplicant, id: 0 },
  save: applicant =>
    set(state => ({ result: { ...applicant, id: state.result.id + 1 } })),
}));
