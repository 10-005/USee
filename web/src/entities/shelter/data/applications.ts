import { animalLabel } from '@/entities/shelter/animal-label';
import { queryOptions } from '@tanstack/react-query';
import type { PageData } from '@/shared/api/contracts';
import { shelterRequest } from '@/shared/api/session';
import type { Adoption, AdoptionStatus } from '../model';

export const statusCodes = {
  '심사 대기': 'SUBMITTED',
  '서류 승인': 'APPROVED',
  '최종 승인': 'ADOPTED',
  거절: 'REJECTED',
} as const;
type Status = (typeof statusCodes)[AdoptionStatus];
const labels: Record<Status, AdoptionStatus> = {
  SUBMITTED: '심사 대기',
  APPROVED: '서류 승인',
  ADOPTED: '최종 승인',
  REJECTED: '거절',
};
type Animal = {
  animalSeq: number;
  speciesName: string | null;
  breedName: string | null;
  colorName: string | null;
  gender: 'MALE' | 'FEMALE' | 'UNKNOWN';
  age: string | null;
  weight: string | null;
  microchipNumber: string | null;
  registrationNumber: string | null;
  representativeImageUrl: string | null;
  isNeutered: boolean | null;
};
type Application = {
  applicationSeq: number;
  status: Status;
  submittedDttm: string | null;
  animalSummary: Animal | null;
};
type Item = Application & { applicantName: string | null };
type Detail = Application & {
  userSeq: number;
  applicant: { name: string | null; birthDate: string | null; phone: string | null };
  careAddress: string | null;
  careAddressDetail: string | null;
  email: string | null;
  visitHopeDate: string | null;
  answers: Record<string, string | string[]>;
  questions: { questionSeq: number; questionText: string }[];
  rejectionReason: string | null;
};
const base = '/adoption-applications';
function record(row: Application, applicant: string | null): Adoption {
  const a = row.animalSummary;
  return {
    id: String(row.applicationSeq),
    status: labels[row.status],
    date: row.submittedDttm?.slice(0, 10) || '미등록',
    applicant: applicant || '미등록',
    animalId: a ? String(a.animalSeq) : '',
    image: a?.representativeImageUrl || '',
    name: a ? animalLabel(a.speciesName, a.breedName) : '동물 정보 없음',
  };
}
async function read<T>(path: string, signal: AbortSignal) {
  const data = await shelterRequest<T>(path, { signal });
  if (data == null) throw new Error('응답 데이터가 없습니다.');
  return data;
}
export function receivedOptions(params: URLSearchParams) {
  const search = new URLSearchParams({
    size: '8',
    page: String(Math.max(0, Math.floor(Number(params.get('page')) || 1) - 1)),
    sort: 'submittedDttm,' + (params.get('sort') === 'oldest' ? 'asc' : 'desc'),
  });
  const status = params.get('status') as AdoptionStatus;
  if (Object.hasOwn(statusCodes, status)) search.set('status', statusCodes[status]);
  const animal = params.get('animalId');
  if (animal) search.set('animalSeq', animal);
  return queryOptions({
    queryKey: ['adoptions', 'received', search.toString()],
    queryFn: async ({ signal }) => {
      const data = await read<PageData<Item>>(base + '/received?' + search, signal);
      return { ...data, content: data.content.map((row) => record(row, row.applicantName)) };
    },
  });
}
export function siblingOptions(animalId = '') {
  return queryOptions({
    queryKey: ['adoptions', 'sibling-animals', animalId],
    enabled: Boolean(animalId),
    queryFn: async ({ signal, client }) => {
      const ids = new Set<string>();
      const params = new URLSearchParams({
        animalSeq: animalId,
        size: '32',
        sort: 'submittedDttm,desc',
      });
      for (let page = 0; ; page++) {
        params.set('page', String(page));
        const data = await read<PageData<Item>>(base + '/received?' + params, signal);
        for (const row of data.content) {
          if (String(row.animalSummary?.animalSeq) === animalId)
            ids.add(String(row.applicationSeq));
        }
        if (!data.content.length || page + 1 >= data.totalPages || data.hasNext === false) break;
      }
      if (ids.size < 2) return [];
      // The received list can show the current breed for every application,
      // while individual details show different animal snapshots.
      const siblings: Pick<Adoption, 'id' | 'name'>[] = [];
      const candidates = [...ids];
      for (let offset = 0; offset < candidates.length; offset += 4) {
        signal.throwIfAborted();
        const batch = candidates.slice(offset, offset + 4);
        const details = await Promise.all(batch.map((id) => client.fetchQuery(detailOptions(id))));
        for (const { record } of details) {
          if (record.animalId === animalId) siblings.push({ id: record.id, name: record.name });
        }
      }
      return siblings;
    },
  });
}
export function countOptions(animal: string | null) {
  return queryOptions({
    queryKey: ['adoptions', 'counts', animal],
    queryFn: ({ signal }) =>
      read<Record<Status | 'total', number>>(
        base +
          '/received/status-counts' +
          (animal ? '?animalSeq=' + encodeURIComponent(animal) : ''),
        signal,
      ),
  });
}
export function applicantApplicationsOptions(userSeq: number) {
  return queryOptions({
    queryKey: ['adoptions', 'by-applicant', userSeq],
    queryFn: async ({ client, signal }) => {
      const matches = new Map<string, Adoption>();
      for (let page = 0; ; page++) {
        const result = await read<PageData<Item>>(
          base + '/received?sort=submittedDttm,desc&size=24&page=' + page,
          signal,
        );
        for (let start = 0; start < result.content.length; start += 4) {
          signal.throwIfAborted();
          const batch = result.content.slice(start, start + 4);
          const details = await Promise.all(
            batch.map((item) => client.fetchQuery(detailOptions(String(item.applicationSeq)))),
          );
          for (const detail of details) {
            if (detail.userSeq === userSeq) matches.set(detail.record.id, detail.record);
          }
        }
        if (!result.content.length || page + 1 >= result.totalPages || result.hasNext === false)
          break;
      }
      return [...matches.values()];
    },
  });
}
export function detailOptions(id: string) {
  return queryOptions({
    queryKey: ['adoptions', 'detail', id],
    queryFn: async ({ signal }) => {
      const data = await read<Detail>(base + '/' + encodeURIComponent(id), signal);
      const a = data.animalSummary;
      const basic: [string, string][] = [
        ['신청자', data.applicant.name],
        ['생년월일', data.applicant.birthDate],
        ['전화번호', data.applicant.phone],
        ['실제 양육 장소', data.careAddress],
        ['상세 주소', data.careAddressDetail],
        ['이메일', data.email],
        ['방문 상담 희망일', data.visitHopeDate],
      ].map(([k, v]) => [k!, v || '미등록']);
      const groups = [
        { title: '주거 및 동거 환경', ids: [1, 2, 3, 4, 5, 11] },
        { title: '양육 경험 및 돌봄 환경', ids: [6, 7, 8, 9, 10, 12] },
        { title: '입양 및 돌봄 계획', ids: [13, 14, 15, 16] },
        { title: '입양 전 교육', ids: [17, 18, 19, 20] },
      ].map((group) => ({ ...group, rows: [] as [string, string][] }));
      const other = { title: '추가 문항', rows: [] as [string, string][] };
      for (const { questionSeq, questionText } of data.questions) {
        const value = data.answers[questionSeq];
        if (value == null) continue;
        const section = groups.find((group) => group.ids.includes(questionSeq)) ?? other;
        section.rows.push([questionText, Array.isArray(value) ? value.join(', ') : value]);
      }
      return {
        userSeq: data.userSeq,
        record: { ...record(data, data.applicant.name), reason: data.rejectionReason ?? undefined },
        animal: a
          ? {
              summary: [
                a.age,
                { MALE: '수컷', FEMALE: '암컷', UNKNOWN: '성별 미상' }[a.gender],
                a.colorName,
                a.weight,
              ]
                .filter(Boolean)
                .join(' · '),
              neutered: a.isNeutered == null ? 'unknown' : a.isNeutered ? 'yes' : 'no',
              registration: a.registrationNumber,
              chip: a.microchipNumber,
            }
          : undefined,
        questions: [
          { title: '신청자 기본 정보', rows: basic },
          ...groups,
          ...(other.rows.length ? [other] : []),
        ],
      };
    },
  });
}
export function submitReview(
  id: string,
  current: AdoptionStatus,
  next: AdoptionStatus,
  reason?: string,
) {
  const path = base + '/' + encodeURIComponent(id);
  if (current === '심사 대기')
    return shelterRequest(path + '/review', {
      method: 'POST',
      json:
        next === '거절' ? { decision: 'REJECT', rejectionReason: reason } : { decision: 'APPROVE' },
    });
  if (next === '최종 승인') return shelterRequest(path + '/complete', { method: 'POST' });
  return shelterRequest(path + '/fail', { method: 'POST', json: { rejectionReason: reason } });
}
