import { shelterAnimalLabel } from '@/entities/shelter/animal-label';
import { sampleAnimals } from './animals';
import {
  adoptionStates,
  rescueStates,
  type Adoption,
  type Rescue,
  type ShelterAnimalRecord,
} from '@/entities/shelter/model';
import cat from '@/shared/assets/figma/demo-cat.png';
import poodle from '@/shared/assets/figma/demo-poodle.png';
import poodleSecond from '@/shared/assets/figma/demo-poodle-2.jpg';
import poodleThird from '@/shared/assets/figma/demo-poodle-3.jpg';

export const demoAnimals: ShelterAnimalRecord[] = sampleAnimals.map((animal, index) => ({
  ...animal,
  noticeNumber: `경북-구미-2026-${String(index + 1).padStart(5, '0')}`,
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
  name: shelterAnimalLabel(demoAnimals[i]!),
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
  receivedAt: `2026-10-${String(29 - i).padStart(2, '0')}T09:30`,
  status: rescueStates[i % 4] ?? '진행 중',
  source: i % 2 ? '직접 접수' : `신고 #F-${123 + i}`,
  reportId: i % 2 === 0 || i === 1 ? `F-${123 + i}` : undefined,
  reason: '배회·유기 의심',
  location: '구미시 인동동 123-12 송산도서관 인근 마을',
  memo: '공원 벤치 아래 은신 중. 사람에게 우호적이나 접근 시 도망감. 포획틀 지참 필요.',
}));
