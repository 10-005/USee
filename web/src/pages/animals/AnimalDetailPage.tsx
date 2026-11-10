import { shelterAnimalLabel } from '@/entities/shelter/animal-label';
import { AnimalPhoto } from '@/shared/ui/AnimalPhoto';
import { useQuery } from '@tanstack/react-query';
import { demoMode } from '@/shared/config/demo';
import { animalDetailOptions, detailRecord } from '@/entities/shelter/data/animals';
import type { ShelterAnimalRecord, Rescue } from '@/entities/shelter/model';
import { LoadStateMessage } from '@/shared/ui/LoadState';
import { LinkButton } from '@/shared/ui/LinkButton';
import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { WorkPage, InfoRows, MissingRecord, RecordBadge } from '@/shared/ui/WorkPage';
import { useAnimal, useAdoptions, useRescue } from '@/entities/shelter/data';
import { LinkedRescueCard } from '@/pages/animals/LinkedRescueCard';
import { Button } from '@/shared/ui/Button';
import pencil from '@/shared/assets/icons/edit-pencil.svg';

import { animalStatuses } from './animal-statuses';
export function AnimalDetailPage() {
  const { id } = useParams();
  return demoMode ? <DemoDetail id={id!} /> : <LiveDetail key={id} id={id!} />;
}
function DemoDetail({ id }: { id: string }) {
  const animal = useAnimal(id);
  const rescue = useRescue(animal?.rescueId);
  const applications = useAdoptions();
  const { search } = useLocation();
  if (!animal) return <MissingRecord back={'/animals' + search} />;
  return (
    <AnimalDetail
      animal={animal}
      rescue={rescue}
      pending={applications.filter((a) => a.animalId === id && a.status === '심사 대기').length}
      approved={applications.filter((a) => a.animalId === id && a.status === '서류 승인').length}
    />
  );
}
function LiveDetail({ id }: { id: string }) {
  const { search } = useLocation();
  const query = useQuery(animalDetailOptions(id));
  if (query.error || !query.data)
    return (
      <WorkPage title="보호동물 상세" back={'/animals' + search}>
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
    <AnimalDetail
      animal={detailRecord(data)}
      rescuedAt={data.rescueInfo.rescuedDttm ?? undefined}
      rescueMemo={data.rescueInfo.rescueMemo ?? undefined}
      pending={data.applicationCounts.SUBMITTED}
      approved={data.applicationCounts.APPROVED}
    />
  );
}
function AnimalDetail({
  animal,
  rescue,
  pending,
  approved,
  rescuedAt,
  rescueMemo,
}: {
  animal: ShelterAnimalRecord;
  rescue?: Rescue;
  pending: number;
  approved: number;
  rescuedAt?: string;
  rescueMemo?: string;
}) {
  const { search } = useLocation();
  const listUrl = '/animals' + search;
  const [photo, setPhoto] = useState(0);
  const images = animal.images?.length ? animal.images : [animal.imageSrc];
  const current = Math.min(photo, images.length - 1);
  return (
    <WorkPage
      title="보호동물 상세"
      back={listUrl}
      titleAction={
        <LinkButton to={`/animals/${animal.id}/edit`}>
          <img src={pencil} alt="" />
          수정하기
        </LinkButton>
      }
    >
      <div className="animal-gallery">
        <AnimalPhoto
          src={images[current]}
          alt={`${shelterAnimalLabel(animal)} 사진 ${current + 1}`}
        />
        {images.length > 1 && (
          <>
            <Button
              variant="secondary"
              aria-label="이전 사진"
              onClick={() => setPhoto((current - 1 + images.length) % images.length)}
            >
              <span aria-hidden="true" className="gallery-chevron gallery-chevron--prev" />
            </Button>
            <span className="gallery-count" aria-live="polite">
              {current + 1} / {images.length}
            </span>
            <Button
              variant="secondary"
              aria-label="다음 사진"
              onClick={() => setPhoto((current + 1) % images.length)}
            >
              <span aria-hidden="true" className="gallery-chevron" />
            </Button>
          </>
        )}
      </div>
      <section className="detail-box animal-summary">
        <div>
          <h2>
            <RecordBadge status={animalStatuses[animal.status]} /> {shelterAnimalLabel(animal)}
          </h2>
          <p>
            {demoMode ? '공고번호' : '보호동물 번호'}{' '}
            {demoMode ? animal.noticeNumber || '미등록' : animal.id}
          </p>
        </div>
        {(animal.status === 'closed' || animal.status === 'returned') && (
          <div className="animal-summary__outcome">
            <h3>{animal.status === 'closed' ? '보호 종료 정보' : '반환 정보'}</h3>
            <InfoRows
              rows={
                animal.status === 'returned'
                  ? [
                      ['보호자 이름', animal.guardianName],
                      ['보호자 연락처', animal.guardianPhone],
                    ]
                  : [['보호 종료 사유', animal.closingReason || '미등록']]
              }
            />
          </div>
        )}
      </section>
      <div className="animal-detail-columns">
        <div>
          <section className="detail-box">
            <h2>기본정보</h2>
            <InfoRows
              rows={[
                ['동물등록번호', animal.registration],
                ['마이크로칩번호', animal.chip],
                ['품종', shelterAnimalLabel(animal)],
                ['성별', { female: '암컷', male: '수컷', unknown: '모름' }[animal.sex]],
                ['중성화', { yes: '완료', no: '미완료', unknown: '모름' }[animal.neutered]],
                [
                  '나이 / 체중',
                  animal.birth || animal.weight
                    ? `${animal.birth || '미상'} / ${animal.weight || '미상'}${demoMode ? 'kg' : ''}`
                    : animal.summary,
                ],
                ['색상', animal.color],
              ]}
            />
          </section>
          <section className="detail-box">
            <h2>구조정보</h2>
            <InfoRows
              rows={[
                ['구조장소', rescue?.location ?? animal.location],
                ['구조일시', (rescuedAt ?? rescue?.date)?.replace('T', ' ')],
                ['특이사항', rescueMemo ?? rescue?.memo],
                ...(animal.status === 'returned' && animal.closingReason
                  ? [['기타사항', animal.closingReason] as [string, string]]
                  : []),
              ]}
            />
            {rescue && <LinkedRescueCard rescue={rescue} compact />}
          </section>
        </div>
        <div>
          <section className="detail-box animal-detail-notes">
            <h2>특징</h2>
            <p>{animal.description || '없음'}</p>
            <h2>건강검진 특이사항</h2>
            <p>{animal.health || '없음'}</p>
            <h2>예방접종 상태</h2>
            <p>{animal.vaccination || '없음'}</p>
          </section>
          <section className="detail-box">
            <div className="detail-box__heading">
              <h2>입양 신청</h2>
              <Link
                className="animal-adoptions-link"
                to={`/adoptions?animalId=${encodeURIComponent(animal.id)}`}
                aria-label="입양 신청 자세히 보기"
              >
                자세히 보기
                <svg aria-hidden="true" width="14" height="14" viewBox="0 0 16 16" fill="none">
                  <path
                    d="m6 3.5 4.5 4.5L6 12.5"
                    stroke="currentColor"
                    strokeWidth="1.6"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </Link>
            </div>
            <InfoRows
              rows={[
                ['심사 대기', `${pending}건`],
                ['서류 승인', `${approved}건`],
              ]}
            />
          </section>
        </div>
      </div>
    </WorkPage>
  );
}
