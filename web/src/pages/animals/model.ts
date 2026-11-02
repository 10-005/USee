import type { AnimalCardProps } from '@/shared/ui/AnimalCard';

export type ShelterAnimal = Omit<AnimalCardProps, 'onClick'> & {
  id: string;
  registeredAt: string;
  species: 'dog' | 'cat' | 'other';
  sex: 'female' | 'male' | 'unknown';
  neutered: 'yes' | 'no' | 'unknown';
};

export const animalFilters = [
  {
    key: 'status',
    label: '상태',
    options: [
      ['protected', '보호 중'],
      ['closed', '보호 종료'],
      ['returned', '반환 완료'],
      ['adopted', '입양 완료'],
    ],
  },
  {
    key: 'period',
    label: '기간',
    options: [
      ['7', '최근 7일'],
      ['30', '최근 30일'],
      ['90', '최근 90일'],
    ],
  },
  {
    key: 'species',
    label: '축종',
    options: [
      ['dog', '개'],
      ['cat', '고양이'],
      ['other', '기타'],
    ],
  },
  {
    key: 'sex',
    label: '성별',
    options: [
      ['female', '암컷'],
      ['male', '수컷'],
      ['unknown', '모름'],
    ],
  },
  {
    key: 'adoptable',
    label: '입양가능여부',
    options: [
      ['true', '입양가능'],
      ['false', '입양불가'],
    ],
  },
  {
    key: 'neutered',
    label: '중성화여부',
    options: [
      ['yes', '완료'],
      ['no', '미완료'],
      ['unknown', '모름'],
    ],
  },
] as const;

export type FilterKey = (typeof animalFilters)[number]['key'];

export function readAnimalFilters(params: URLSearchParams): Record<FilterKey, string> {
  return Object.fromEntries(
    animalFilters.map((filter) => {
      const value = params.get(filter.key) ?? '';
      return [filter.key, filter.options.some((option) => option[0] === value) ? value : ''];
    }),
  ) as Record<FilterKey, string>;
}

export function selectAnimals(
  animals: readonly ShelterAnimal[],
  params: URLSearchParams,
  referenceDate: string,
) {
  const filters = readAnimalFilters(params);
  const end = Date.parse(`${referenceDate}T00:00:00Z`);
  return animals
    .filter((animal) => {
      const daysAgo = (end - Date.parse(`${animal.registeredAt}T00:00:00Z`)) / 86400000;
      return (
        (!filters.status || filters.status === animal.status) &&
        (!filters.period || (daysAgo >= 0 && daysAgo < Number(filters.period))) &&
        (!filters.species || filters.species === animal.species) &&
        (!filters.sex || filters.sex === animal.sex) &&
        (!filters.adoptable || filters.adoptable === String(animal.adoptable)) &&
        (!filters.neutered || filters.neutered === animal.neutered)
      );
    })
    .sort((a, b) => {
      const dateOrder = a.registeredAt.localeCompare(b.registeredAt);
      return (params.get('sort') === 'oldest' ? dateOrder : -dateOrder) || a.id.localeCompare(b.id);
    });
}
