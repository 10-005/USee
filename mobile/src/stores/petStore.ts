import type { ImageSourcePropType } from 'react-native';
import { create } from 'zustand';

import { getApiErrorDisplayMessage } from '../services/api';
import {
  deleteMyPet,
  getMyPet,
  getMyPets,
  type PetDetail,
  type PetGender,
  type PetListItem,
} from '../services/pets';

export type Pet = {
  age: number | null;
  breed: string;
  color: string;
  combinationSeq?: number;
  description: string;
  gender: '암컷' | '수컷' | '모름';
  id: string;
  image: ImageSourcePropType;
  imageSeqs?: number[];
  images: ImageSourcePropType[];
  microchipNumber: string;
  name: string;
  neutered: boolean | null;
  species: string;
  weight: number | null;
};

type PetState = {
  deletePet: (petId: string) => Promise<void>;
  error: string | null;
  fetchPet: (petId: string) => Promise<Pet | null>;
  fetchPets: () => Promise<void>;
  isDetailLoading: boolean;
  isLoading: boolean;
  pets: Pet[];
  resetPets: () => void;
  upsertPet: (pet: Pet) => void;
};

const genderLabel = (gender: PetGender): Pet['gender'] =>
  gender === 'MALE' ? '수컷' : gender === 'FEMALE' ? '암컷' : '모름';

const numericValue = (value: string | null | undefined): number | null => {
  if (!value) {
    return null;
  }
  const parsed = Number.parseFloat(value.replace(/[^0-9.]/g, ''));
  return Number.isFinite(parsed) ? parsed : null;
};

const imageSource = (url: string): ImageSourcePropType => ({ uri: url });

export const mapPetListItem = (pet: PetListItem): Pet => ({
  age: null,
  breed: pet.classification.breedName,
  color: pet.classification.colorName,
  combinationSeq: pet.classification.combinationSeq,
  description: '',
  gender: genderLabel(pet.gender),
  id: String(pet.petSeq),
  image: imageSource(pet.representativeImageUrl),
  imageSeqs: [],
  images: [imageSource(pet.representativeImageUrl)],
  microchipNumber: '',
  name: pet.name,
  neutered: null,
  species: pet.classification.speciesName,
  weight: null,
});

export const mapPetDetail = (pet: PetDetail): Pet => ({
  ...mapPetListItem(pet),
  age: numericValue(pet.age),
  description: pet.description ?? '',
  imageSeqs: pet.images.map(image => image.imageSeq),
  images: pet.images.map(image => imageSource(image.imageUrl)),
  microchipNumber: pet.microchipNumber ?? '',
  neutered: pet.isNeutered,
  weight: numericValue(pet.weight),
});

export const usePetStore = create<PetState>((set, get) => ({
  deletePet: async petId => {
    await deleteMyPet(Number(petId));
    set(state => ({ pets: state.pets.filter(pet => pet.id !== petId) }));
  },
  error: null,
  fetchPet: async petId => {
    set({ error: null, isDetailLoading: true });
    try {
      const pet = mapPetDetail(await getMyPet(Number(petId)));
      get().upsertPet(pet);
      return pet;
    } catch (error) {
      set({ error: getApiErrorDisplayMessage(error, '반려동물 정보를 불러오지 못했어요.') });
      return null;
    } finally {
      set({ isDetailLoading: false });
    }
  },
  fetchPets: async () => {
    set({ error: null, isLoading: true });
    try {
      const page = await getMyPets();
      const pets = await Promise.all(
        page.content.map(async item => {
          const pet = mapPetListItem(item);
          try {
            const detail = await getMyPet(item.petSeq);
            return { ...pet, age: numericValue(detail.age) };
          } catch {
            const cached = get().pets.find(previous => previous.id === pet.id);
            return { ...pet, age: cached?.age ?? null };
          }
        }),
      );
      set({ pets });
    } catch (error) {
      set({ error: getApiErrorDisplayMessage(error, '반려동물 목록을 불러오지 못했어요.') });
    } finally {
      set({ isLoading: false });
    }
  },
  isDetailLoading: false,
  isLoading: false,
  pets: [],
  resetPets: () => set({ error: null, isDetailLoading: false, isLoading: false, pets: [] }),
  upsertPet: pet =>
    set(state => ({
      pets: state.pets.some(item => item.id === pet.id)
        ? state.pets.map(item => (item.id === pet.id ? pet : item))
        : [pet, ...state.pets],
    })),
}));
