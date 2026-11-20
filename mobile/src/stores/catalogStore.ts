import { create } from 'zustand';

import { getApiErrorDisplayMessage } from '../services/api';
import { getDistricts, type District } from '../services/catalog';
import {
  getAnimalBreeds,
  getAnimalSpecies,
  type AnimalBreed,
  type AnimalSpecies,
} from '../services/pets';

type CatalogState = {
  breedsBySpecies: Record<number, AnimalBreed[]>;
  districts: District[];
  error: string | null;
  isLoading: boolean;
  loadBreeds: (speciesSeq: number) => Promise<void>;
  loadDistricts: () => Promise<void>;
  loadSpecies: () => Promise<void>;
  species: AnimalSpecies[];
};

export const useCatalogStore = create<CatalogState>((set, get) => ({
  breedsBySpecies: {},
  districts: [],
  error: null,
  isLoading: false,
  loadBreeds: async speciesSeq => {
    if (get().breedsBySpecies[speciesSeq]) {
      return;
    }
    try {
      const breeds = await getAnimalBreeds(speciesSeq);
      set(state => ({
        breedsBySpecies: { ...state.breedsBySpecies, [speciesSeq]: breeds },
      }));
    } catch (error) {
      set({ error: getApiErrorDisplayMessage(error, '품종 목록을 불러오지 못했어요.') });
    }
  },
  loadDistricts: async () => {
    if (get().districts.length > 0) {
      return;
    }
    set({ error: null, isLoading: true });
    try {
      set({ districts: await getDistricts() });
    } catch (error) {
      set({ error: getApiErrorDisplayMessage(error, '지역 목록을 불러오지 못했어요.') });
    } finally {
      set({ isLoading: false });
    }
  },
  loadSpecies: async () => {
    if (get().species.length > 0) {
      return;
    }
    set({ error: null, isLoading: true });
    try {
      set({ species: await getAnimalSpecies() });
    } catch (error) {
      set({ error: getApiErrorDisplayMessage(error, '축종 목록을 불러오지 못했어요.') });
    } finally {
      set({ isLoading: false });
    }
  },
  species: [],
}));
