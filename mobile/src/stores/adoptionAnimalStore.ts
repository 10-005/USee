import { create } from 'zustand';

import type { AdoptionAnimal } from '../screens/adoption/data';
import { getApiErrorDisplayMessage } from '../services/api';
import {
  getShelterAnimal,
  searchShelterAnimals,
  type ShelterAnimalCard,
  type ShelterAnimalDetail,
  type ShelterAnimalSearchParams,
} from '../services/adoption';

type AdoptionAnimalState = {
  detailsById: Record<string, ShelterAnimalDetail>;
  error: string | null;
  fetchAnimal: (animalId: string) => Promise<void>;
  fetchAnimals: (params?: ShelterAnimalSearchParams) => Promise<void>;
  isDetailLoading: boolean;
  isLoading: boolean;
  animals: AdoptionAnimal[];
};

const genderLabel = (gender: ShelterAnimalCard['gender']) =>
  gender === 'MALE' ? '수컷' : gender === 'FEMALE' ? '암컷' : '모름';

export const mapShelterAnimalCard = (animal: ShelterAnimalCard): AdoptionAnimal => ({
  date: animal.noticeStartDate,
  description: '',
  districtCode: animal.shelter?.districtCode,
  id: String(animal.animalSeq),
  image: animal.representativeImageUrl
    ? { uri: animal.representativeImageUrl }
    : require('../assets/images/adoption/animal-dog-white.png'),
  location: animal.shelter?.shelterName ?? '보호소 정보 없음',
  isAdoptionUnavailable:
    animal.protectionStatus === 'PROTECTING' && animal.adoptionStatus !== 'AVAILABLE',
  name: animal.classification?.breedName ?? '보호동물',
  neutered: animal.isNeutered,
  species: animal.classification?.speciesName ?? '기타',
  status: animal.protectionStatus === 'PROTECTING' ? '보호' : '종료',
  summary: `${animal.age ?? '모름'} | ${genderLabel(animal.gender)} | ${
    animal.classification?.colorName ?? '모름'
  } | ${animal.weight ?? '모름'}`,
});

export const useAdoptionAnimalStore = create<AdoptionAnimalState>(set => ({
  animals: [],
  detailsById: {},
  error: null,
  fetchAnimal: async animalId => {
    set({ error: null, isDetailLoading: true });
    try {
      const detail = await getShelterAnimal(Number(animalId));
      set(state => ({
        animals: [
          ...state.animals.filter(animal => animal.id !== animalId),
          mapShelterAnimalCard(detail),
        ],
        detailsById: { ...state.detailsById, [animalId]: detail },
      }));
    } catch (error) {
      set({ error: getApiErrorDisplayMessage(error, '보호동물 정보를 불러오지 못했어요.') });
    } finally {
      set({ isDetailLoading: false });
    }
  },
  fetchAnimals: async params => {
    set({ error: null, isLoading: true });
    try {
      const page = await searchShelterAnimals(params);
      set({ animals: page.content.map(mapShelterAnimalCard) });
    } catch (error) {
      set({ error: getApiErrorDisplayMessage(error, '보호동물 목록을 불러오지 못했어요.') });
    } finally {
      set({ isLoading: false });
    }
  },
  isDetailLoading: false,
  isLoading: false,
}));
