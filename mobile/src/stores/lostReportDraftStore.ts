import type { ImageSourcePropType } from 'react-native';
import { create } from 'zustand';

import type { Pet } from './petStore';

type LostReportEntryMode = 'manual' | 'pet' | null;

export type LostReportAnimalDraft = {
  age: number | null;
  breed: string;
  color: string;
  combinationSeq: number | null;
  description: string;
  gender: Pet['gender'] | null;
  images: ImageSourcePropType[];
  imageSeqs: number[];
  microchipNumber: string;
  name: string;
  neutered: boolean | null;
  sourcePetId: string | null;
  species: string;
  weight: number | null;
};

export type LostReportDetailsDraft = {
  contact: string;
  detailAddress: string;
  exposeContact: boolean;
  latitude: number | null;
  location: string;
  longitude: number | null;
  lostAt: string;
};

const createEmptyAnimalDraft = (): LostReportAnimalDraft => ({
  age: null,
  breed: '',
  color: '',
  combinationSeq: null,
  description: '',
  gender: null,
  images: [],
  imageSeqs: [],
  microchipNumber: '',
  name: '',
  neutered: null,
  sourcePetId: null,
  species: '',
  weight: null,
});

const createEmptyDetailsDraft = (): LostReportDetailsDraft => ({
  contact: '',
  detailAddress: '',
  exposeContact: false,
  latitude: null,
  location: '',
  longitude: null,
  lostAt: '',
});

const createAnimalDraftFromPet = (pet: Pet): LostReportAnimalDraft => ({
  age: pet.age,
  breed: pet.breed,
  color: pet.color,
  combinationSeq: pet.combinationSeq ?? null,
  description: pet.description,
  gender: pet.gender,
  images: [...pet.images],
  imageSeqs: [...(pet.imageSeqs ?? [])],
  microchipNumber: pet.microchipNumber,
  name: pet.name,
  neutered: pet.neutered,
  sourcePetId: pet.id,
  species: pet.species,
  weight: pet.weight,
});

type LostReportDraftState = {
  animal: LostReportAnimalDraft;
  details: LostReportDetailsDraft;
  entryMode: LostReportEntryMode;
  resetDraft: () => void;
  resetSelection: () => void;
  selectManual: () => void;
  selectPet: (pet: Pet) => void;
  selectedPetId: string | null;
  updateAnimal: (values: Partial<LostReportAnimalDraft>) => void;
  updateDetails: (values: Partial<LostReportDetailsDraft>) => void;
};

export const useLostReportDraftStore = create<LostReportDraftState>(set => ({
  animal: createEmptyAnimalDraft(),
  details: createEmptyDetailsDraft(),
  entryMode: null,
  resetDraft: () =>
    set({
      animal: createEmptyAnimalDraft(),
      details: createEmptyDetailsDraft(),
      entryMode: null,
      selectedPetId: null,
    }),
  resetSelection: () =>
    set({
      animal: createEmptyAnimalDraft(),
      details: createEmptyDetailsDraft(),
      entryMode: null,
      selectedPetId: null,
    }),
  selectManual: () =>
    set({ animal: createEmptyAnimalDraft(), entryMode: 'manual', selectedPetId: null }),
  selectPet: pet =>
    set({ animal: createAnimalDraftFromPet(pet), entryMode: 'pet', selectedPetId: pet.id }),
  selectedPetId: null,
  updateAnimal: values => set(state => ({ animal: { ...state.animal, ...values } })),
  updateDetails: values => set(state => ({ details: { ...state.details, ...values } })),
}));
