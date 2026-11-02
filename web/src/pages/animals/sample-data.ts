import animalImage from '@/shared/assets/figma/animal-example.png';
import type { ShelterAnimal } from './model';

// Fixed reference date keeps sample date filters reproducible until API integration.
export const sampleReferenceDate = '2026-11-01';
export const sampleAnimals: ShelterAnimal[] = Array.from({ length: 24 }, (_, index) => {
  const status = (
    ['protected', 'closed', 'returned', 'adopted', 'protected', 'protected'] as const
  )[index % 6]!;
  const sex = (['female', 'male', 'unknown'] as const)[index % 3]!;
  const species = index % 3 === 2 ? 'cat' : 'dog';
  return {
    id: `sample-${String(index + 1).padStart(2, '0')}`,
    registeredAt: new Date(Date.UTC(2026, 10, 1 - index * 5)).toISOString().slice(0, 10),
    name: species === 'dog' ? '말티즈' : '한국고양이',
    imageSrc: animalImage,
    imageAlt: '보호동물 예시 사진',
    status,
    adoptable: status === 'protected' && index % 6 !== 4,
    species,
    sex,
    neutered: (['yes', 'no', 'unknown'] as const)[Math.floor(index / 3) % 3]!,
    location: index % 2 ? '구미시 진평동' : '구미시 인동동 123-123',
    period: '2026-10-19 ~ 2026-11-17',
    summary: `${index % 4}살 | ${sex === 'female' ? '암컷' : sex === 'male' ? '수컷' : '성별 모름'} | 흰색 | ${2 + (index % 5)}kg`,
    description: '사람을 잘 따르고 산책을 좋아해요.',
  };
});
