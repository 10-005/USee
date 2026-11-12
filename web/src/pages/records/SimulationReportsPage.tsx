import { useQuery } from '@tanstack/react-query';
import { demoMode } from '@/shared/config/demo';
import { detailOptions } from '@/entities/shelter/data/applications';
import { reportListOptions, reportDetailOptions } from '@/entities/shelter/data/simulation-reports';
import { LoadStateMessage } from '@/shared/ui/LoadState';
import { Fragment, useId, useState } from 'react';
import { useLocation, useParams } from 'react-router';
import { getSimulationReports, useAdoption } from '@/entities/shelter/data';
import { WorkPage, MissingRecord, RecordBadge } from '@/shared/ui/WorkPage';
import { LinkButton } from '@/shared/ui/LinkButton';
import './simulation-reports.css';

type DemoReport = ReturnType<typeof getSimulationReports>[number];
type Report = Omit<DemoReport, 'applicationId' | 'summary'> & {
  summary: string | null;
  status?: string;
  total?: number;
};
type Entry = Pick<Report, 'id' | 'animal' | 'start' | 'end'> & {
  total: number;
  temperament?: string;
  days?: number;
};
const expenseGroups = [
  { key: 'meal', label: '식사', color: '#B8DCC8', categories: ['FOOD', 'FOOD_BOWL', 'WATER_BOWL'] },
  { key: 'snack', label: '간식', color: '#F5CCAD', categories: ['SNACK'] },
  { key: 'play', label: '놀이', color: '#C9C3EB', categories: ['TOY', 'CATNIP'] },
  { key: 'furniture', label: '가구', color: '#B9D7ED', categories: ['FURNITURE'] },
  {
    key: 'hygiene',
    label: '위생',
    color: '#F0C2D1',
    categories: ['LITTER_BOX', 'LITTER', 'SHAMPOO', 'GROOMING_KIT', 'POOP_BAG'],
  },
  { key: 'daily', label: '생활', color: '#ECDCAB', categories: ['LEASH', 'MEDICINE', 'EVENT'] },
];
const statColors = { low: '#C82850', medium: '#996A36', high: '#4050E0' };
const statLegend = [
  { tone: 'high', label: '양호', range: '70% 이상' },
  { tone: 'medium', label: '주의', range: '40~69%' },
  { tone: 'low', label: '부족', range: '40% 미만' },
] as const;
const statTone = (value: number) => (value < 40 ? 'low' : value < 70 ? 'medium' : 'high');
const won = (amount: number) => amount.toLocaleString('ko-KR') + '원';
const totalSpent = (report: Report) => report.expenses.reduce((sum, item) => sum + item.amount, 0);

function CategorySpending({ report, total }: { report: Report; total: number }) {
  const chartId = useId();
  const [selected, setSelected] = useState<string>();
  const [expanded, setExpanded] = useState<string>();
  const groups = expenseGroups.map((group) => {
    const purchases = report.expenses
      .filter((item) => item.amount > 0 && group.categories.includes(item.key))
      .sort((a, b) => b.amount - a.amount || a.key.localeCompare(b.key));
    return { ...group, purchases, amount: purchases.reduce((sum, item) => sum + item.amount, 0) };
  });
  const active =
    groups.find((item) => item.key === selected) ?? groups.find((item) => item.amount > 0);
  const point = (angle: number, radius: number) =>
    [100 + radius * Math.cos(angle), 100 + radius * Math.sin(angle)].join(' ');
  let angle = -Math.PI / 2;
  const slices = [];
  for (const item of groups.filter((item) => item.amount > 0)) {
    const start = angle;
    const share = total ? item.amount / total : 0;
    angle += share * Math.PI * 2;
    const middle = (start + angle) / 2;
    slices.push({
      ...item,
      share,
      color: item.color,
      // Two arcs also cover the case where a single category fills the circle.
      path: [
        `M${point(start, 84)} A84 84 0 0 1 ${point(middle, 84)} A84 84 0 0 1 ${point(angle, 84)}`,
        `L${point(angle, 56)} A56 56 0 0 0 ${point(middle, 56)} A56 56 0 0 0 ${point(start, 56)} Z`,
      ].join(' '),
      offset: `translate(${Math.cos(middle) * 7}px, ${Math.sin(middle) * 7}px)`,
    });
  }
  return (
    <div className="sim-breakdown">
      <figure className="sim-category-figure">
        <svg viewBox="0 0 200 200" aria-hidden="true">
          <defs>
            {slices.map((item, i) => (
              <radialGradient
                key={item.label}
                id={chartId + '-' + i}
                gradientUnits="userSpaceOnUse"
                cx="100"
                cy="100"
                r="84"
              >
                <stop offset="0%" stopColor={item.color} />
                <stop offset="75%" stopColor={item.color} />
                <stop offset="91%" stopColor={item.color} stopOpacity=".85" />
                <stop offset="100%" stopColor={item.color} stopOpacity=".72" />
              </radialGradient>
            ))}
          </defs>
          {total === 0 && (
            <circle cx="100" cy="100" r="70" fill="none" stroke="#ebeae5" strokeWidth="28" />
          )}
          {slices.map(
            (item, i) =>
              item.share > 0 && (
                <path
                  key={item.label}
                  d={item.path}
                  fill={`url(#${chartId}-${i})`}
                  className="sim-category-slice"
                  data-active={item.key === active?.key}
                  style={{ transform: item.key === active?.key ? item.offset : undefined }}
                  onPointerEnter={() => setSelected(item.key)}
                  onClick={() => {
                    setSelected(item.key);
                    setExpanded(item.key);
                  }}
                />
              ),
          )}
        </svg>
        <figcaption aria-live="polite">
          {active && (
            <>
              <span>{active.label}</span>
              <strong>{won(active.amount)}</strong>
              <span>{total ? Math.round((active.amount / total) * 100) : 0}%</span>
            </>
          )}
          {!active && <span>지출한 항목이 없어요.</span>}
        </figcaption>
      </figure>
      <div className="sim-legend" role="group" aria-label="지출 카테고리 선택">
        {groups.map((item) => (
          <div key={item.key}>
            <button
              type="button"
              aria-label={item.label + ' ' + won(item.amount)}
              aria-pressed={item.key === active?.key}
              aria-expanded={expanded === item.key}
              aria-controls={chartId + '-' + item.key + '-purchases'}
              style={
                item.key === active?.key
                  ? { background: item.color + '33', borderColor: item.color }
                  : undefined
              }
              onPointerEnter={() => setSelected(item.key)}
              onFocus={() => setSelected(item.key)}
              onClick={() => {
                setSelected(item.key);
                setExpanded((current) => (current === item.key ? undefined : item.key));
              }}
            >
              <i aria-hidden="true" style={{ background: item.color }} />
              <span>{item.label}</span>
              <strong>{won(item.amount)}</strong>
              <span className="sim-purchase-arrow" aria-hidden="true">
                ⌄
              </span>
            </button>
            <div
              id={chartId + '-' + item.key + '-purchases'}
              className="sim-purchases"
              style={{ borderLeftColor: item.color }}
              hidden={expanded !== item.key}
              role="region"
              aria-label={item.label + ' 구매 내역'}
            >
              {item.purchases.length ? (
                item.purchases.map((purchase) => (
                  <div key={purchase.label}>
                    <span>{purchase.label}</span>
                    <span>{won(purchase.amount)}</span>
                  </div>
                ))
              ) : (
                <span>구매 내역이 없어요.</span>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function Spending({ report }: { report: Report }) {
  const total = report.total ?? totalSpent(report);
  const difference = total - report.budget;
  const peak = Math.max(0, ...report.daily);
  const step = Math.max(1000, Math.ceil(peak / 4 / 5000) * 5000);
  const [selectedDay, setSelectedDay] = useState<number>();
  const activeDay = selectedDay ?? report.daily.indexOf(peak);
  return (
    <section className="sim-card">
      <h2>지출 분석</h2>
      <div className="sim-total">
        <strong>{won(total)}</strong>
        <span>
          예상 {won(report.budget)} ·{' '}
          {difference > 0 ? (
            <span className="sim-over-budget">{won(difference)} 초과</span>
          ) : difference < 0 ? (
            <span className="sim-saved-budget">{won(-difference)} 절약</span>
          ) : (
            <span className="sim-even-budget">예산과 일치</span>
          )}
        </span>
      </div>
      <div className="sim-charts">
        <div>
          <h3>카테고리별 지출</h3>
          <CategorySpending report={report} total={total} />
        </div>
        <div>
          <h3>일별 지출</h3>
          <div className="sim-daily-chart">
            <div className="sim-axis" aria-hidden="true">
              {[4, 3, 2, 1, 0].map((tick) => (
                <span key={tick}>{won(tick * step)}</span>
              ))}
            </div>
            <ol className="sim-bars">
              {report.daily.map((amount, i) => (
                <li key={i}>
                  <button
                    type="button"
                    className="sim-bar-track"
                    aria-label={`${i + 1}일차 지출 ${won(amount)}`}
                    aria-pressed={activeDay === i}
                    onPointerEnter={() => setSelectedDay(i)}
                    onFocus={() => setSelectedDay(i)}
                    onClick={() => setSelectedDay(i)}
                  >
                    <span
                      className="sim-bar"
                      style={{
                        height:
                          (amount > 0
                            ? Math.max((10 / 184) * 100, Math.min(100, (amount / (4 * step)) * 100))
                            : 0) + '%',
                      }}
                    >
                      <span className="sim-day-tip" aria-hidden="true">
                        <small>{i + 1}일차 지출</small>
                        <strong>{won(amount)}</strong>
                      </span>
                    </span>
                  </button>
                  <span>{i + 1}일차</span>
                </li>
              ))}
            </ol>
          </div>
        </div>
      </div>
    </section>
  );
}

function FinalStateChart({ state }: { state: Report['state'] }) {
  const titleId = useId();
  const chartId = useId();
  const radius = 92;
  const labelRadius = radius + 46;
  const axes = state.map((item, index) => {
    const angle = -Math.PI / 2 + (index * 2 * Math.PI) / state.length;
    return {
      ...item,
      color: statColors[statTone(item.value)],
      dx: Math.cos(angle),
      dy: Math.sin(angle),
      x: 180 + (Math.cos(angle) * radius * item.value) / 100,
      y: 150 + (Math.sin(angle) * radius * item.value) / 100,
    };
  });
  const outline = axes.map(({ x, y }) => x + ',' + y).join(' ');
  const baseline = axes
    .map(({ dx, dy }) => 180 + dx * radius + ',' + (150 + dy * radius))
    .join(' ');
  return (
    <figure className="sim-radar-panel">
      <svg className="sim-radar" viewBox="-12 -30 384 356" role="img" aria-labelledby={titleId}>
        <title id={titleId}>
          {axes
            .map(
              (item) =>
                item.label +
                ' ' +
                item.value +
                '%, ' +
                statLegend.find((level) => level.tone === statTone(item.value))?.label,
            )
            .join(', ')}
        </title>
        <defs>
          <clipPath id={chartId + '-area'}>
            <polygon points={outline} />
          </clipPath>
          {axes.map((item, index) => {
            const next = axes[(index + 1) % axes.length] ?? item;
            return (
              <Fragment key={item.label}>
                <radialGradient
                  id={chartId + '-fill-' + index}
                  cx={item.x}
                  cy={item.y}
                  r={radius * 1.5}
                  gradientUnits="userSpaceOnUse"
                >
                  <stop offset="0%" stopColor={item.color} stopOpacity=".18" />
                  <stop offset="55%" stopColor={item.color} stopOpacity=".05" />
                  <stop offset="100%" stopColor={item.color} stopOpacity="0" />
                </radialGradient>
                <linearGradient
                  id={chartId + '-edge-' + index}
                  x1={item.x}
                  y1={item.y}
                  x2={next.x}
                  y2={next.y}
                  gradientUnits="userSpaceOnUse"
                >
                  <stop offset="0%" stopColor={item.color} />
                  <stop offset="100%" stopColor={next.color} />
                </linearGradient>
              </Fragment>
            );
          })}
        </defs>
        {[radius + 12, radius * 0.77, radius * 0.42].map((r) => (
          <circle key={r} cx="180" cy="150" r={r} fill="white" />
        ))}
        {axes.map(({ label, dx, dy }) => (
          <line
            key={label}
            x1="180"
            y1="150"
            x2={180 + radius * dx}
            y2={150 + radius * dy}
            stroke="#DFEAE5"
            strokeWidth=".8"
          />
        ))}
        <polygon
          points={baseline}
          fill="none"
          stroke="#C4C6DF"
          strokeWidth="1"
          strokeLinejoin="round"
        />
        <g clipPath={'url(#' + chartId + '-area)'}>
          {axes.map((item, index) => (
            <rect
              key={item.label}
              x={180 - radius}
              y={150 - radius}
              width={radius * 2}
              height={radius * 2}
              fill={'url(#' + chartId + '-fill-' + index + ')'}
            />
          ))}
        </g>
        {axes.map((item, index) => {
          const next = axes[(index + 1) % axes.length] ?? item;
          return (
            <line
              key={item.label}
              x1={item.x}
              y1={item.y}
              x2={next.x}
              y2={next.y}
              stroke={'url(#' + chartId + '-edge-' + index + ')'}
              strokeWidth="1.6"
              strokeLinecap="round"
            />
          );
        })}
        {axes.map((item) => (
          <circle
            key={item.label}
            cx={item.x}
            cy={item.y}
            r="3.2"
            fill={item.color}
            stroke="white"
            strokeWidth="1"
          />
        ))}
        {axes.map(({ label, value, dx, dy }) => (
          <g
            key={label}
            transform={
              'translate(' + (180 + labelRadius * dx) + ' ' + (150 + labelRadius * dy) + ')'
            }
            textAnchor="middle"
          >
            <text>{label}</text>
            <text y="18" className="sim-radar-value" data-level={statTone(value)}>
              {value}%
            </text>
          </g>
        ))}
      </svg>
      <figcaption>
        <div className="sim-stat-legend">
          {statLegend.map((item) => (
            <span key={item.tone}>
              <i style={{ background: statColors[item.tone] }} />
              {item.label} {item.range}
            </span>
          ))}
        </div>
        <span className="sim-radar-baseline">100% 기준선</span>
      </figcaption>
    </figure>
  );
}

function ReportDetails({ report }: { report: Report }) {
  return (
    <>
      <section className="sim-card sim-summary">
        <small>모의 입양 동물</small>
        <h2>
          {report.animal} <RecordBadge status={report.status ?? '완료'} />
        </h2>
        <p>
          {report.temperament} · {report.start} ~ {report.end}
        </p>
        <p>{report.summary ?? '요약 준비 중'}</p>
      </section>
      <Spending report={report} />
      <div className="sim-bottom">
        <section className="sim-card">
          <h2>최종 상태</h2>
          <FinalStateChart state={report.state} />
        </section>
        <section className="sim-card">
          <h2>돌봄 활동</h2>
          <dl className="sim-care">
            <div>
              <dt>총 돌봄</dt>
              <dd>{report.care}회</dd>
            </div>
            <div>
              <dt>산책</dt>
              <dd>{report.walks}회</dd>
            </div>
            <div>
              <dt>돌발 이벤트</dt>
              <dd>{report.events}건</dd>
            </div>
          </dl>
          <div className="sim-event-result">
            <h3>돌발 이벤트 처리 결과</h3>
            <div className="sim-event-track" aria-hidden="true">
              <b
                style={{
                  width: (report.events ? (report.resolved / report.events) * 100 : 0) + '%',
                }}
              />
            </div>
            <div className="sim-event-labels">
              <span>해결 {report.resolved}건</span>
              <span>미해결 {report.events - report.resolved}건</span>
            </div>
          </div>
        </section>
      </div>
    </>
  );
}

export function SimulationReportsPage() {
  const { id = '', reportId } = useParams();
  return demoMode ? (
    <DemoReports id={id} reportId={reportId} />
  ) : (
    <LiveReports id={id} reportId={reportId} />
  );
}
type RouteProps = { id: string; reportId?: string };
function DemoReports({ id, reportId }: RouteProps) {
  const { search } = useLocation();
  const application = useAdoption(id);
  if (!application) return <MissingRecord back={'/adoptions' + search} />;
  const reports = getSimulationReports(id);
  const selected = reports.find((item) => item.id === reportId);
  if (reportId && !selected)
    return <MissingRecord back={'/adoptions/' + id + '/simulation-reports' + search} />;
  return (
    <ReportView
      application={application}
      reports={reports.map((item) => ({
        ...item,
        total: totalSpent(item),
        days: item.daily.length,
      }))}
      selected={selected}
    />
  );
}
function LiveReports({ id, reportId }: RouteProps) {
  const { search } = useLocation();
  const application = useQuery(detailOptions(id));
  const list = useQuery(reportListOptions(id));
  const belongs = !!reportId && !!list.data?.some((item) => item.id === reportId);
  const detail = useQuery({
    ...reportDetailOptions(id, reportId ?? ''),
    enabled: belongs,
    refetchInterval: (query) =>
      query.state.data?.summary === null && !query.state.error ? 10000 : false,
  });
  const back = '/adoptions/' + id + (reportId ? '/simulation-reports' : '') + search;
  const failed = [application, list, ...(belongs ? [detail] : [])].find((q) => q.error);
  if (failed || !application.data || !list.data || (belongs && !detail.data))
    return (
      <WorkPage title="모의 입양 리포트" back={back}>
        <LoadStateMessage
          state={
            failed
              ? {
                  status: 'error',
                  message: failed.error?.message,
                  retry: () => void failed.refetch(),
                }
              : { status: 'loading' }
          }
        >
          {null}
        </LoadStateMessage>
      </WorkPage>
    );
  if (reportId && !belongs) return <MissingRecord back={back} />;
  return (
    <ReportView
      application={application.data.record}
      reports={list.data}
      selected={belongs ? detail.data : undefined}
    />
  );
}
function ReportView({
  application,
  reports,
  selected,
}: {
  application: { id: string; applicant: string };
  reports: Entry[];
  selected?: Report;
}) {
  const { search } = useLocation();
  const applicationUrl = '/adoptions/' + application.id;
  const listUrl = applicationUrl + '/simulation-reports';
  return (
    <WorkPage
      title={selected ? '모의 입양 리포트 상세' : '신청자 모의 입양 리포트'}
      titleDescription={
        <p className="sim-applicant">
          {application.applicant} · 신청번호 {application.id}
        </p>
      }
      back={(selected ? listUrl : applicationUrl) + search}
      backLabel={selected ? '리포트 목록' : '입양신청 상세'}
    >
      <div className="sim-reports">
        {selected ? (
          <ReportDetails report={selected} />
        ) : (
          <section className="sim-card">
            <h2>
              {demoMode ? '완료된 리포트' : '종료된 리포트'} <small>{reports.length}건</small>
            </h2>
            {reports.length === 0 ? (
              <div className="sim-empty">
                <strong>아직 완료된 모의 입양 리포트가 없습니다</strong>
                <p>신청자가 모의 입양을 마치면 이곳에서 결과를 확인할 수 있어요.</p>
              </div>
            ) : (
              reports.map((item) => (
                <article key={item.id} className="sim-list-row">
                  <div>
                    <small>모의 입양 동물</small>
                    <h3>{item.animal}</h3>
                    {item.temperament && <span>{item.temperament}</span>}
                  </div>
                  <div>
                    <small>체험 기간</small>
                    <strong>
                      {item.start.split(' ')[0]} ~ {item.end.split(' ')[0]}
                    </strong>
                    {item.days && <span>{item.days}일 체험</span>}
                  </div>
                  <div>
                    <small>총 지출</small>
                    <strong>{won(item.total)}</strong>
                  </div>
                  <LinkButton to={listUrl + '/' + item.id + search}>상세 보기</LinkButton>
                </article>
              ))
            )}
          </section>
        )}
      </div>
    </WorkPage>
  );
}
