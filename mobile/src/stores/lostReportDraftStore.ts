import type { ImageSourcePropType } from 'react-native';
import { create } from 'zustand';

import type { Pet } from './petStore';

type LostReportEntryMode = 'manual' | 'pet' | null;

export type LostReportAnimalDraft = {
  age: number | null;
  breed: string;
  color: string;
  description: string;
  gender: Pet['gender'] | null;
  images: ImageSourcePropType[];
  microchipNumber: string;
  name: string;
  neutered: boolean | null;
  sourcePetId: string | null;
  species: string;
  weight: number | null;
};

const createEmptyAnimalDraft = (): LostReportAnimalDraft => ({
  age: null,
  breed: '',
  color: '',
  description: '',
  gender: null,
  images: [],
  microchipNumber: '',
  name: '',
  neutered: null,
  sourcePetId: null,
  species: '',
  weight: null,
});

const createAnimalDraftFromPet = (pet: Pet): LostReportAnimalDraft => ({
  age: pet.age,
  breed: pet.breed,
  color: pet.color,
  description: pet.description,
  gender: pet.gender,
  images: [...pet.images],
  microchipNumber: pet.microchipNumber,
  name: pet.name,
  neutered: pet.neutered,
  sourcePetId: pet.id,
  species: pet.species,
  weight: pet.weight,
});

type LostReportDraftState = {
  animal: LostReportAnimalDraft;
  entryMode: LostReportEntryMode;
  resetSelection: () => void;
  selectManual: () => void;
  selectPet: (pet: Pet) => void;
  selectedPetId: string | null;
  updateAnimal: (values: Partial<LostReportAnimalDraft>) => void;
};

export const useLostReportDraftStore = create<LostReportDraftState>(set => ({
  animal: createEmptyAnimalDraft(),
  entryMode: null,
  resetSelection: () =>
    set({ animal: createEmptyAnimalDraft(), entryMode: null, selectedPetId: null }),
  selectManual: () =>
    set({ animal: createEmptyAnimalDraft(), entryMode: 'manual', selectedPetId: null }),
  selectPet: pet =>
    set({ animal: createAnimalDraftFromPet(pet), entryMode: 'pet', selectedPetId: pet.id }),
  selectedPetId: null,
  updateAnimal: values => set(state => ({ animal: { ...state.animal, ...values } })),
}));
