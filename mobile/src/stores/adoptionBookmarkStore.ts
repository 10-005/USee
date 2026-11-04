import { create } from 'zustand';
import { persist } from 'zustand/middleware';

import { createPersistStorage } from '../services/storage/persistStorage';

type AdoptionBookmarkState = {
  bookmarkedIds: string[];
  reset: () => void;
  toggleBookmark: (animalId: string) => void;
};

export const useAdoptionBookmarkStore = create<AdoptionBookmarkState>()(
  persist(
    set => ({
      bookmarkedIds: [],
      reset: () => set({ bookmarkedIds: [] }),
      toggleBookmark: animalId =>
        set(state => ({
          bookmarkedIds: state.bookmarkedIds.includes(animalId)
            ? state.bookmarkedIds.filter(id => id !== animalId)
            : [...state.bookmarkedIds, animalId],
        })),
    }),
    {
      name: 'usee-bookmarks',
      storage: createPersistStorage<AdoptionBookmarkState>(),
    },
  ),
);
