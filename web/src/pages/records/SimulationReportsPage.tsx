import { useId, useState } from 'react';
import { useLocation, useParams } from 'react-router';
import { getSimulationReports, useAdoption } from '@/entities/shelter/data';
import { WorkPage, MissingRecord, RecordBadge } from '@/shared/ui/WorkPage';
import { LinkButton } from '@/shared/ui/LinkButton';
import './simulation-reports.css';

type Report = ReturnType<typeof getSimulationReports>[number];
const expensePalette = [
  '#99e2b4',
  '#88d4ab',
  '#78c6a3',
  '#67b99a',
  '#56ab91',
  '#469d89',
  '#358f80',
  '#248277',
  '#14746f',
  '#036666',
];
const icons = import.meta.glob<string>('../../shared/assets/icons/simulation-*.svg', {
  eager: true,
  import: 'default',
});
const won = (amount: number) => amount.toLocaleString('ko-KR') + '원';
const totalSpent = (report: Report) => report.expenses.reduce((sum, item) => sum + item.amount, 0);

function CategorySpending({ report, total }: { report: Report; total: number }) {
  const chartId = useId();
  const [selected, setSelected] = useState<string>();
  const sorted = [...report.expenses].sort((a, b) => b.amount - a.amount);
  const active = sorted.find((item) => item.label === selected) ?? sorted[0];
  const point = (angle: number, radius: number) =>
    [100 + radius * Math.cos(angle), 100 + radius * Math.sin(angle)].join(' ');
  let angle = -Math.PI / 2;
  const slices = [];
  for (const [index, item] of sorted.entries()) {
    const start = angle;
    const share = total ? item.amount / total : 0;
    angle += share * Math.PI * 2;
    const middle = (start + angle) / 2;
    slices.push({
      ...item,
      share,
      color: expensePalette[index % expensePalette.length],
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
                  data-active={item.label === active?.label}
                  style={{ transform: item.label === active?.label ? item.offset : undefined }}
                  onPointerEnter={() => setSelected(item.label)}
                  onClick={() => setSelected(item.label)}
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
        </figcaption>
      </figure>
      <div className="sim-legend" role="group" aria-label="지출 카테고리 선택">
        {slices.map((item) => (
          <button
            key={item.label}
            type="button"
            aria-pressed={item.label === active?.label}
            onPointerEnter={() => setSelected(item.label)}
            onFocus={() => setSelected(item.label)}
            onClick={() => setSelected(item.label)}
          >
            <i aria-hidden="true" style={{ background: item.color }} />
            <span>{item.label}</span>
            <strong>{won(item.amount)}</strong>
          </button>
        ))}
      </div>
    </div>
  );
}

function Spending({ report }: { report: Report }) {
  const total = totalSpent(report);
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
            won(-difference) + ' 절약'
          ) : (
            '예산과 일치'
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
                    <span className="sim-bar" style={{ height: (amount / (4 * step)) * 100 + '%' }}>
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
  const gradientId = useId();
  const axes = state.map((item, index) => {
    const angle = -Math.PI / 2 + (index * 2 * Math.PI) / state.length;
    return { ...item, dx: Math.cos(angle), dy: Math.sin(angle) };
  });
  const radius = 92;
  const labelRadius = radius + 56;
  const points = (level?: number) =>
    axes
      .map(({ dx, dy, value }) => {
        const distance = (radius * (level ?? value)) / 100;
        return `${180 + dx * distance},${150 + dy * distance}`;
      })
      .join(' ');
  return (
    <figure className="sim-radar-panel">
      <svg className="sim-radar" viewBox="-12 -30 384 356" role="img" aria-labelledby={titleId}>
        <title id={titleId}>
          {axes.map((item) => item.label + ' ' + item.value + '%').join(', ')}
        </title>
        <defs>
          <linearGradient id={gradientId} x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#469d89" />
            <stop offset="100%" stopColor="#036666" />
          </linearGradient>
        </defs>
        {[radius + 12, radius * 0.77, radius * 0.42].map((r) => (
          <circle key={r} cx="180" cy="150" r={r} fill="white" className="sim-radar-tier" />
        ))}
        {axes.map(({ label, dx, dy }) => (
          <line
            key={label}
            x1="180"
            y1="150"
            x2={180 + radius * dx}
            y2={150 + radius * dy}
            stroke="#dfeae5"
            strokeWidth=".6"
          />
        ))}
        <polygon
          points={points(100)}
          fill="none"
          stroke="#88bfa7"
          strokeWidth=".85"
          strokeLinejoin="round"
        />
        <polygon
          points={points()}
          fill={`url(#${gradientId})`}
          fillOpacity=".08"
          stroke={`url(#${gradientId})`}
          strokeWidth="1.2"
          strokeLinejoin="round"
        />
        {axes.map(({ label, icon, value, dx, dy }) => (
          <g
            key={label}
            transform={`translate(${180 + labelRadius * dx} ${150 + labelRadius * dy})`}
            textAnchor="middle"
          >
            <image
              href={icons[`../../shared/assets/icons/simulation-${icon}.svg`]}
              x="-8"
              y="-19"
              width="16"
              height="16"
            />
            <text y="11">{label}</text>
            <text
              y="27"
              className="sim-radar-value"
              data-level={value < 40 ? 'low' : value < 70 ? 'medium' : 'high'}
            >
              {value}%
            </text>
          </g>
        ))}
      </svg>
      <figcaption>
        <span>최종 상태</span>
        <span>100% 기준선</span>
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
          {report.animal} <RecordBadge status="완료" />
        </h2>
        <p>
          {report.temperament} · {report.start} ~ {report.end}
        </p>
        <p>{report.summary}</p>
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
  const { id, reportId } = useParams();
  const { search } = useLocation();
  const application = useAdoption(id);
  if (!application) return <MissingRecord back={'/adoptions' + search} />;
  const applicationUrl = '/adoptions/' + application.id;
  const listUrl = applicationUrl + '/simulation-reports';
  const reports = getSimulationReports(application.id);
  const selected = reports.find((item) => item.id === reportId);
  if (reportId && !selected) return <MissingRecord back={listUrl + search} />;
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
              완료된 리포트 <small>{reports.length}건</small>
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
                    <span>{item.temperament}</span>
                  </div>
                  <div>
                    <small>체험 기간</small>
                    <strong>
                      {item.start.split(' ')[0]} ~ {item.end.split(' ')[0]}
                    </strong>
                    <span>{item.daily.length}일 체험</span>
                  </div>
                  <div>
                    <small>총 지출</small>
                    <strong>{won(totalSpent(item))}</strong>
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
