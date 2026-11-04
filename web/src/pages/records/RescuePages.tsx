import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router';
import { DemoPage, InfoRows, MissingRecord, RecordBadge } from '@/features/shelter-demo/Page';
import {
  useShelterDemo,
  rescueStates,
  type Rescue,
  type RescueStatus,
} from '@/features/shelter-demo/store';
import { Button } from '@/shared/ui/Button';
import { ChoiceGroup } from '@/shared/ui/ChoiceGroup';
import { FormSection } from '@/shared/ui/FormSection';
import { TextField } from '@/shared/ui/TextField';
import { TextAreaField } from '@/shared/ui/TextAreaField';
import { SelectField } from '@/shared/ui/SelectField';
import { CalendarField } from '@/shared/ui/CalendarField';
import { Modal } from '@/shared/ui/Modal';
import pencil from '@/shared/assets/figma/edit-pencil.svg';
import locationIcon from '@/shared/assets/figma/location.svg';
import calendarIcon from '@/shared/assets/figma/calendar-check.svg';
import cat from '@/shared/assets/figma/demo-cat.png';
import { initialReports } from './reports-data';

function useReportListUrl() {
  const { state } = useLocation();
  if (typeof state?.reportSearch !== 'string') return undefined;
  return state.reportSearch ? `/reports?${state.reportSearch}` : '/reports';
}

function useRescueStatus(record?: Rescue) {
  const [status, setStatus] = useState<RescueStatus>(record?.status ?? '진행 중');
  const [reason, setReason] = useState(record?.statusReason ?? '');
  const requiresReason = status === '실패' || status === '취소';
  const statusReason = requiresReason ? reason.trim() : undefined;
  const missingReason = requiresReason && !statusReason;
  const changeStatus = (value: string) => {
    setStatus(value as RescueStatus);
    setReason(value === record?.status ? (record.statusReason ?? '') : '');
  };
  return { status, changeStatus, reason, setReason, requiresReason, statusReason, missingReason };
}

function StatusReasonField({
  draft,
  submitted,
}: {
  draft: ReturnType<typeof useRescueStatus>;
  submitted: boolean;
}) {
  if (!draft.requiresReason) return null;
  return (
    <TextAreaField
      label={`${draft.status} 사유`}
      required
      value={draft.reason}
      onChange={(e) => draft.setReason(e.target.value)}
      error={submitted && draft.missingReason ? `${draft.status} 사유를 입력해 주세요.` : undefined}
    />
  );
}

export function RescueDetailPage() {
  const { id } = useParams();
  const { search } = useLocation();
  const listUrl = useReportListUrl() ?? '/rescues' + search;
  const record = useShelterDemo((s) => s.rescues.find((r) => r.id === id));
  return record ? (
    <RescueDetail key={record.id} record={record} listUrl={listUrl} />
  ) : (
    <MissingRecord back={listUrl} />
  );
}
function RescueDetail({ record, listUrl }: { record: Rescue; listUrl: string }) {
  const report = initialReports.find(
    (r) => r.id === (record.reportId ?? record.source.replace(/^신고 #/, '')),
  );
  const save = useShelterDemo((s) => s.saveRescue);
  const draft = useRescueStatus(record);
  const { status, statusReason } = draft;
  const [submitted, setSubmitted] = useState(false);
  const [confirmStatus, setConfirmStatus] = useState(false);
  return (
    <DemoPage
      title={
        <span className="rescue-detail-title">
          {record.id}
          <RecordBadge status={record.status} />
        </span>
      }
      back={listUrl}
      titleAction={
        record.status === '완료' && (
          <Link className="page-link" to={`/animals/new?rescueId=${encodeURIComponent(record.id)}`}>
            보호동물 등록
          </Link>
        )
      }
    >
      <p className="rescue-detail-date">
        접수 일시{' '}
        {record.receivedAt ? (
          <time dateTime={record.receivedAt}>{record.receivedAt.replace('T', ' ')}</time>
        ) : (
          '미등록'
        )}
      </p>
      <div className="detail-grid">
        <div>
          <section className="detail-box">
            <div className="detail-box__heading">
              <h2>구조 정보</h2>
              <Link to={`/rescues/${record.id}/edit`}>수정</Link>
            </div>
            <InfoRows
              rows={[
                ['구조 사유', record.reason],
                ['구조 장소', record.location],
                ['구조 일시', record.date.replace('T', ' ')],
                ['구조 메모', record.memo],
              ]}
            />
          </section>
          <section className="detail-box">
            <h2>연결된 발견 신고</h2>
            {report ? (
              <>
                <article className="linked-report">
                  <div className="linked-report__photo">
                    <img src={report.image} alt={report.name} />
                    <span>{report.animalStatus}</span>
                  </div>
                  <div>
                    <h3>{report.name}</h3>
                    <p className="linked-report__meta">
                      <img src={locationIcon} alt="발견 장소" width="16" height="16" />
                      {report.location}
                    </p>
                    <p className="linked-report__meta">
                      <img src={calendarIcon} alt="발견 일시" width="16" height="16" />
                      <time dateTime={report.foundAt}>
                        {report.foundAt.slice(0, 10)}{' '}
                        {new Date(report.foundAt).toLocaleTimeString('ko-KR', {
                          hour: 'numeric',
                          minute: '2-digit',
                        })}
                      </time>
                    </p>
                    <p>{report.traits}</p>
                    <p>
                      신고자 | {report.reporter} {report.contact}
                    </p>
                  </div>
                </article>
                <p>구조가 완료되어도 발견 신고는 자동 종료되지 않아요.</p>
              </>
            ) : (
              <p>연결된 발견 신고가 없어요.</p>
            )}
          </section>
        </div>
        <section className="detail-box rescue-detail-status">
          <h2>진행 상태</h2>
          <ChoiceGroup
            label="구조 상태"
            hideLabel
            value={status}
            onChange={draft.changeStatus}
            options={rescueStates.map((value) => ({ value, label: value }))}
          />
          <StatusReasonField draft={draft} submitted={submitted} />
          <div className="page-actions">
            <Button
              variant="approval"
              fullWidth
              onClick={() => {
                setSubmitted(true);
                if (!draft.missingReason) setConfirmStatus(true);
              }}
            >
              상태 저장
            </Button>
          </div>
        </section>
      </div>
      <Modal
        open={confirmStatus}
        title="구조 상태를 저장하시겠어요?"
        description={`구조 상태를 ‘${status}’ 상태로 저장합니다.`}
        onClose={() => setConfirmStatus(false)}
        actions={
          <>
            <Button variant="secondary" onClick={() => setConfirmStatus(false)}>
              취소
            </Button>
            <Button
              onClick={() => {
                save({ ...record, status, statusReason });
                setSubmitted(false);
                setConfirmStatus(false);
              }}
            >
              저장
            </Button>
          </>
        }
      />
    </DemoPage>
  );
}

export function RescueFormPage() {
  const { id } = useParams();
  const record = useShelterDemo((s) => s.rescues.find((r) => r.id === id));
  const [params] = useSearchParams();
  if (id && !record) return <MissingRecord back="/rescues" />;
  const reportId = record
    ? (record.reportId ?? record.source.replace(/^신고 #/, ''))
    : params.get('report');
  const report = initialReports.find((item) => item.id === reportId);
  if (!record && reportId && !report) return <MissingRecord back="/reports" />;
  return <RescueForm key={id ?? params.get('report') ?? 'new'} record={record} report={report} />;
}
function RescueForm({
  record,
  report,
}: {
  record?: Rescue;
  report?: (typeof initialReports)[number];
}) {
  const navigate = useNavigate();
  const reportListUrl = useReportListUrl();
  const { state } = useLocation();
  const back = reportListUrl ?? '/rescues';
  const save = useShelterDemo((s) => s.saveRescue);
  const [cancel, setCancel] = useState(false);
  const [reason, setReason] = useState(record?.reason ?? '');
  const [location, setLocation] = useState(record?.location ?? report?.location ?? '');
  const [date, setDate] = useState(record?.date ?? '');
  const draft = useRescueStatus(record);
  const [submitted, setSubmitted] = useState(false);
  const submit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setSubmitted(true);
    if (!reason || !location.trim() || !date || draft.missingReason) {
      const form = e.currentTarget;
      requestAnimationFrame(() => {
        const first = form.querySelector<HTMLElement>('[aria-invalid="true"]');
        first?.scrollIntoView?.({ block: 'center' });
        first?.focus({ preventScroll: true });
      });
      return;
    }
    const data = new FormData(e.currentTarget);
    const value = (key: string) => String(data.get(key) ?? '').trim();
    const next: Rescue = {
      id: record?.id ?? `R-demo-${crypto.randomUUID().slice(0, 8)}`,
      name: record?.name ?? report?.name ?? '미확인 동물',
      image: record?.image ?? report?.image ?? cat,
      source: record?.source ?? (report ? `신고 #${report.id}` : '직접 접수'),
      reportId: record?.reportId ?? report?.id,
      reason: value('reason'),
      location: `${value('location')} ${value('detail')}`.trim(),
      date: value('date'),
      receivedAt: record
        ? record.receivedAt
        : new Date().toLocaleString('sv-SE').replace(' ', 'T').slice(0, 16),
      memo: value('memo'),
      status: draft.status,
      statusReason: draft.statusReason,
    };
    save(next);
    navigate(`/rescues/${next.id}`, { state });
  };
  return (
    <DemoPage title={record ? '구조 정보 수정' : '구조 접수'} narrow back={back}>
      <form className="work-form rescue-form" noValidate onSubmit={submit}>
        <FormSection title="접수 내용">
          <InfoRows
            rows={[
              [
                '접수 경로',
                record?.source.replace(/^신고 #/, '발견 신고 #') ??
                  (report ? '발견 신고에서 접수' : '직접 접수'),
              ],
            ]}
          />
          {report && (
            <div className="rescue-report">
              <img src={report.image} alt="" />
              <div>
                <small>연결된 발견 신고 · {report.id}</small>
                <strong>{report.name}</strong>
                <span>{report.location}</span>
              </div>
            </div>
          )}
          {!record && (
            <div className="page-actions">
              {report && (
                <Link className="record-link" to="/rescues/new">
                  직접 접수로 변경
                </Link>
              )}
              <Link className="record-link" to={reportListUrl ?? '/reports'}>
                {report ? '다른 발견 신고 선택' : '발견 신고에서 접수하기'}
              </Link>
            </div>
          )}
        </FormSection>
        <FormSection title="구조 정보">
          <SelectField
            className="shelter-filter--menu"
            label="구조 사유"
            name="reason"
            required
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            error={submitted && !reason ? '구조 사유를 선택해 주세요.' : undefined}
          >
            <option value="">선택</option>
            {['배회·유기 의심', '부상·질병', '위험 장소', '기타'].map((v) => (
              <option key={v}>{v}</option>
            ))}
          </SelectField>
          <TextField
            label="구조 장소 *"
            name="location"
            required
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            error={submitted && !location.trim() ? '구조 장소를 입력해 주세요.' : undefined}
            placeholder="주소를 입력해 주세요"
          />
          <TextField label="상세 장소" name="detail" placeholder="건물명 또는 주변 지형" />
          <CalendarField
            label="구조 일시"
            name="date"
            withTime
            initialValue={record?.date ?? ''}
            onConfirm={setDate}
            placeholder="구조 일시를 선택해 주세요"
            error={submitted && !date ? '구조 일시를 선택해 주세요.' : undefined}
          />
          <TextAreaField
            label="구조 관련 메모"
            name="memo"
            defaultValue={record?.memo}
            placeholder="예: 공원 벤치 아래 은신 중, 포획틀 필요."
            maxLength={2000}
          />
        </FormSection>
        <FormSection title="진행 상태">
          {record ? (
            <ChoiceGroup
              label="구조 상태"
              hideLabel
              value={draft.status}
              onChange={draft.changeStatus}
              options={rescueStates.map((value) => ({ value, label: value }))}
            />
          ) : (
            <ul className="rescue-status-summary" aria-label="구조 상태">
              {rescueStates.map((state) => (
                <li key={state} aria-current={state === '진행 중' ? 'true' : undefined}>
                  {state}
                </li>
              ))}
            </ul>
          )}
          <StatusReasonField draft={draft} submitted={submitted} />
        </FormSection>
        <div className="page-actions">
          <Button variant="secondary" onClick={() => setCancel(true)}>
            취소
          </Button>
          <Button type="submit" variant="approval">
            <img src={pencil} alt="" />
            {record ? '저장하기' : '접수하기'}
          </Button>
        </div>
      </form>
      <Modal
        open={cancel}
        title="작성을 취소할까요?"
        description="저장하지 않은 내용은 사라집니다."
        onClose={() => setCancel(false)}
        actions={
          <>
            <Button variant="secondary" onClick={() => setCancel(false)}>
              계속 작성
            </Button>
            <Button onClick={() => navigate(record ? `/rescues/${record.id}` : back, { state })}>
              작성 취소
            </Button>
          </>
        }
      />
    </DemoPage>
  );
}
