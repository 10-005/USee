import { create } from 'zustand';
import { demoAnimals, initialAdoptions, initialRescues } from './fixtures';
import type { ShelterStore } from '@/entities/shelter/store-types';

// Deliberately memory-only: no credentials or demo records are sent to a server.
export const useShelterDemo = create<ShelterStore>((set) => ({
  animals: demoAnimals,
  adoptions: initialAdoptions,
  rescues: initialRescues,
  profile: {
    name: '박범준동물보호센터',
    address: '경상북도 구미시 3공단3로 302',
    phone: '053-1234-5678',
    holiday: '일요일',
    hours: '09:00 ~ 18:00',
    introduction:
      '안녕하세요. 박범준동물보호센터입니다. 보호동물들이 새로운 가족을 만날 수 있도록 함께합니다.',
  },
  saveAnimal: (animal) =>
    set((s) => ({
      adoptions: s.adoptions.map((a) =>
        a.animalId === animal.id ? { ...a, name: animal.name, image: animal.imageSrc } : a,
      ),
      animals: s.animals.some((a) => a.id === animal.id)
        ? s.animals.map((a) => (a.id === animal.id ? animal : a))
        : [animal, ...s.animals],
    })),
  deleteAnimal: (id) => set((s) => ({ animals: s.animals.filter((animal) => animal.id !== id) })),
  reviewAdoption: (id, status, reason) =>
    set((s) => {
      const application = s.adoptions.find((a) => a.id === id);
      if (!application) return s;
      return {
        adoptions: s.adoptions.map((a) => (a.id === id ? { ...a, status, reason } : a)),
        animals:
          status === '최종 승인'
            ? s.animals.map((animal) =>
                animal.id === application.animalId
                  ? {
                      ...animal,
                      status: 'adopted',
                      adoptable: false,
                      closingReason: reason,
                      closingReasonType: undefined,
                      guardianName: undefined,
                      guardianPhone: undefined,
                    }
                  : animal,
              )
            : s.animals,
      };
    }),
  saveRescue: (rescue) =>
    set((s) => ({
      rescues: s.rescues.some((r) => r.id === rescue.id)
        ? s.rescues.map((r) => (r.id === rescue.id ? rescue : r))
        : [rescue, ...s.rescues],
    })),
  saveProfile: (profile) => set({ profile }),
}));
