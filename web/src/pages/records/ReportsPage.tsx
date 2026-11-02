import { useSearchParams, Link } from 'react-router';
import { DemoPage, RecordBadge } from '@/features/shelter-demo/Page';
import { useShelterDemo } from '@/features/shelter-demo/store';
import { Pagination } from '@/shared/ui/DataTable';
import { FilterSelect } from '@/shared/ui/ListPanel';
import { Button } from '@/shared/ui/Button';
import { initialReports } from './reports-data';
import { sampleReferenceDate } from '@/pages/animals/sample-data';

const regions = [...new Set(initialReports.map((r) => r.location.split(' ')[0]))];
const periods = [7, 30, 90];

const reportTabs = [
  ['', '전체'],
  ['new', '미접수'],
  ['active', '진행 중·완료'],
  ['closed', '실패·취소 이력'],
] as const;

export function ReportsPage() {
  const [params, setParams] = useSearchParams();
  const rescues = useShelterDemo((s) => s.rescues);
  const tabIndex = Math.max(
    0,
    reportTabs.findIndex(([value]) => value === params.get('kind')),
  );
  const kind = reportTabs[tabIndex]![0];
  const days = periods.find((n) => String(n) === params.get('period'));
  const since = new Date(sampleReferenceDate);
  since.setUTCDate(since.getUTCDate() - (days ?? 1) + 1);
  const firstDay = since.toISOString().slice(0, 10);
  const reportState = { reportSearch: params.toString() };
  const reports = initialReports.map((report) => ({
    ...report,
    rescue: rescues.find((r) => r.reportId === report.id || r.source === `신고 #${report.id}`),
  }));
  const reportKind = (report: (typeof reports)[number]) =>
    !report.rescue
      ? 'new'
      : ['진행 중', '완료'].includes(report.rescue.status)
        ? 'active'
        : 'closed';
  const rows = reports
    .filter(
      (r) =>
        (!params.get('species') || r.species === params.get('species')) &&
        (!params.get('status') || r.reportStatus === params.get('status')) &&
        (!params.get('region') || r.location.split(' ')[0] === params.get('region')) &&
        (!days ||
          (r.foundAt.slice(0, 10) >= firstDay && r.foundAt.slice(0, 10) <= sampleReferenceDate)) &&
        (!kind || reportKind(r) === kind),
    )
    .sort((a, b) =>
      params.get('sort') === 'oldest'
        ? a.foundAt.localeCompare(b.foundAt)
        : b.foundAt.localeCompare(a.foundAt),
    );
  const pageCount = Math.max(1, Math.ceil(rows.length / 8));
  const page = Math.min(pageCount, Math.max(1, Math.floor(Number(params.get('page'))) || 1));
  const change = (key: string, value: string) =>
    setParams((old) => {
      const next = new URLSearchParams(old);
      next.delete('page');
      if (value) next.set(key, value);
      else next.delete(key);
      return next;
    });
  return (
    <DemoPage title="제보 목록" hideTitle>
      <div className="record-status-filters report-tabs" role="group" aria-label="접수 상태">
        {reportTabs.map(([value, label]) => (
          <button
            type="button"
            key={value}
            aria-pressed={kind === value}
            onClick={() => change('kind', value)}
          >
            {label}{' '}
            <RecordBadge
              status={
                value === 'active'
                  ? '진행 중'
                  : value === 'closed'
                    ? '실패'
                    : value === 'new'
                      ? '완료'
                      : ''
              }
            >
              {reports.filter((report) => !value || reportKind(report) === value).length}
            </RecordBadge>
          </button>
        ))}
        <span
          className="record-status-underline"
          aria-hidden="true"
          style={{ transform: `translateX(${tabIndex * 100}%)` }}
        />
      </div>
      <div className="record-toolbar">
        <div className="record-filters">
          <FilterSelect
            variant="filter"
            label="신고 상태"
            value={params.get('status') ?? ''}
            onChange={(e) => change('status', e.target.value)}
          >
            <option>신고 진행 중</option>
            <option>신고 종료</option>
          </FilterSelect>
          <FilterSelect
            variant="filter"
            label="발견 기간"
            value={params.get('period') ?? ''}
            onChange={(e) => change('period', e.target.value)}
          >
            {periods.map((n) => (
              <option key={n} value={n}>
                최근 {n}일
              </option>
            ))}
          </FilterSelect>
          <FilterSelect
            variant="filter"
            label="지역"
            value={params.get('region') ?? ''}
            onChange={(e) => change('region', e.target.value)}
          >
            {regions.map((region) => (
              <option key={region} value={region}>
                {region}
              </option>
            ))}
          </FilterSelect>
          <FilterSelect
            variant="filter"
            label="축종"
            value={params.get('species') ?? ''}
            onChange={(e) => change('species', e.target.value)}
          >
            <option value="dog">개</option>
            <option value="cat">고양이</option>
          </FilterSelect>
        </div>
        <Button
          className="filter-reset"
          variant="secondary"
          onClick={() =>
            setParams((old) => {
              const next = new URLSearchParams(old);
              ['status', 'period', 'region', 'species', 'page'].forEach((key) => next.delete(key));
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
      </div>
      <div className="record-toolbar">
        <p role="status">
          총 <strong>{rows.length}</strong>건
        </p>
        <FilterSelect
          variant="sort"
          label="정렬"
          value={params.get('sort') === 'oldest' ? 'oldest' : 'latest'}
          onChange={(e) => change('sort', e.target.value)}
        >
          <option value="latest">발견일 최신순</option>
          <option value="oldest">발견일 오래된순</option>
        </FilterSelect>
      </div>
      <div className="reports-grid">
        {rows.slice((page - 1) * 8, page * 8).map((r) => (
          <article className="report-card" key={r.id}>
            {r.rescue && (
              <Link
                className="report-card__detail"
                state={reportState}
                to={`/rescues/${r.rescue.id}`}
                aria-label={`${r.name} ${r.id} 구조 상세보기`}
              />
            )}
            <div className="report-card__photo">
              <img src={r.image} alt={r.name} />
              <span
                className={`animal-card__status report-card__status animal-card__status--${r.reportStatus === '신고 종료' ? 'closed' : 'protected'}`}
              >
                {r.reportStatus}
              </span>
            </div>
            <div className="report-card__body">
              <h2>{r.name}</h2>
              <p>{r.location}</p>
              <p>발견 일시 · {r.foundAt.replace('T', ' ')}</p>
              <p>{r.traits}</p>
              <p>신고자 | {r.reporter}</p>
              {!r.rescue || ['실패', '취소'].includes(r.rescue.status) ? (
                <Link
                  className={`page-link report-card__action ${r.rescue ? 'report-card__action--retry' : ''}`}
                  state={reportState}
                  to={`/rescues/new?report=${r.id}`}
                >
                  {r.rescue ? '재구조 접수' : '구조 접수'}
                </Link>
              ) : (
                <span
                  className={`report-card__action report-card__action--${r.rescue.status === '완료' ? 'complete' : 'active'}`}
                >
                  구조 {r.rescue.status}
                </span>
              )}
            </div>
          </article>
        ))}
      </div>
      {!rows.length && <p className="detail-box">조건에 맞는 제보가 없습니다.</p>}
      <Pagination
        page={page}
        pageCount={pageCount}
        onPageChange={(p) =>
          setParams((old) => {
            const next = new URLSearchParams(old);
            next.set('page', String(p));
            return next;
          })
        }
      />
    </DemoPage>
  );
}
