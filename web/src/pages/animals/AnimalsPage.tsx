import { shelterAnimalLabel } from '@/entities/shelter/animal-label';
import { useQueries, useQuery } from '@tanstack/react-query';
import {
  animalDetailOptions,
  animalListOptions,
  pageSize as PAGE_SIZE,
} from '@/entities/shelter/data/animals';
import { demoMode } from '@/shared/config/demo';
import type { LoadState } from '@/shared/ui/LoadState';
import type { ShelterAnimal } from '@/entities/shelter/model';
import { useNavigate, useSearchParams } from 'react-router';
import { AnimalCard } from '@/shared/ui/AnimalCard';
import { Button } from '@/shared/ui/Button';
import { Pagination } from '@/shared/ui/DataTable';
import { FilteredListEmpty, FilterSelect, ListPanel } from '@/shared/ui/ListPanel';
import { useAnimalsResult } from '@/entities/shelter/data';
import { RecordBadge } from '@/shared/ui/WorkPage';
import { animalFilters, readAnimalFilters, selectAnimals } from './model';
import { referenceDate } from '@/entities/shelter/data';
import { animalStatuses } from './animal-statuses';
import './animals.css';

export function AnimalsPage() {
  return demoMode ? <DemoList /> : <LiveList />;
}
function DemoList() {
  const [params] = useSearchParams();
  const { data: animals, loadState } = useAnimalsResult();
  const visible = selectAnimals(animals, params, referenceDate);
  const page = Math.max(
    1,
    Math.min(Math.ceil(visible.length / PAGE_SIZE), Math.floor(Number(params.get('page'))) || 1),
  );
  const counts = Object.fromEntries(
    ['', ...Object.keys(animalStatuses)].map((key) => [
      key,
      animals.filter((a) => !key || a.status === key).length,
    ]),
  );
  return (
    <AnimalList
      animals={visible.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)}
      total={visible.length}
      counts={counts}
      page={page}
      loadState={loadState}
    />
  );
}
function LiveList() {
  const [params] = useSearchParams();
  const requested = Number(params.get('page'));
  const page = Number.isSafeInteger(requested) && requested > 0 ? requested : 1;
  const query = useQuery(
    animalListOptions(readAnimalFilters(params), page, params.get('sort') === 'oldest'),
  );
  const animals = query.data?.animals ?? [];
  const missing = animals.filter((animal) => animal.needsDetailDescription);
  const details = useQueries({
    queries: missing.map(({ id }) => ({ ...animalDetailOptions(id), staleTime: 60_000 })),
  });
  const descriptions = new Map(missing.map(({ id }, i) => [id, details[i]?.data?.description]));
  return (
    <AnimalList
      animals={animals.map((animal) => ({
        ...animal,
        description: descriptions.get(animal.id) ?? animal.description,
      }))}
      total={query.data?.total ?? 0}
      counts={query.data?.counts ?? {}}
      page={query.data?.page ?? 1}
      loadState={
        query.error
          ? { status: 'error', message: query.error.message, retry: () => void query.refetch() }
          : query.isPending
            ? { status: 'loading' }
            : { status: 'ready' }
      }
    />
  );
}
function AnimalList({
  animals,
  total,
  counts,
  page,
  loadState,
}: {
  animals: ShelterAnimal[];
  total: number;
  counts: Record<string, number>;
  page: number;
  loadState: LoadState;
}) {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();

  const filters = readAnimalFilters(params);
  const statusTabs = [['', '전체'], ...Object.entries(animalStatuses)] as const;
  const activeTab = Math.max(
    0,
    statusTabs.findIndex(([value]) => value === filters.status),
  );
  const pageCount = Math.ceil(total / PAGE_SIZE);
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
      <h1 className="shelter-sr-only">보호동물 목록</h1>
      <ListPanel
        loadState={loadState}
        header={
          <div className="record-status-filters" role="group" aria-label="상태 필터">
            {statusTabs.map(([value, label]) => (
              <button
                type="button"
                key={value}
                aria-pressed={filters.status === value}
                onClick={() => updateFilter('status', value)}
              >
                {label}{' '}
                <RecordBadge status={label === '전체' ? '' : label}>
                  {counts[value] ?? '—'}
                </RecordBadge>
              </button>
            ))}
            <span
              className="record-status-underline"
              aria-hidden="true"
              style={{ transform: `translateX(${activeTab * 100}%)` }}
            />
          </div>
        }
        total={total}
        unit="마리"
        filters={animalFilters
          .filter((filter) => filter.key !== 'status')
          .map((filter) => (
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
            <Button variant="secondary" className="filter-reset" onClick={() => setParams({})}>
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
        emptyState={<FilteredListEmpty title="조건에 맞는 보호동물이 없어요." />}
      >
        {animals.map((animal) => (
          <AnimalCard
            key={animal.id}
            {...animal}
            name={shelterAnimalLabel(animal)}
            onClick={() =>
              navigate({ pathname: `/animals/${animal.id}`, search: params.toString() })
            }
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
    </div>
  );
}
