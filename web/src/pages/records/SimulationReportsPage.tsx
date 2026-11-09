import { useLocation, useParams } from 'react-router';
import { getSimulationReports, useAdoption } from '@/entities/shelter/data';
import { WorkPage, MissingRecord, RecordBadge } from '@/shared/ui/WorkPage';
import { LinkButton } from '@/shared/ui/LinkButton';
import './simulation-reports.css';

type Report = ReturnType<typeof getSimulationReports>[number];
const icons = import.meta.glob<string>('../../shared/assets/icons/simulation-*.svg', {
  eager: true,
  import: 'default',
});
const won = (amount: number) => amount.toLocaleString('ko-KR') + '원';
const totalSpent = (report: Report) => report.expenses.reduce((sum, item) => sum + item.amount, 0);

function Spending({ report }: { report: Report }) {
  const total = totalSpent(report);
  const difference = total - report.budget;
  const paid = report.expenses
    .filter((item) => item.amount > 0)
    .sort((a, b) => b.amount - a.amount);
  const unpaid = report.expenses.filter((item) => item.amount === 0);
  let offset = 0;
  const stops: string[] = [];
  for (const item of paid) {
    const start = offset;
    offset += (item.amount / total) * 100;
    stops.push(`${item.color} ${start}% ${offset}%`);
  }
  const peak = Math.max(0, ...report.daily);
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
          <div className="sim-breakdown">
            <div
              className="sim-donut"
              style={{
                background: stops.length ? 'conic-gradient(' + stops.join(',') + ')' : '#ebeae5',
              }}
            >
              <div>
                <small>총 지출</small>
                <strong>{won(total)}</strong>
                {paid[0] && (
                  <small>
                    {paid[0].label} {Math.round((paid[0].amount / total) * 100)}%
                  </small>
                )}
              </div>
            </div>
            <dl className="sim-legend">
              {paid.map((item) => (
                <div key={item.label}>
                  <dt>
                    <i style={{ background: item.color }} />
                    {item.label}
                  </dt>
                  <dd>{won(item.amount)}</dd>
                </div>
              ))}
              {unpaid.length > 0 && (
                <div className="sim-zero">
                  <dt>{unpaid.map((item) => item.label).join(' · ')}</dt>
                  <dd>각 0원</dd>
                </div>
              )}
            </dl>
          </div>
        </div>
        <div>
          <h3>일별 지출</h3>
          <ol className="sim-bars">
            {report.daily.map((amount, i) => (
              <li key={i}>
                <div className="sim-bar-track">
                  <div
                    className="sim-bar"
                    data-peak={amount > 0 && amount === peak}
                    style={{ height: (peak ? (amount / peak) * 80 : 0) + '%' }}
                  >
                    <small>{won(amount)}</small>
                  </div>
                </div>
                <span>{i + 1}일차</span>
              </li>
            ))}
          </ol>
        </div>
      </div>
    </section>
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
          <dl className="sim-state">
            {report.state.map(({ label, icon, value }) => (
              <div key={label} data-level={value < 40 ? 'low' : value < 70 ? 'medium' : 'high'}>
                <dt>
                  <img alt="" src={icons[`../../shared/assets/icons/simulation-${icon}.svg`]} />
                  {label}
                </dt>
                <dd>
                  <span className="sim-gauge" aria-hidden="true">
                    {Array.from({ length: 10 }, (_, i) => (
                      <i key={i}>
                        <b
                          style={{
                            width: Math.max(0, Math.min(100, value * 10 - i * 100)) + '%',
                            backgroundSize: `${value}cqi 100%`,
                            backgroundPositionX: `calc(-${i} * (10cqi + var(--segment-gap) / 10))`,
                          }}
                        />
                      </i>
                    ))}
                  </span>
                  <strong>{value}%</strong>
                </dd>
              </div>
            ))}
          </dl>
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
          <h3>돌발 이벤트 처리 결과</h3>
          <div className="sim-event-track" aria-hidden="true">
            <b
              style={{ width: (report.events ? (report.resolved / report.events) * 100 : 0) + '%' }}
            />
          </div>
          <div className="sim-event-labels">
            <span>해결 {report.resolved}건</span>
            <span>미해결 {report.events - report.resolved}건</span>
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
