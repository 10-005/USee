import { shelterRequest } from '@/shared/api/session';
import type { ShelterAnimalRecord } from '../model';
import type { AnimalDetail } from './animals';

const statuses = {
  protected: 'PROTECTING',
  closed: 'ENDED',
  returned: 'RETURNED',
  adopted: 'ADOPTED',
} as const;
function managementFields(a: ShelterAnimalRecord) {
  const state = { status: statuses[a.status] };
  switch (a.status) {
    case 'protected':
      return { ...state, adoptionStatus: a.adoptable ? 'AVAILABLE' : 'UNAVAILABLE' };
    case 'returned':
      return { ...state, recipientName: a.guardianName, recipientPhone: a.guardianPhone };
    case 'closed':
      return {
        ...state,
        endReason:
          a.closingReason === '자연사'
            ? 'NATURAL_DEATH'
            : a.closingReason === '안락사'
              ? 'EUTHANIZED'
              : 'OTHER',
      };
    case 'adopted':
      return { ...state, reason: a.closingReason || undefined };
  }
}
const fields = (
  a: ShelterAnimalRecord,
  combinationSeq: number | undefined,
  imageSeqs: number[],
) => ({
  combinationSeq,
  gender: a.sex.toUpperCase(),
  isNeutered: a.neutered === 'unknown' ? null : a.neutered === 'yes',
  age: a.birth || null,
  weight: a.weight || null,
  microchipNumber: a.chip || null,
  registrationNumber: a.registration || null,
  description: a.description || null,
  healthCheck: a.health || null,
  vaccinationStatus: a.vaccination || null,
  imageSeqs,
});
export async function saveLiveAnimal(
  next: ShelterAnimalRecord,
  combinationSeq: number,
  imageSeqs: number[],
  previous?: { animal: ShelterAnimalRecord; detail: AnimalDetail },
  onBasicSaved?: (detail: AnimalDetail) => void,
) {
  const body = fields(next, combinationSeq, imageSeqs);
  const [noticeStartDate, noticeEndDate] = next.period.split(' ~ ');
  if (!previous) {
    const data = await shelterRequest<{ animalSeq: number }>('/shelter-animals', {
      method: 'POST',
      json: {
        ...body,
        noticeStartDate,
        noticeEndDate,
        adoptionStatus: next.adoptable ? 'AVAILABLE' : 'UNAVAILABLE',
        rescueSeq: next.rescueId ? Number(next.rescueId) : undefined,
      },
    });
    if (!data?.animalSeq) throw new Error('등록 결과를 확인할 수 없습니다. 목록을 확인해 주세요.');
    return String(data.animalSeq);
  }
  const path = '/shelter-animals/' + encodeURIComponent(next.id);
  const before = fields(
    previous.animal,
    previous.detail.classification?.combinationSeq,
    previous.detail.images.map((i) => i.imageSeq),
  );
  const patch = Object.fromEntries(
    Object.entries(body).filter(
      ([key, value]) =>
        JSON.stringify(before[key as keyof typeof before]) !== JSON.stringify(value),
    ),
  );
  let basicSaved = false;
  if (Object.keys(patch).length) {
    const updated = await shelterRequest<AnimalDetail>(path, { method: 'PATCH', json: patch });
    if (updated) onBasicSaved?.(updated);
    basicSaved = true;
  }
  const state = managementFields(next);
  const management: Record<string, unknown> =
    JSON.stringify(state) === JSON.stringify(managementFields(previous.animal)) ? {} : { ...state };
  const [oldStart, oldEnd] = previous.animal.period.split(' ~ ');
  if (oldStart !== noticeStartDate) management.noticeStartDate = noticeStartDate;
  if (oldEnd !== noticeEndDate) management.noticeEndDate = noticeEndDate;
  if (Object.keys(management).length) {
    try {
      await shelterRequest(path + '/management', { method: 'PATCH', json: management });
    } catch (error) {
      if (basicSaved)
        throw new Error(
          '기본정보는 저장됐습니다. 공고 및 상태 변경 실패: ' +
            (error instanceof Error ? error.message : '다시 시도해 주세요.'),
          { cause: error },
        );
      throw error;
    }
  }
  return next.id;
}
