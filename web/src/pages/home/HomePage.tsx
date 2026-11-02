import { Link, useNavigate } from 'react-router';
import { ShelterPageLayout } from '@/app/layouts/ShelterPageLayout';
import { ActivityCard } from '@/shared/ui/ActivityCard';
import { StatCard } from '@/shared/ui/StatCard';
import { RecordBadge } from '@/features/shelter-demo/Page';
import { useShelterDemo } from '@/features/shelter-demo/store';
import { initialReports } from '@/pages/records/reports-data';
import './home.css';

export function HomePage() {
  const navigate = useNavigate();
  const protectedAnimalCount = useShelterDemo(
    (s) => s.animals.filter((animal) => animal.status === 'protected').length,
  );
  const adoptions = useShelterDemo((s) => s.adoptions);
  const rescues = useShelterDemo((s) => s.rescues);
  const activeRescues = rescues.filter((r) => r.status === '진행 중');
  const sections = [
    {
      title: '입양 신청 내역',
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
      href: '/rescues?status=진행+중',
      items: [...activeRescues]
        .sort((a, b) => b.date.localeCompare(a.date))
        .slice(0, 3)
        .map((r) => ({
          title: r.location,
          description: `구조 날짜 ${r.date.slice(0, 10)} · ${r.source}`,
          badge: <RecordBadge status={r.status} />,
          href: `/rescues/${r.id}`,
        })),
    },
    {
      title: '발견 신고',
      href: '/reports',
      items: [...initialReports]
        .sort((a, b) => b.foundAt.localeCompare(a.foundAt))
        .slice(0, 3)
        .map((r) => ({
          title: r.name,
          description: `${r.location} · ${r.foundAt.slice(0, 10)} 발견`,
          badge: <RecordBadge status={r.reportStatus} />,
          href: '/reports',
        })),
    },
  ];
  return (
    <div className="home-page">
      <ShelterPageLayout>
        <div className="home-page__dashboard">
          <h1 className="shelter-sr-only">보호소 홈</h1>
          {/* <p className="home-page__sample">예시 데이터로 표시하고 있습니다.</p> */}
          <div className="home-page__stats">
            <Link
              className="home-stat--adoption"
              to="/adoptions"
              aria-label={`입양신청 ${adoptions.length}건 목록 보기`}
            >
              <StatCard label="입양 신청" value={`${adoptions.length}건`} />
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
              aria-label={`진행 중인 구조 ${activeRescues.length}건 목록 보기`}
            >
              <StatCard label="진행 중인 구조" value={`${activeRescues.length}건`} tone="orange" />
            </Link>
          </div>
          <div className="home-page__sections">
            {sections.map((section) => (
              <section
                key={section.title}
                className="home-page__section"
                aria-label={section.title}
              >
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
                  {section.items.map((item, index) => (
                    <ActivityCard key={index} {...item} onClick={() => navigate(item.href)} />
                  ))}
                </div>
              </section>
            ))}
          </div>
        </div>
      </ShelterPageLayout>
    </div>
  );
}
