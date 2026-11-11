import { animalLabel } from '@/entities/shelter/animal-label';
import { queryOptions, type QueryClient } from '@tanstack/react-query';
import { shelterRequest } from '@/shared/api/session';
import type { PageData } from '@/shared/api/contracts';
import type { Rescue, RescueStatus } from '../model';
import type { Report } from '../report';

export const statusCode = {
  '진행 중': 'PROCESSING',
  완료: 'DONE',
  실패: 'FAILED',
  취소: 'CANCELED',
} as const;
const statusName = {
  PROCESSING: '진행 중',
  DONE: '완료',
  FAILED: '실패',
  CANCELED: '취소',
} as const;
const reasons: Record<string, string> = {
  '배회·유기 의심': 'WANDERING',
  '부상·질병': 'INJURY_ILLNESS',
  '위험 장소': 'DANGEROUS_PLACE',
  기타: 'OTHER',
};
const kinds: Record<string, string> = {
  new: 'NONE',
  active: 'PROCESSING',
  completed: 'DONE',
  closed: 'ABORTED',
};
type Found = {
  foundReportSeq: number;
  classification: { speciesName: string; breedName: string; colorName: string } | null;
  address: string;
  occurredDttm: string;
  regDttm?: string | null;
  representativeImageUrl: string | null;
  status: 'ACTIVE' | 'CLOSED';
  reporter: { name?: string; displayName?: string; phone?: string } | null;
  reporterPhone?: string | null;
  age: string | null;
  weight: string | null;
  description?: string | null;
};
const seoulMinute = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});
function localMinute(value: string) {
  return seoulMinute.format(new Date(value)).replace(' ', 'T');
}
function reportRecord(f: Found): Report {
  const c = f.classification;
  return {
    id: String(f.foundReportSeq),
    name: c ? animalLabel(c.speciesName, c.breedName) : '미확인 동물',
    species: c?.speciesName ?? '',
    location: f.address,
    reportStatus: f.status === 'ACTIVE' ? '신고 진행 중' : '신고 종료',
    foundAt: localMinute(f.occurredDttm),
    createdAt: f.regDttm ?? '',
    traits: f.description || [f.age, c?.colorName, f.weight].filter(Boolean).join(' · '),
    reporter: f.reporter?.name || f.reporter?.displayName || '미등록',
    contact: f.reporter?.phone ?? f.reporterPhone ?? '',
    animalStatus: '',
    image: f.representativeImageUrl ?? '',
  };
}
type Assigned = Found & {
  rescueStatus: 'NONE' | 'PROCESSING' | 'DONE' | 'ABORTED';
  activeRescueSeq: number | null;
};
type RescueRow = {
  rescueSeq: number;
  foundReportSeq: number | null;
  status: keyof typeof statusName;
  rescueReasonCode: string;
  rescuedDttm: string;
  rescueMemo: string | null;
  resultReason: string | null;
  regDttm: string | null;
  animal?: {
    breedName: string;
    speciesName: string;
    representativeImageUrl: string | null;
    age: string | null;
    gender: 'MALE' | 'FEMALE' | 'UNKNOWN' | null;
    colorName: string | null;
    weight: string | null;
  } | null;
};
type Detail = RescueRow & {
  rescueAddress: string;
  rescueAddressDetail: string | null;
  foundReportSummary: Found | null;
  linkedAnimalCount: number;
};
function rescueRecord(r: RescueRow, address = ''): Rescue {
  const animal = r.animal;
  return {
    id: String(r.rescueSeq),
    reportId: r.foundReportSeq == null ? undefined : String(r.foundReportSeq),
    name: r.animal ? animalLabel(r.animal.speciesName, r.animal.breedName) : '—',
    animalSummary: [
      animal?.age,
      animal?.gender && { MALE: '수컷', FEMALE: '암컷', UNKNOWN: '성별 미상' }[animal.gender],
      animal?.colorName,
      animal?.weight,
    ]
      .filter(Boolean)
      .join(' | '),
    image: r.animal?.representativeImageUrl ?? '',
    date: localMinute(r.rescuedDttm),
    receivedAt: r.regDttm ? localMinute(r.regDttm) : undefined,
    status: statusName[r.status],
    statusReason: r.resultReason ?? undefined,
    source: r.foundReportSeq == null ? '직접 접수' : '신고 #' + r.foundReportSeq,
    reason: Object.keys(reasons).find((k) => reasons[k] === r.rescueReasonCode) ?? '',
    location: address,
    memo: r.rescueMemo ?? '',
  };
}
async function read<T>(path: string, signal: AbortSignal) {
  const data = await shelterRequest<T>(path, { signal });
  if (data == null) throw new Error('서버 응답을 확인할 수 없습니다.');
  return data;
}
function listParams(params: URLSearchParams) {
  const q = new URLSearchParams({
    page: String(Math.max(0, (Number.parseInt(params.get('page') ?? '', 10) || 1) - 1)),
    size: '8',
    sort: 'regDttm,' + (params.get('sort') === 'oldest' ? 'asc' : 'desc'),
  });
  const period = params.get('period');
  if (period && ['7', '30', '90'].includes(period)) q.set('period', 'RECENT_' + period + 'D');
  const species = params.get('species');
  if (species) q.set('speciesSeq', species);
  return q;
}
export const speciesOptions = queryOptions({
  queryKey: ['rescue-species'],
  queryFn: ({ signal }) =>
    read<{ speciesSeq: number; speciesName: string }[]>('/animal-species', signal),
  staleTime: 300000,
});
export const districtOptions = queryOptions({
  queryKey: ['districts'],
  queryFn: ({ signal }) =>
    read<{ districtCode: string; sidoName: string; sigunguName: string }[]>('/districts', signal),
  staleTime: 300000,
});
export function assignedRefreshOptions(params: URLSearchParams) {
  const latestPage = (Number(params.get('page')) || 1) <= 1 && params.get('sort') !== 'oldest';
  return {
    refetchInterval: latestPage ? 5000 : (false as const),
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: latestPage ? ('always' as const) : (false as const),
  };
}
export function assignedOptions(params: URLSearchParams, size = 8) {
  const q = listParams(params);
  q.set('size', String(size));
  if (params.get('region')) q.set('districtCode', params.get('region')!);
  if (params.get('status'))
    q.set('status', params.get('status') === '신고 종료' ? 'CLOSED' : 'ACTIVE');
  const kind = kinds[params.get('kind') ?? ''];
  if (kind) q.set('rescueStatus', kind);
  return queryOptions({
    queryKey: ['assigned-reports', q.toString()],
    queryFn: async ({ signal }) => {
      const page = await read<PageData<Assigned>>('/found-reports/assigned?' + q, signal);
      return {
        ...page,
        content: page.content.map((f) => ({
          ...reportRecord(f),
          rescue:
            f.rescueStatus === 'NONE'
              ? undefined
              : {
                  id: f.activeRescueSeq == null ? '' : String(f.activeRescueSeq),
                  status: { PROCESSING: '진행 중', DONE: '완료', ABORTED: '실패' }[f.rescueStatus],
                },
        })),
      };
    },
  });
}
export function rescueListOptions(params: URLSearchParams) {
  const q = listParams(params);
  const status = statusCode[params.get('status') as RescueStatus];
  if (status) q.set('status', status);
  if (params.get('source')) q.set('source', params.get('source')!.toUpperCase());
  return queryOptions({
    queryKey: ['rescues', 'list', q.toString()],
    queryFn: async ({ signal }) => {
      const data = await read<{ page: PageData<RescueRow>; statusCounts: Record<string, number> }>(
        '/rescues?' + q,
        signal,
      );
      return {
        counts: data.statusCounts,
        page: { ...data.page, content: data.page.content.map((r) => rescueRecord(r)) },
      };
    },
  });
}
export const foundOptions = (id: string) =>
  queryOptions({
    queryKey: ['found-report', id],
    queryFn: async ({ signal }) =>
      reportRecord(await read<Found>('/found-reports/' + encodeURIComponent(id), signal)),
  });
export const rescueSummaryOptions = (id: string) =>
  queryOptions({
    queryKey: ['rescues', 'summary', id],
    retry: false,
    queryFn: async ({ signal }) => {
      let pageNumber = 0;
      let totalPages = 1;
      while (pageNumber < totalPages) {
        const { page } = await read<{ page: PageData<RescueRow> }>(
          '/rescues?size=50&sort=regDttm,desc&page=' + pageNumber,
          signal,
        );
        const row = page.content.find((record) => String(record.rescueSeq) === id);
        if (row) return rescueRecord(row);
        if (!page.content.length) break;
        if (pageNumber === 0) totalPages = page.totalPages;
        pageNumber++;
      }
      return null;
    },
  });
export const rescueOptions = (id: string) =>
  queryOptions({
    queryKey: ['rescues', 'detail', id],
    queryFn: async ({ signal }) => {
      const d = await read<Detail>('/rescues/' + encodeURIComponent(id), signal);
      return {
        record: rescueRecord(d, d.rescueAddress),
        addressDetail: d.rescueAddressDetail ?? '',
        report: d.foundReportSummary ? reportRecord(d.foundReportSummary) : undefined,
        count: d.linkedAnimalCount,
      };
    },
  });
export async function refreshRescues(cache: QueryClient) {
  await Promise.all(
    [['rescues'], ['assigned-reports'], ['rescue-for-animal']].map((queryKey) =>
      cache.invalidateQueries({ queryKey }),
    ),
  );
}
export async function saveRescue(
  record: Rescue,
  addressDetail: string,
  previous?: Rescue,
  oldDetail = '',
) {
  const body: Record<string, unknown> = {};
  if (!previous || previous.memo !== record.memo) body.rescueMemo = record.memo || null;
  if (!previous || previous.status === '진행 중') {
    if (!previous || record.reason !== previous.reason)
      body.rescueReasonCode = reasons[record.reason];
    if (!previous || record.location !== previous.location) body.rescueAddress = record.location;
    if (!previous || addressDetail !== oldDetail) body.rescueAddressDetail = addressDetail || null;
    if (!previous || previous.date !== record.date) body.rescuedDttm = record.date + ':00+09:00';
  }
  if (!previous) {
    if (record.reportId) body.foundReportSeq = Number(record.reportId);
    const result = await shelterRequest<{ rescueSeq: number }>('/rescues', {
      method: 'POST',
      json: body,
    });
    if (!result?.rescueSeq) throw new Error('접수한 구조 번호를 확인할 수 없습니다.');
    return String(result.rescueSeq);
  }
  if (Object.keys(body).length)
    await shelterRequest('/rescues/' + encodeURIComponent(previous.id), {
      method: 'PATCH',
      json: body,
    });
  return previous.id;
}
export const finishRescue = (id: string, status: RescueStatus, reason?: string) =>
  shelterRequest('/rescues/' + encodeURIComponent(id) + '/status', {
    method: 'PATCH',
    json: { status: statusCode[status], resultReason: reason },
  });
