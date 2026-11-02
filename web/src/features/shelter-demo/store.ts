import { create } from 'zustand';
import { sampleAnimals } from '@/pages/animals/sample-data';
import type { ShelterAnimal } from '@/pages/animals/model';
import cat from '@/shared/assets/figma/demo-cat.png';
import poodle from '@/shared/assets/figma/demo-poodle.png';
import poodleSecond from '@/shared/assets/figma/demo-poodle-2.jpg';
import poodleThird from '@/shared/assets/figma/demo-poodle-3.jpg';

export const adoptionStates = ['심사 대기', '서류 승인', '최종 승인', '거절'] as const;
export const rescueStates = ['진행 중', '완료', '실패', '취소'] as const;
export type AdoptionStatus = (typeof adoptionStates)[number];
export type RescueStatus = (typeof rescueStates)[number];
export type Adoption = {
  id: string;
  animalId: string;
  name: string;
  image: string;
  applicant: string;
  date: string;
  status: AdoptionStatus;
  reason: string;
};
export type Rescue = {
  reportId?: string;
  id: string;
  name: string;
  image: string;
  date: string;
  status: RescueStatus;
  source: string;
  reason: string;
  location: string;
  memo: string;
};
export type DemoAnimal = ShelterAnimal & {
  rescueId?: string;
  images?: string[];
  chip?: string;
  registration?: string;
  color?: string;
  birth?: string;
  weight?: string;
  health?: string;
  vaccination?: string;
  closingReason?: string;
  closingReasonType?: '자연사' | '안락사' | '직접 입력';
  guardianName?: string;
  guardianPhone?: string;
};
export type ShelterProfile = {
  name: string;
  address: string;
  phone: string;
  holiday: string;
  hours: string;
  introduction: string;
};
const demoAnimals: DemoAnimal[] = sampleAnimals.map((animal) => ({
  ...animal,
  birth: animal.summary.split(' | ')[0],
  color: '흰색',
  weight: animal.summary.split(' | ')[3]?.replace('kg', ''),
  name: animal.species === 'cat' ? '코리안숏헤어' : '푸들',
  imageSrc: animal.species === 'cat' ? cat : poodle,
  images: animal.id === 'sample-01' ? [poodle, poodleSecond, poodleThird] : undefined,
}));
export const initialAdoptions: Adoption[] = Array.from({ length: 24 }, (_, i) => ({
  id: `A-1029-${String(i + 1).padStart(2, '0')}`,
  animalId: `sample-${String((i % 24) + 1).padStart(2, '0')}`,
  name: demoAnimals[i]?.name ?? '푸들',
  image: demoAnimals[i]?.imageSrc ?? poodle,
  applicant: ['김민지', '이서준', '박지현', '최유진'][i % 4] ?? '김민지',
  date: `2026-10-${String(29 - i).padStart(2, '0')}`,
  status: adoptionStates[i % 4] ?? '심사 대기',
  reason: '',
}));
export const initialRescues: Rescue[] = Array.from({ length: 24 }, (_, i) => ({
  id: `R-1029-${String(i + 1).padStart(2, '0')}`,
  name: i % 2 ? '[개] 푸들' : '[고양이] 코리안숏헤어',
  image: i % 2 ? poodle : cat,
  date: `2026-10-${String(29 - i).padStart(2, '0')}T17:47`,
  status: rescueStates[i % 4] ?? '진행 중',
  source: i % 2 ? '직접 접수' : `신고 #F-${123 + i}`,
  reportId: i % 2 === 0 || i === 1 ? `F-${123 + i}` : undefined,
  reason: '배회·유기 의심',
  location: '구미시 인동동 123-12 송산도서관 인근 마을',
  memo: '공원 벤치 아래 은신 중. 사람에게 우호적이나 접근 시 도망감. 포획틀 지참 필요.',
}));
type DemoState = {
  animals: DemoAnimal[];
  adoptions: Adoption[];
  rescues: Rescue[];
  profile: ShelterProfile;
  saveAnimal: (animal: DemoAnimal) => void;
  reviewAdoption: (id: string, status: AdoptionStatus, reason: string) => void;
  saveRescue: (rescue: Rescue) => void;
  saveProfile: (profile: ShelterProfile) => void;
};
// Deliberately memory-only: no credentials or demo records are sent to a server.
export const useShelterDemo = create<DemoState>((set) => ({
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
