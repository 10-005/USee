import {
  assignedOptions,
  assignedRefreshOptions,
  rescueListOptions,
} from '@/entities/shelter/data/rescues';
import { animalListOptions } from '@/entities/shelter/data/animals';
import { readAnimalFilters } from '@/pages/animals/model';
import { useQuery } from '@tanstack/react-query';
import { receivedOptions } from '@/entities/shelter/data/applications';
import { demoMode } from '@/shared/config/demo';
import { Link, useNavigate } from 'react-router';
import { ActivityCard } from '@/shared/ui/ActivityCard';
import { StatCard } from '@/shared/ui/StatCard';
import { RecordBadge } from '@/shared/ui/WorkPage';
import { useAnimals, useAdoptions, useRescues } from '@/entities/shelter/data';
import { useReports } from '@/entities/shelter/data';
import './home.css';

export function HomePage() {
  const navigate = useNavigate();
  const demoReports = useReports();
  const reportParams = new URLSearchParams();
  const assigned = useQuery({
    ...assignedOptions(reportParams),
    ...assignedRefreshOptions(reportParams),
    enabled: !demoMode,
  });
  const processing = useQuery({
    ...rescueListOptions(new URLSearchParams({ status: '진행 중' })),
    enabled: !demoMode,
  });
  const reports = demoMode
    ? [...demoReports].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    : (assigned.data?.content ?? []);
  const animalList = useQuery({
    ...animalListOptions(readAnimalFilters(new URLSearchParams()), 1, false),
    enabled: !demoMode,
  });
  const demoProtectedCount = useAnimals().filter((animal) => animal.status === 'protected').length;
  const protectedAnimalCount = demoMode
    ? demoProtectedCount
    : (animalList.data?.counts.protected ?? '—');
  const demoAdoptions = useAdoptions();
  const received = useQuery({ ...receivedOptions(new URLSearchParams()), enabled: !demoMode });
  const adoptions = demoMode ? demoAdoptions : (received.data?.content ?? []);
  const adoptionCount = demoMode ? adoptions.length : (received.data?.totalElements ?? '—');
  const rescues = useRescues();
  const activeRescues = demoMode
    ? rescues.filter((r) => r.status === '진행 중')
    : (processing.data?.page.content ?? []);
  const activeCount = demoMode ? activeRescues.length : (processing.data?.counts.PROCESSING ?? '—');
  const sections = [
    {
      title: '입양 신청 내역',
      query: received,
      emptyTitle: '아직 접수된 입양 신청이 없어요.',
      emptyHint: '입양 신청이 들어오면 여기에서 확인할 수 있어요.',
      href: '/adoptions',
      items: [...adoptions]
        .sort((a, b) => {
          const pendingOrder = Number(b.status === '심사 대기') - Number(a.status === '심사 대기');
          return pendingOrder || b.date.localeCompare(a.date);
        })
        .slice(0, 6)
        .map((r) => ({
          title: `${r.name} / ${r.applicant}`,
          description: `${r.date} 접수`,
          badge: <RecordBadge status={r.status} />,
          href: `/adoptions/${r.id}`,
        })),
    },
    {
      title: '진행 중인 구조',
      query: processing,
      emptyTitle: '현재 진행 중인 구조가 없어요.',
      emptyHint: '구조를 접수하면 진행 상황이 여기에 표시돼요.',
      href: '/rescues?status=진행+중',
      items: [...activeRescues]
        .sort((a, b) => b.date.localeCompare(a.date))
        .slice(0, 3)
        .map((r) => ({
          title: r.location || r.name,
          description: `구조 일시 ${r.date.replace('T', ' ')} · ${r.source.replace(/^신고 #/, '발견 신고 #')}`,
          badge: <RecordBadge status={r.status} />,
          href: `/rescues/${r.id}`,
        })),
    },
    {
      title: '발견 신고',
      query: assigned,
      emptyTitle: '아직 배정된 발견 신고가 없어요.',
      emptyHint: '보호소에 새 신고가 배정되면 여기에 표시돼요.',
      href: '/reports',
      items: reports.slice(0, 3).map((r) => ({
        title: r.name,
        description: `${r.location} · ${r.foundAt.slice(0, 10)} 발견`,
        badge: <RecordBadge status={r.reportStatus} />,
        href: '/reports',
      })),
    },
  ];
  return (
    <div className="home-page">
      <div className="home-page__dashboard">
        <h1 className="shelter-sr-only">보호소 홈</h1>
        <div className="home-page__stats">
          <Link
            className="home-stat--adoption"
            to="/adoptions"
            aria-label={`입양 신청 ${adoptionCount}건 목록 보기`}
          >
            <StatCard label="입양 신청" value={`${adoptionCount}건`} />
          </Link>
          <Link
            className="home-stat--animals"
            to="/animals?status=protected"
            aria-label={`보호동물 ${protectedAnimalCount}마리 목록 보기`}
          >
            <StatCard label="보호동물" value={`${protectedAnimalCount}마리`} tone="green" />
          </Link>
          <Link
            className="home-stat--rescue"
            to="/rescues?status=진행+중"
            aria-label={`진행 중인 구조 ${activeCount}건 목록 보기`}
          >
            <StatCard label="진행 중인 구조" value={`${activeCount}건`} tone="orange" />
          </Link>
        </div>
        <div className="home-page__sections">
          {sections.map((section) => (
            <section key={section.title} className="home-page__section" aria-label={section.title}>
              <div className="home-page__section-heading">
                <h2>{section.title}</h2>
                <button
                  type="button"
                  aria-label={`${section.title} 더보기`}
                  onClick={() => navigate(section.href)}
                >
                  <span aria-hidden="true">+</span>
                  더보기
                </button>
              </div>
              <div className="home-page__activities">
                {!demoMode && !section.query.data ? (
                  <p role="status">{section.query.error?.message ?? '불러오는 중입니다.'}</p>
                ) : section.items.length === 0 ? (
                  <div
                    className={
                      section.query === processing
                        ? 'home-page__empty home-page__empty--rescue'
                        : 'home-page__empty'
                    }
                    role="status"
                  >
                    <strong>{section.emptyTitle}</strong>
                    <p>{section.emptyHint}</p>
                  </div>
                ) : null}
                {section.items.map((item, index) => (
                  <ActivityCard key={index} {...item} onClick={() => navigate(item.href)} />
                ))}
              </div>
            </section>
          ))}
        </div>
      </div>
    </div>
  );
}
