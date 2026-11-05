import { shelterMockRepository } from '@/mocks/shelter/repository';
import { useShelterDemo } from '@/mocks/shelter/store';
import type { ShelterAnimalRecord } from '../model';
import type { LoadState } from '@/shared/ui/LoadState';

// This adapter is synchronous and memory-only until an API is connected.
export const useAnimals = () => useShelterDemo((state) => state.animals);
export type DataResult<T> = { data: T; loadState: LoadState };
export const useAnimalsResult = (): DataResult<ShelterAnimalRecord[]> => ({
  data: useAnimals(),
  loadState: { status: 'ready' },
});
export const useAnimal = (id?: string) =>
  useShelterDemo((state) => state.animals.find((item) => item.id === id));
export const useAdoptions = () => useShelterDemo((state) => state.adoptions);
export const useAdoption = (id?: string) =>
  useShelterDemo((state) => state.adoptions.find((item) => item.id === id));
export const useRescues = () => useShelterDemo((state) => state.rescues);
export const useRescue = (id?: string) =>
  useShelterDemo((state) => state.rescues.find((item) => item.id === id));
export const useAnimalByRescue = (id?: string) =>
  useShelterDemo((state) => state.animals.find((item) => item.rescueId === id && id !== undefined));
export const useProfile = () => useShelterDemo((state) => state.profile);
export const useSaveAnimal = () => shelterMockRepository.saveAnimal;
export const useDeleteAnimal = () => shelterMockRepository.deleteAnimal;
export const useSaveRescue = () => shelterMockRepository.saveRescue;
export const useSaveProfile = () => shelterMockRepository.saveProfile;
export const useReviewAdoption = () => shelterMockRepository.reviewAdoption;
export const getProfile = () => useShelterDemo.getState().profile;
export const saveProfile = (
  profile: Parameters<ReturnType<typeof useShelterDemo.getState>['saveProfile']>[0],
) => useShelterDemo.getState().saveProfile(profile);
export type AnimalRecord = ShelterAnimalRecord;

// The current adapter uses fixed fixtures; API adapters can supply live records and dates.
import { initialReports } from '@/mocks/shelter/reports';
export const useReports = () => initialReports;
export type { Report } from '../report';
export { sampleReferenceDate as referenceDate } from '@/mocks/shelter/animals';
