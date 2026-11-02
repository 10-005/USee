import { create } from 'zustand';

type AdoptionBookmarkState = {
  bookmarkedIds: string[];
  reset: () => void;
  toggleBookmark: (animalId: string) => void;
};

export const useAdoptionBookmarkStore = create<AdoptionBookmarkState>(set => ({
  bookmarkedIds: [],
  reset: () => set({ bookmarkedIds: [] }),
  toggleBookmark: animalId =>
    set(state => ({
      bookmarkedIds: state.bookmarkedIds.includes(animalId)
        ? state.bookmarkedIds.filter(id => id !== animalId)
        : [...state.bookmarkedIds, animalId],
    })),
}));
