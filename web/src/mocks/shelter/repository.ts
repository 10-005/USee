import type { ShelterRepository } from '@/entities/shelter/data/contracts';
import { initialReports } from './reports';
import { useShelterDemo } from './store';

/** 메모리 저장소를 비동기 도메인 계약에 맞게 제공하는 데모 어댑터입니다. */
export const shelterMockRepository: ShelterRepository = {
  async listAnimals(options) {
    options?.signal?.throwIfAborted();
    return useShelterDemo.getState().animals;
  },
  async getAnimal(id, options) {
    options?.signal?.throwIfAborted();
    return useShelterDemo.getState().animals.find((a) => a.id === id) ?? null;
  },
  async listRescues(options) {
    options?.signal?.throwIfAborted();
    return useShelterDemo.getState().rescues;
  },
  async getRescue(id, options) {
    options?.signal?.throwIfAborted();
    return useShelterDemo.getState().rescues.find((r) => r.id === id) ?? null;
  },
  async listAdoptions(options) {
    options?.signal?.throwIfAborted();
    return useShelterDemo.getState().adoptions;
  },
  async getAdoption(id, options) {
    options?.signal?.throwIfAborted();
    return useShelterDemo.getState().adoptions.find((a) => a.id === id) ?? null;
  },
  async listReports(options) {
    options?.signal?.throwIfAborted();
    return initialReports;
  },
  async getProfile(options) {
    options?.signal?.throwIfAborted();
    return useShelterDemo.getState().profile;
  },
  async saveAnimal(animal) {
    useShelterDemo.getState().saveAnimal(animal);
    return animal;
  },
  async deleteAnimal(id) {
    useShelterDemo.getState().deleteAnimal(id);
  },
  async saveRescue(rescue) {
    useShelterDemo.getState().saveRescue(rescue);
    return rescue;
  },
  async saveProfile(profile) {
    useShelterDemo.getState().saveProfile(profile);
    return profile;
  },
  async reviewAdoption(id, status, reason) {
    useShelterDemo.getState().reviewAdoption(id, status, reason);
  },
};
