import { create } from 'zustand';

type AppState = {
  isReady: boolean;
  markReady: () => void;
  reset: () => void;
};

const initialState = {
  isReady: false,
};

export const useAppStore = create<AppState>(set => ({
  ...initialState,
  markReady: () => set({ isReady: true }),
  reset: () => set(initialState),
}));
