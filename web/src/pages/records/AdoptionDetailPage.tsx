import { AnimalPhoto } from '@/shared/ui/AnimalPhoto';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  applicationSections,
  detailOptions,
  siblingOptions,
  submitReview,
} from '@/entities/shelter/data/applications';
import { demoMode } from '@/shared/config/demo';
import { LoadStateMessage } from '@/shared/ui/LoadState';
import type { Adoption } from '@/entities/shelter/model';
import { LinkButton } from '@/shared/ui/LinkButton';
import { useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import { WorkPage, InfoRows, MissingRecord, RecordBadge } from '@/shared/ui/WorkPage';
import { useAnimal, useAdoption, useAdoptions, useReviewAdoption } from '@/entities/shelter/data';
import type { AdoptionStatus } from '@/entities/shelter/model';
import { Button } from '@/shared/ui/Button';
import { Modal } from '@/shared/ui/Modal';
import { TextAreaField } from '@/shared/ui/TextAreaField';
import documentArt from '@/shared/assets/review-documents.png';
import interviewArt from '@/shared/assets/review-interview.png';
import approvedArt from '@/shared/assets/review-approved.png';
import rejectedArt from '@/shared/assets/review-rejected.png';
import chevron from '@/shared/assets/icons/chat-back.svg';
import './simulation-reports.css';

const reviewStages = ['신청서 접수', '서류 심사', '입양 심사', '최종 승인'];
const reviewPresentation = {
  '심사 대기': {
    completed: 1,
    title: '신청서가 접수되었어요',
    description: '신청 내용을 검토하고 서류 심사 결과를 결정해 주세요.',
    image: documentArt,
  },
  '서류 승인': {
    completed: 2,
    title: '입양 심사가 진행 중이에요',
    description: '면담 후 최종 입양 여부를 결정해 주세요.',
    image: interviewArt,
  },
  '최종 승인': { completed: 4, title: '입양이 완료되었어요', description: '', image: approvedArt },
  거절: { completed: 0, title: '입양 신청이 거절되었어요', description: '', image: rejectedArt },
} satisfies Record<
  AdoptionStatus,
  { completed: number; title: string; description: string; image: string }
>;

function ReviewProgress({ status, reason }: Pick<Adoption, 'status' | 'reason'>) {
  const { completed, title, description, image } = reviewPresentation[status];
  return (
    <div className="review-notice">
      <div className="review-notice__heading">
        <div>
          <strong>{title}</strong>
          {description && <p>{description}</p>}
        </div>
        <img src={image} alt="" width="84" height="84" />
      </div>
      {status === '거절' ? (
        <div className="review-notice__reason">
          <strong>거절 사유</strong>
          <p>{reason?.trim() || '등록된 거절 사유가 없습니다.'}</p>
        </div>
      ) : (
        <ol className="review-stages" aria-label="입양 심사 단계">
          {reviewStages.map((label, index) => {
            const state = index < completed ? '완료' : index === completed ? '진행 중' : '예정';
            return (
              <li
                key={label}
                data-state={state}
                aria-current={state === '진행 중' ? 'step' : undefined}
              >
                <span className="review-stages__dot" aria-hidden="true">
                  {state === '완료' && '✓'}
                </span>
                {label}
                <span className="review-stages__sr"> {state}</span>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}

export function AdoptionDetailPage() {
  const { id } = useParams();
  return demoMode ? <DemoDetail key={id} id={id} /> : <LiveDetail key={id} id={id!} />;
}
function LiveDetail({ id }: { id: string }) {
  const { search, state } = useLocation();
  const query = useQuery(detailOptions(id));
  const animalId = query.data ? query.data.record.animalId : state?.animalId;
  const siblings = useQuery(siblingOptions(animalId));
  const cache = useQueryClient();
  if (query.error || !query.data)
    return (
      <WorkPage title="입양 신청 상세" back={'/adoptions' + search}>
        <ApplicationSwitcher id={id} animalId={animalId} siblingIds={siblings.data ?? []} />
        <LoadStateMessage
          state={
            query.error
              ? { status: 'error', message: query.error.message, retry: () => void query.refetch() }
              : { status: 'loading' }
          }
        >
          {null}
        </LoadStateMessage>
      </WorkPage>
    );
  const data = query.data;
  return (
    <DetailView
      key={data.record.status}
      {...data}
      siblingIds={siblings.data ?? []}
      review={async (id, action, reason) => {
        try {
          await submitReview(id, data.record.status, action, reason);
        } finally {
          await Promise.all([
            cache.invalidateQueries({ queryKey: ['adoptions'] }),
            cache.invalidateQueries({ queryKey: ['animals'] }),
          ]);
        }
      }}
    />
  );
}
function DemoDetail({ id }: { id?: string }) {
  const { search } = useLocation();
  const listUrl = '/adoptions' + search;
  const record = useAdoption(id);
  const applications = useAdoptions();
  const animal = useAnimal(record?.animalId);
  const review = useReviewAdoption();

  if (!record) return <MissingRecord back={listUrl} />;
  const questions = applicationSections({
    applicant: { name: record.applicant, phone: null, birthDate: null },
    careAddress: null,
    careAddressDetail: null,
    email: null,
    visitHopeDate: null,
    answers: {
      1: '아파트',
      2: '자가',
      3: '가능',
      4: '있음',
      5: '전원 동의',
      6: '없음',
      8: '있음',
      9: '2시간 미만',
      10: '본인',
      11: '없음',
      12: '가족이 돌봄 예정',
      13: '오랫동안 반려동물 입양을 고민해 왔으며, 충분한 준비를 마친 후 함께 생활하고 싶습니다.',
      14: '월 20만 원',
      15: '가족과 협력해 지속적으로 돌볼 예정입니다.',
      16: '동의',
      17: '이수',
      18: 'PBJ 동물 보호 교육기관',
      19: '고양이(유기동물) 입양 교육',
      20: '2026.10.10',
    },
  });
  const siblingIds = applications
    .filter((item) => record.animalId && item.animalId === record.animalId)
    .sort((a, b) => b.date.localeCompare(a.date) || b.id.localeCompare(a.id))
    .map((item) => item.id);
  return (
    <DetailView
      record={record}
      siblingIds={siblingIds}
      animal={animal}
      questions={questions}
      review={review}
    />
  );
}
function ApplicationSwitcher({
  id,
  animalId,
  siblingIds,
}: {
  id: string;
  animalId: string;
  siblingIds: string[];
}) {
  const { search } = useLocation();
  const navigate = useNavigate();
  const index = siblingIds.indexOf(id);
  const move = (offset: number) => {
    const target = siblingIds[index + offset];
    if (target)
      void navigate('/adoptions/' + encodeURIComponent(target) + search, {
        state: { animalId, slide: offset > 0 ? 'next' : 'previous' },
      });
  };
  if (index < 0 || siblingIds.length < 2) return null;
  return (
    <nav className="ui-pagination application-switcher" aria-label="해당 동물의 신청서">
      <Button aria-label="이전 신청서" disabled={index === 0} onClick={() => move(-1)}>
        <img src={chevron} alt="" width="18" height="18" />
      </Button>
      <span>
        해당 동물의 신청서 {index + 1} / {siblingIds.length}
      </span>
      <Button
        aria-label="다음 신청서"
        disabled={index === siblingIds.length - 1}
        onClick={() => move(1)}
      >
        <img className="application-switcher__next" src={chevron} alt="" width="18" height="18" />
      </Button>
    </nav>
  );
}
function DetailView({
  record,
  siblingIds,
  animal,
  questions,
  review,
}: {
  record: Adoption;
  siblingIds: string[];
  animal?: {
    summary: string;
    neutered: string;
    registration?: string | null;
    chip?: string | null;
  };
  questions: { title: string; rows: [string, string][] }[];
  review: (id: string, status: AdoptionStatus, reason?: string) => Promise<void>;
}) {
  const { search, state } = useLocation();
  const listUrl = '/adoptions' + search;
  const [action, setAction] = useState<AdoptionStatus>();
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');
  const [openSections, setOpenSections] = useState<number[]>([0]);
  const openReview = (next: AdoptionStatus) => {
    setAction(next);
    setSaveError('');
    setError('');
    setReason('');
  };
  return (
    <WorkPage
      title="입양 신청 상세"
      narrow
      back={listUrl}
      titleAction={
        <div className="page-actions">
          <LinkButton
            className="simulation-entry"
            variant="soft"
            to={`/adoptions/${record.id}/simulation-reports${search}`}
          >
            모의 입양 리포트 보기
          </LinkButton>
          {record.animalId && (
            <LinkButton to={`/animals/${record.animalId}`}>보호동물 정보 보기</LinkButton>
          )}
        </div>
      }
    >
      <section className="detail-box application-overview">
        <div className="application-animal">
          <AnimalPhoto src={record.image} alt={record.name} />
          <div>
            <h2>{record.name}</h2>
            <p>{animal?.summary}</p>
            <p>
              중성화 여부{' '}
              {animal?.neutered === 'yes' ? 'O' : animal?.neutered === 'no' ? 'X' : '미상'}
            </p>
            <p>
              <span>등록번호</span> {animal?.registration || '미등록'}
            </p>
            <p>
              <span>마이크로칩번호</span> {animal?.chip || '미등록'}
            </p>
          </div>
        </div>
        <InfoRows
          rows={[
            ['신청번호', record.id],
            ['신청일자', record.date],
          ]}
        />
      </section>
      <ApplicationSwitcher id={record.id} animalId={record.animalId} siblingIds={siblingIds} />
      <section
        className="detail-box application-questions"
        data-slide={state?.slide}
        aria-label="입양 신청서"
      >
        {questions.map((question, i) => (
          <div className="application-question" key={question.title}>
            <button
              type="button"
              className="application-question__toggle"
              aria-expanded={openSections.includes(i)}
              aria-controls={`application-section-${i}`}
              onClick={() =>
                setOpenSections((sections) =>
                  sections.includes(i)
                    ? sections.filter((section) => section !== i)
                    : [...sections, i],
                )
              }
            >
              <span className="application-questions__number">
                {String(i + 1).padStart(2, '0')}
              </span>
              <span>{question.title}</span>
            </button>
            <div
              id={`application-section-${i}`}
              className="application-question__content"
              data-open={openSections.includes(i)}
              inert={!openSections.includes(i)}
            >
              <div>
                {question.rows.length ? (
                  <InfoRows rows={question.rows} />
                ) : (
                  <p className="application-question__empty">제출된 답변이 없습니다.</p>
                )}
              </div>
            </div>
          </div>
        ))}
      </section>
      <section className="detail-box">
        <div className="detail-box__heading">
          <h2>입양 심사 현황</h2>
          <RecordBadge status={record.status} />
        </div>
        <ReviewProgress status={record.status} reason={record.reason} />
        {['심사 대기', '서류 승인'].includes(record.status) && (
          <div className="page-actions">
            <Button variant="secondary" onClick={() => openReview('거절')}>
              {record.status === '심사 대기' ? '서류 거절' : '최종 거절'}
            </Button>
            <Button
              variant="approval"
              onClick={() => openReview(record.status === '심사 대기' ? '서류 승인' : '최종 승인')}
            >
              {record.status === '심사 대기' ? '서류 승인' : '최종 승인'}
            </Button>
          </div>
        )}
      </section>
      <Modal
        open={!!action}
        title={`${action ?? ''} 확인`}
        onClose={() => {
          if (!saving) setAction(undefined);
        }}
        actions={
          <>
            <Button variant="secondary" disabled={saving} onClick={() => setAction(undefined)}>
              취소
            </Button>
            <Button
              disabled={saving}
              onClick={async () => {
                if (action === '거절' && !reason.trim()) {
                  setError('거절 사유를 입력해 주세요.');
                  return;
                }
                if (!action || saving) return;
                setSaving(true);
                setSaveError('');
                try {
                  if (action === '거절') await review(record.id, action, reason.trim());
                  else await review(record.id, action);
                  setAction(undefined);
                } catch (error) {
                  setSaveError(
                    error instanceof Error
                      ? error.message
                      : '저장하지 못했습니다. 다시 시도해 주세요.',
                  );
                } finally {
                  setSaving(false);
                }
              }}
            >
              확인
            </Button>
          </>
        }
      >
        {saveError && <p role="alert">{saveError}</p>}
        {action === '최종 승인' && <p>입양을 최종 승인하시겠습니까?</p>}
        {action === '거절' && (
          <TextAreaField
            label="거절 사유"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            error={error}
            required
            maxLength={1000}
          />
        )}
      </Modal>
    </WorkPage>
  );
}
