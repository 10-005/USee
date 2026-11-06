import { LinkButton } from '@/shared/ui/LinkButton';
import { useState } from 'react';
import { useLocation, useParams } from 'react-router';
import { WorkPage, InfoRows, MissingRecord, RecordBadge } from '@/shared/ui/WorkPage';
import { useAnimal, useAdoption, useReviewAdoption } from '@/entities/shelter/data';
import type { AdoptionStatus } from '@/entities/shelter/model';
import { Button } from '@/shared/ui/Button';
import { Modal } from '@/shared/ui/Modal';
import { TextAreaField } from '@/shared/ui/TextAreaField';
import clock from '@/shared/assets/icons/review-clock.svg';

export function AdoptionDetailPage() {
  const { id } = useParams();
  const listUrl = '/adoptions' + useLocation().search;
  const record = useAdoption(id);
  const animal = useAnimal(record?.animalId);
  const review = useReviewAdoption();
  const [action, setAction] = useState<AdoptionStatus>();
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');
  const [openSections, setOpenSections] = useState<number[]>([0]);
  if (!record) return <MissingRecord back={listUrl} />;
  const questions: { title: string; rows: [string, string][] }[] = [
    {
      title: '신청자 기본 정보',
      rows: [
        ['신청자', record.applicant],
        ['생년월일', '미등록'],
        ['전화번호', '미등록'],
        ['실제 양육 장소', '미등록'],
        ['상세 주소', '미등록'],
        ['이메일', '미등록'],
        ['방문 상담 희망일', '미등록'],
      ],
    },
    {
      title: '주거 및 동거 환경',
      rows: [
        ['주거형태', '아파트'],
        ['반려동물 양육 가능 여부', '가능'],
        ['동거인 유무', '있음'],
        ['동거인의 입양 동의', '전원 동의'],
        ['주 양육자', '본인'],
        ['주로 생활할 공간', '실내'],
        ['반려동물이 하루 평균 혼자 있는 시간', '2시간 미만'],
      ],
    },
    {
      title: '양육 경험 및 돌봄 환경',
      rows: [
        ['현재 함께 사는 동물', '없음'],
        ['동물 종류와 마리수 및 합사 준비', '해당없음'],
        ['과거 양육 경험', '있음'],
        ['과거 양육 동물 및 기간', '고양이(치즈냥이) 1년 동안 양육'],
        ['과거 동물과 함께하지 않는 이유', '사망'],
        ['부재·응급상황의 대처 돌봄', '있음'],
        ['대체 돌봄 방법', '가족이 돌봄 예정'],
      ],
    },
    {
      title: '입양 및 돌봄 계획',
      rows: [
        [
          '입양 동기',
          '오랫동안 반려동물 입양을 고민해 왔으며, 충분한 준비를 마친 후 함께 생활하고 싶습니다.',
        ],
        [
          '일상적인 돌봄 계획',
          '규칙적인 급식과 산책, 놀이 시간을 확보하고 위생 및 건강 상태를 꾸준히 관리할 예정입니다.',
        ],
        [
          '치료·행동 적용 지원 계획',
          '동물병원에서 정기적으로 검진하고 필요한 치료를 받을 예정입니다.',
        ],
        [
          '생활환경 변화 시 돌봄 계획',
          '이사나 장기 부재 시에도 가족과 협력해 지속적으로 돌볼 예정입니다.',
        ],
      ],
    },
    {
      title: '입양 전 교육',
      rows: [
        ['교육 이수 여부', '이수'],
        ['교육기관', 'PBJ 동물 보호 교육기관'],
        ['이수한 교육 과정', '고양이(유기동물) 입양 교육'],
        ['이수일', '2026.10.10'],
      ],
    },
  ];
  const openReview = (next: AdoptionStatus) => {
    setAction(next);
    setError('');
    setReason('');
  };
  return (
    <WorkPage
      title="입양 신청 상세"
      narrow
      back={listUrl}
      titleAction={<LinkButton to={`/animals/${record.animalId}`}>보호동물 정보 보기</LinkButton>}
    >
      <section className="detail-box application-overview">
        <div className="application-animal">
          <img src={record.image} alt={record.name} />
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
      <section className="detail-box application-questions" aria-label="입양 신청서">
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
                <InfoRows rows={question.rows} />
              </div>
            </div>
          </div>
        ))}
      </section>
      <section className="detail-box">
        <div className="detail-box__heading">
          <h2>심사 결과</h2>
          <RecordBadge status={record.status} />
        </div>
        <div className="review-notice">
          <img src={clock} alt="" />
          <strong>
            {record.status === '심사 대기'
              ? '서류 심사 대기 중'
              : `${record.status} 처리된 신청입니다`}
          </strong>
          <p>
            {record.status === '심사 대기'
              ? '입양 신청서가 접수되었습니다. 제출된 내용을 검토하고 서류 승인 또는 거절 여부를 결정해 주세요.'
              : record.status === '서류 승인'
                ? '면접 및 후속 절차를 진행한 뒤 최종 입양 여부를 결정해 주세요.'
                : (record.status === '거절' && record.reason) || '심사 처리가 완료되었습니다.'}
          </p>
        </div>
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
                } catch {
                  setSaveError('저장하지 못했습니다. 다시 시도해 주세요.');
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
