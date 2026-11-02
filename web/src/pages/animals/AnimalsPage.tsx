import { ShelterPageLayout } from '@/app/layouts/ShelterPageLayout';
import { useNavigate, useSearchParams } from 'react-router';
import { AnimalCard } from '@/shared/ui/AnimalCard';
import { Button } from '@/shared/ui/Button';
import { Pagination } from '@/shared/ui/DataTable';
import { FilterSelect, ListPanel } from '@/shared/ui/ListPanel';
import { useShelterDemo } from '@/features/shelter-demo/store';
import { RecordBadge } from '@/features/shelter-demo/Page';
import { animalFilters, readAnimalFilters, selectAnimals } from './model';
import { sampleReferenceDate } from './sample-data';
import { animalStatuses } from './animal-statuses';
import './animals.css';

const PAGE_SIZE = 12;

export function AnimalsPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const animals = useShelterDemo((s) => s.animals);
  const filters = readAnimalFilters(params);
  const statusTabs = [['', '전체'], ...Object.entries(animalStatuses)] as const;
  const activeTab = Math.max(0, statusTabs.findIndex(([value]) => value === filters.status));
  const visible = selectAnimals(animals, params, sampleReferenceDate);
  const pageCount = Math.ceil(visible.length / PAGE_SIZE);
  const page = Math.max(1, Math.min(pageCount, Math.floor(Number(params.get('page'))) || 1));
  const updateFilter = (key: string, value: string) => {
    setParams((previous) => {
      const next = new URLSearchParams(previous);
      next.delete('page');
      if (value) next.set(key, value);
      else next.delete(key);
      return next;
    });
  };
  return (
    <div className="animals-page">
      <ShelterPageLayout>
        <h1 className="shelter-sr-only">보호동물 목록</h1>
        {/* <p className="animals-page__sample">예시 데이터로 표시하고 있습니다.</p> */}
        <ListPanel
          header={
            <div className="record-status-filters" role="group" aria-label="상태 필터">
              {statusTabs.map(([value, label]) => (
                <button type="button" key={value} aria-pressed={filters.status === value}
                  onClick={() => updateFilter('status', value)}>
                  {label} <RecordBadge status={label === '전체' ? '' : label}>{animals.filter((animal) => !value || animal.status === value).length}</RecordBadge>
                </button>
              ))}
              <span className="record-status-underline" aria-hidden="true"
                style={{ transform: `translateX(${activeTab * 100}%)` }} />
            </div>
          }
          total={visible.length}
          unit="마리"
          filters={animalFilters.filter((filter) => filter.key !== 'status').map((filter) => (
            <FilterSelect
              key={filter.key}
              variant="filter"
              label={filter.label}
              value={filters[filter.key]}
              onChange={(event) => updateFilter(filter.key, event.target.value)}
            >
              {filter.options.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </FilterSelect>
          ))}
          actions={
            <>
              <Button
                variant="secondary"
                className="filter-reset"
                onClick={() => setParams({})}
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
              <Button className="animals-page__register" onClick={() => navigate('/animals/new')}>
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  aria-hidden="true"
                >
                  <path d="M12 5v14 M5 12h14" />
                </svg>
                동물 등록하기
              </Button>
            </>
          }
          sort={
            <FilterSelect
              variant="sort"
              label="정렬"
              value={params.get('sort') === 'oldest' ? 'oldest' : 'latest'}
              onChange={(event) => updateFilter('sort', event.target.value)}
            >
              <option value="latest">등록일 최신순</option>
              <option value="oldest">등록일 오래된순</option>
            </FilterSelect>
          }
          emptyMessage={
            <>
              <strong>조건에 맞는 보호동물이 없어요.</strong>
              <span>필터 조건을 변경하거나 초기화해 주세요.</span>
            </>
          }
        >
          {visible.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE).map((animal) => (
            <AnimalCard
              key={animal.id}
              {...animal}
              onClick={() => navigate({ pathname: `/animals/${animal.id}`, search: params.toString() })}
            />
          ))}
        </ListPanel>
        {pageCount > 1 && (
          <Pagination
            page={page}
            pageCount={pageCount}
            onPageChange={(value) => updateFilter('page', value === 1 ? '' : String(value))}
          />
        )}
      </ShelterPageLayout>
    </div>
  );
}
