import { create } from 'zustand';

type AdoptionLocationSelection = {
  address: string;
  id: number;
  latitude: number;
  longitude: number;
};

type AdoptionLocationState = {
  selection: AdoptionLocationSelection;
  selectLocation: (location: Omit<AdoptionLocationSelection, 'id'>) => void;
};

export const useAdoptionLocationStore = create<AdoptionLocationState>(set => ({
  selection: { address: '', id: 0, latitude: 0, longitude: 0 },
  selectLocation: location =>
    set(state => ({ selection: { ...location, id: state.selection.id + 1 } })),
}));
