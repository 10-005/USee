import { queryOptions } from '@tanstack/react-query';
import { shelterRequest } from '@/shared/api/session';
import type { PageData } from '@/shared/api/contracts';
import type { ShelterAnimalRecord } from '../model';

const base = '/shelter-animals';
type Gender = 'MALE' | 'FEMALE' | 'UNKNOWN';
type Status = 'PROTECTING' | 'ENDED' | 'RETURNED' | 'ADOPTED';
type Tab = 'PROTECTING_AVAILABLE' | 'PROTECTING_UNAVAILABLE' | 'ENDED' | 'RETURNED' | 'ADOPTED';
const protection = {
  PROTECTING: 'protected',
  ENDED: 'closed',
  RETURNED: 'returned',
  ADOPTED: 'adopted',
} as const;
const gender = { MALE: 'male', FEMALE: 'female', UNKNOWN: 'unknown' } as const;
type Classification = {
  combinationSeq: number;
  speciesName: string;
  breedName: string;
  colorName: string;
};
export type Combination = Classification & {
  speciesSeq: number;
  breedSeq: number;
  colorSeq: number;
};
type Animal = {
  animalSeq: number;
  classification: Classification | null;
  gender: Gender;
  isNeutered: boolean | null;
  age: string | null;
  weight: string | null;
  protectionStatus: Status;
  adoptionStatus: 'AVAILABLE' | 'UNAVAILABLE' | 'ADOPTED';
  noticeStartDate: string;
  noticeEndDate: string;
  regDttm: string;
};
type Card = Animal & {
  description?: string | null;
  representativeImageUrl: string | null;
  shelter: { shelterName: string } | null;
};
export type AnimalDetail = Animal & {
  classification: Combination | null;
  images: { imageSeq: number; imageUrl: string }[];
  registrationNumber: string | null;
  microchipNumber: string | null;
  description: string | null;
  healthCheck: string | null;
  vaccinationStatus: string | null;
  endReason: string | null;
  rescueSeq: number | null;
  rescueInfo: {
    rescueAddress: string | null;
    rescueAddressDetail: string | null;
    rescuedDttm: string | null;
    rescueMemo: string | null;
  };
  returnInfo: { recipientName: string; recipientPhone: string } | null;
  adoptionInfo: {
    reason: string | null;
    memo: string | null;
    completedApplicationSeq: number | null;
  } | null;
  applicationCounts: { SUBMITTED: number; APPROVED: number };
};
export function speciesKey(name: string | undefined) {
  return name === '개' ? 'dog' : name === '고양이' ? 'cat' : 'other';
}
function animalRecord(a: Animal): ShelterAnimalRecord {
  const c = a.classification;
  return {
    id: String(a.animalSeq),
    name: c?.breedName || '품종 미상',
    status: protection[a.protectionStatus],
    adoptable: a.adoptionStatus === 'AVAILABLE',
    species: speciesKey(c?.speciesName),
    speciesName: c?.speciesName || '축종 미상',
    color: c?.colorName,
    sex: gender[a.gender],
    neutered: a.isNeutered === null ? 'unknown' : a.isNeutered ? 'yes' : 'no',
    imageSrc: '',
    imageAlt: c?.breedName ?? '보호동물',
    location: '',
    description: '',
    period: [a.noticeStartDate, a.noticeEndDate].join(' ~ '),
    registeredAt: a.regDttm.slice(0, 10),
    birth: a.age ?? undefined,
    weight: a.weight ?? undefined,
    summary: [
      a.age,
      { MALE: '수컷', FEMALE: '암컷', UNKNOWN: '성별 미상' }[a.gender],
      c?.colorName,
      a.weight,
    ]
      .filter(Boolean)
      .join(' | '),
  };
}
export function detailRecord(a: AnimalDetail): ShelterAnimalRecord {
  return {
    ...animalRecord(a),
    images: a.images.map((i) => i.imageUrl),
    imageSrc: a.images[0]?.imageUrl ?? '',
    chip: a.microchipNumber ?? undefined,
    registration: a.registrationNumber ?? undefined,
    description: a.description ?? '',
    health: a.healthCheck ?? undefined,
    vaccination: a.vaccinationStatus ?? undefined,
    location: [a.rescueInfo.rescueAddress, a.rescueInfo.rescueAddressDetail]
      .filter(Boolean)
      .join(' '),
    rescueId: a.rescueSeq ? String(a.rescueSeq) : undefined,
    guardianName: a.returnInfo?.recipientName,
    guardianPhone: a.returnInfo?.recipientPhone,
    closingReason:
      a.protectionStatus === 'ADOPTED'
        ? (a.adoptionInfo?.reason ?? undefined)
        : { NATURAL_DEATH: '자연사', EUTHANIZED: '안락사', OTHER: '기타' }[a.endReason ?? ''],
  };
}
async function read<T>(path: string, signal: AbortSignal) {
  const data = await shelterRequest<T>(path, { signal });
  if (data == null) throw new Error('보호동물 응답을 확인할 수 없습니다.');
  return data;
}
export const animalDetailOptions = (id: string) =>
  queryOptions({
    queryKey: ['animals', 'detail', id],
    queryFn: ({ signal }) => read<AnimalDetail>(base + '/me/' + encodeURIComponent(id), signal),
  });
export const combinationsOptions = queryOptions({
  queryKey: ['animal-combinations'],
  staleTime: 300000,
  queryFn: ({ signal }) => read<Combination[]>('/animal-combinations', signal),
});
export const linkedRescueOptions = (id: string) =>
  queryOptions({
    queryKey: ['rescue-for-animal', id],
    queryFn: ({ signal }) =>
      read<{ rescueSeq: number; status: string; rescueAddress: string; rescuedDttm: string }>(
        '/rescues/' + encodeURIComponent(id),
        signal,
      ),
  });

type Counts = Record<Tab | 'total', number>;
type Filters = {
  status: string;
  period: string;
  species: string;
  sex: string;
  adoptable: string;
  neutered: string;
};
export const pageSize = 12;
const tabs: Tab[] = [
  'PROTECTING_AVAILABLE',
  'PROTECTING_UNAVAILABLE',
  'RETURNED',
  'ADOPTED',
  'ENDED',
];
function matches(tab: Tab, status: string, adoptable: string) {
  const group = tab.startsWith('PROTECTING_') ? 'protected' : protection[tab as Status];
  return (
    (!status || status === group) &&
    (!adoptable || String(tab === 'PROTECTING_AVAILABLE') === adoptable)
  );
}
export function animalListOptions(filters: Filters, page: number, oldest: boolean) {
  return queryOptions({
    queryKey: ['animals', 'list', filters, page, oldest],
    queryFn: async ({ signal }) => {
      const params = new URLSearchParams({ sort: 'regDttm,' + (oldest ? 'asc' : 'desc') });
      if (filters.period) params.set('period', 'RECENT_' + filters.period + 'D');
      if (filters.sex) params.set('gender', filters.sex.toUpperCase());
      if (filters.neutered) params.set('isNeutered', String(filters.neutered === 'yes'));
      if (filters.species) {
        const species = await read<{ speciesSeq: number; speciesName: string }[]>(
          '/animal-species',
          signal,
        );
        const match = species.find((s) => speciesKey(s.speciesName) === filters.species);
        if (!match) throw new Error('축종 정보를 확인할 수 없습니다.');
        params.set('speciesSeq', String(match.speciesSeq));
      }
      const allowed = tabs.filter((t) => matches(t, filters.status, filters.adoptable));
      if (allowed.length === 1) params.set('status', allowed[0]!);
      const combined = allowed.length > 1 && allowed.length < tabs.length;
      const fetchPage = (index: number) => {
        params.set('page', String(index));
        params.set('size', String(combined ? 100 : pageSize));
        return read<{ statusCounts: Counts; page: PageData<Card> }>(base + '/me?' + params, signal);
      };
      let response = await fetchPage(combined ? 0 : page - 1);
      const counts: Record<string, number> = {};
      for (const status of ['', ...Object.values(protection)])
        counts[status] = tabs
          .filter((t) => matches(t, status, filters.adoptable))
          .reduce((sum, t) => sum + response.statusCounts[t], 0);
      const total = counts[filters.status] ?? 0;
      const currentPage = Math.max(1, Math.min(page, Math.ceil(total / pageSize)));
      const items: Card[] = [];
      if (combined) {
        // The API accepts one composite status. Read in server sort order across both protection tabs.
        for (;;) {
          items.push(
            ...response.page.content.filter((a) =>
              allowed.includes(
                a.protectionStatus === 'PROTECTING'
                  ? a.adoptionStatus === 'AVAILABLE'
                    ? 'PROTECTING_AVAILABLE'
                    : 'PROTECTING_UNAVAILABLE'
                  : a.protectionStatus,
              ),
            ),
          );
          if (items.length >= currentPage * pageSize || !response.page.hasNext) break;
          response = await fetchPage(response.page.page + 1);
        }
      } else if (allowed.length) {
        if (currentPage !== page) response = await fetchPage(currentPage - 1);
        items.push(...response.page.content);
      }
      const visible = combined
        ? items.slice((currentPage - 1) * pageSize, currentPage * pageSize)
        : items;
      return {
        counts,
        total,
        page: currentPage,
        animals: visible.map((a) => ({
          ...animalRecord(a),
          description: a.description ?? '',
          needsDetailDescription: a.description === undefined,
          imageSrc: a.representativeImageUrl ?? '',
          location: a.shelter?.shelterName ?? '',
        })),
      };
    },
  });
}
export const removeAnimal = (id: string) =>
  shelterRequest(base + '/' + encodeURIComponent(id), { method: 'DELETE' });
