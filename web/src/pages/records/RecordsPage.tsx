import { useSearchParams, Link } from 'react-router';
import { DataTable, Pagination, type TableColumn } from '@/shared/ui/DataTable';
import { FilterSelect } from '@/shared/ui/ListPanel';
import { Button } from '@/shared/ui/Button';
import { DemoPage, RecordBadge } from '@/features/shelter-demo/Page';
import { adoptionStates, rescueStates, useShelterDemo } from '@/features/shelter-demo/store';
import { sampleReferenceDate } from '@/pages/animals/sample-data';

const rescuePeriods = [7, 30, 90];

export function RecordsPage({ kind }: { kind: 'adoptions' | 'rescues' }) {
  const adoptions = useShelterDemo((s) => s.adoptions);
  const rescues = useShelterDemo((s) => s.rescues);
  const [params, setParams] = useSearchParams();
  const rescue = kind === 'rescues';
  const states = rescue ? rescueStates : adoptionStates;
  const statusOptions: string[] = ['', ...states];
  const animalId = rescue ? null : params.get('animalId');
  const scopedAdoptions = animalId ? adoptions.filter((r) => r.animalId === animalId) : adoptions;
  const source = rescue ? rescues : scopedAdoptions;
  const status = states.find((s) => s === params.get('status')) ?? '';
  const period = rescue && rescuePeriods.find((days) => String(days) === params.get('period'));
  const periodStart = new Date(sampleReferenceDate);
  if (period) periodStart.setUTCDate(periodStart.getUTCDate() - period + 1);
  const startDate = periodStart.toISOString().slice(0, 10);
  const change = (key: string, value: string) =>
    setParams((old) => {
      const next = new URLSearchParams(old);
      next.delete('page');
      if (value) next.set(key, value);
      else next.delete(key);
      return next;
    });
  const rows = source.filter(
    (r) =>
      (!status || r.status === status) &&
      (!params.get('species') || r.name.includes(params.get('species')!)) &&
      (!params.get('source') ||
        ('source' in r &&
          (params.get('source') === 'direct'
            ? r.source === '직접 접수'
            : r.source !== '직접 접수'))) &&
      (!period || (r.date.slice(0, 10) >= startDate && r.date.slice(0, 10) <= sampleReferenceDate)),
  );
  const sorted = [...rows].sort((a, b) =>
    params.get('sort') === 'oldest' ? a.date.localeCompare(b.date) : b.date.localeCompare(a.date),
  );
  const pageCount = Math.max(1, Math.ceil(rows.length / 8));
  const page = Math.min(pageCount, Math.max(1, Math.floor(Number(params.get('page'))) || 1));
  type Row = (typeof source)[number];
  const columns: TableColumn<Row>[] = [
    {
      id: 'status',
      header: '상태',
      width: rescue ? '96px' : '13%',
      render: (r) => <RecordBadge status={r.status} />,
    },
    {
      id: 'id',
      header: rescue ? '등록번호' : '신청번호',
      width: rescue ? '144px' : '17%',
      render: (r) => r.id,
    },
    {
      id: 'animal',
      header: '동물 정보',
      width: rescue ? '280px' : '29%',
      render: (r) => (
        <div className="ui-table__animal">
          <img src={r.image} alt="예시 동물" />
          <div>
            <strong>{r.name}</strong>
            <small>{'animalId' in r ? r.animalId : '0살 | 암컷 | 갈색 | 3kg'}</small>
          </div>
        </div>
      ),
    },
    {
      id: 'date',
      header: rescue ? '구조일자' : '신청일자',
      width: rescue ? '140px' : '16%',
      render: (r) => r.date.slice(0, 10),
    },
    {
      id: 'owner',
      header: rescue ? '접수 경로' : '신청자',
      width: rescue ? undefined : '12%',
      render: (r) => ('applicant' in r ? r.applicant : r.source),
    },
    ...(rescue ? [{ id: 'memo', header: '비고', width: '100px', render: () => '-' }] : []),
    {
      id: 'detail',
      header: '상세',
      width: rescue ? '130px' : '13%',
      render: (r) => (
        <Link
          className="record-link"
          to={{ pathname: `/${kind}/${r.id}`, search: params.toString() }}
        >
          상세보기
        </Link>
      ),
    },
  ];
  return (
    <DemoPage title={rescue ? '구조일지' : '입양신청 목록'} hideTitle>
      <div className="record-status-filters" role="group" aria-label="상태 필터">
        {statusOptions.map((value) => {
          const count = source.filter((r) => !value || r.status === value).length;
          return (
            <button
              key={value}
              type="button"
              aria-pressed={status === value}
              onClick={() => change('status', value)}
            >
              {value || '전체'} <RecordBadge status={value}>{count}</RecordBadge>
            </button>
          );
        })}
        <span
          className="record-status-underline"
          aria-hidden="true"
          style={{ transform: `translateX(${statusOptions.indexOf(status) * 100}%)` }}
        />
      </div>
      {rescue && (
        <div className="record-toolbar">
          <div className="record-filters">
            <FilterSelect
              variant="filter"
              label="기간"
              value={params.get('period') ?? ''}
              onChange={(e) => change('period', e.target.value)}
            >
              {rescuePeriods.map((days) => (
                <option key={days} value={days}>
                  최근 {days}일
                </option>
              ))}
            </FilterSelect>
            <FilterSelect
              variant="filter"
              label="축종"
              value={params.get('species') ?? ''}
              onChange={(e) => change('species', e.target.value)}
            >
              <option value="[개]">개</option>
              <option value="[고양이]">고양이</option>
            </FilterSelect>
            <FilterSelect
              variant="filter"
              label="접수 경로"
              value={params.get('source') ?? ''}
              onChange={(e) => change('source', e.target.value)}
            >
              <option value="direct">직접 접수</option>
              <option value="report">제보 접수</option>
            </FilterSelect>
          </div>
          <div className="page-actions rescue-actions">
            <Button
              variant="secondary"
              className="filter-reset"
              onClick={() =>
                setParams((old) => {
                  const next = new URLSearchParams(old);
                  ['period', 'species', 'source', 'page'].forEach((key) => next.delete(key));
                  return next;
                })
              }
            >
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d="M3 4v6h6 M3.7 10a8.5 8.5 0 1 1 .7 6" />
              </svg>
              필터 초기화
            </Button>
            <Link className="page-link rescue-actions__reports" to="/reports">
              <svg
                aria-hidden="true"
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinejoin="round"
              >
                <path d="M4 4h16v12H9l-5 4z" />
              </svg>
              제보 구조 접수
            </Link>
            <Link className="page-link rescue-actions__new" to="/rescues/new">
              <svg
                aria-hidden="true"
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
              >
                <path d="M12 5v14 M5 12h14" />
              </svg>
              직접 구조 접수
            </Link>
          </div>
        </div>
      )}
      <div className="record-toolbar">
        <p role="status">
          {animalId && <span>{animalId} · </span>}총 <strong>{rows.length}</strong>건
        </p>
        <FilterSelect
          variant="sort"
          label="정렬"
          value={params.get('sort') === 'oldest' ? 'oldest' : 'latest'}
          onChange={(e) => change('sort', e.target.value)}
        >
          <option value="latest">등록일 최신순</option>
          <option value="oldest">등록일 오래된순</option>
        </FilterSelect>
      </div>
      <DataTable
        className={rescue ? 'records-table' : 'records-table adoption-table'}
        caption={rescue ? '구조일지 목록' : '입양신청 목록'}
        columns={columns}
        rows={sorted.slice((page - 1) * 8, page * 8)}
        rowKey={(r) => r.id}
      />
      <Pagination
        page={page}
        pageCount={pageCount}
        onPageChange={(value) =>
          setParams((old) => {
            const next = new URLSearchParams(old);
            next.set('page', String(value));
            return next;
          })
        }
      />
    </DemoPage>
  );
}
