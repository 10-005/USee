import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { animalLabel } from '@/entities/shelter/animal-label';
import { ApiError } from '@/shared/api/client';
import { shelterRequest } from '@/shared/api/session';
import { AnimalPhoto } from '@/shared/ui/AnimalPhoto';
import { Modal } from '@/shared/ui/Modal';
import { Button } from '@/shared/ui/Button';

type Report = {
  classification: { speciesName: string; breedName: string; colorName: string } | null;
  age: string | null;
  weight: string | null;
  gender: 'MALE' | 'FEMALE' | 'UNKNOWN';
  status: string;
  occurredDttm: string;
  address: string;
  description: string | null;
  landmarkDescription?: string | null;
  representativeImageUrl: string | null;
  images: { imageSeq: number; imageUrl: string; displayOrder: number }[];
  user?: { displayName: string };
  reporter?: { displayName: string };
};

export function SharedReportPost({ id, kind }: { id: string; kind: 'MISSING' | 'FOUND' }) {
  const [open, setOpen] = useState(false);
  const missing = kind === 'MISSING';
  const label = missing ? '실종' : '발견';
  const query = useQuery({
    queryKey: ['chat', 'shared-report', kind, id],
    queryFn: async ({ signal }) => {
      const report = await shelterRequest<Report>(
        (missing ? '/missing-reports/' : '/found-reports/') + encodeURIComponent(id),
        { signal },
      );
      if (!report) throw new Error('신고 내용을 불러올 수 없습니다.');
      return report;
    },
    retry: false,
  });
  const data = query.data;
  if (!data) {
    if (query.isPending) return <p role="status">{label} 신고를 불러오는 중이에요.</p>;
    if (query.error instanceof ApiError && [403, 404, 410].includes(query.error.status))
      return <p className="chat-post--missing">확인할 수 없는 {label} 신고예요.</p>;
    return (
      <button type="button" onClick={() => void query.refetch()}>
        {label} 신고 다시 불러오기
      </button>
    );
  }
  const name = animalLabel(data.classification?.speciesName, data.classification?.breedName);
  const summary = [
    data.age,
    { MALE: '수컷', FEMALE: '암컷', UNKNOWN: null }[data.gender],
    data.classification?.colorName,
    data.weight,
  ]
    .filter(Boolean)
    .join(' | ');
  const photos = [...data.images].sort((a, b) => a.displayOrder - b.displayOrder);
  const cover = data.representativeImageUrl || photos[0]?.imageUrl;
  return (
    <>
      <button
        className="chat-post chat-report-card"
        type="button"
        aria-haspopup="dialog"
        onClick={() => setOpen(true)}
      >
        <AnimalPhoto src={cover} alt="" />
        <span className="chat-post__content">
          <span className="chat-post__heading">
            <span className="chat-post__badge">{label}</span>
            <strong>{name}</strong>
          </span>
          <span className="chat-post__summary">{summary}</span>
          <span className="chat-post__description">{data.address}</span>
          <span>원문 보기</span>
        </span>
      </button>
      {open && (
        <Modal
          open
          title={label + ' 신고 원문'}
          onClose={() => setOpen(false)}
          actions={<Button onClick={() => setOpen(false)}>닫기</Button>}
        >
          <div className="chat-report-detail">
            <strong>
              {name} ·{' '}
              {data.status === 'CLOSED' ? '신고 종료' : missing ? '찾는 중' : '발견 신고 중'}
            </strong>
            <p>{summary}</p>
            {(photos.length ? photos.map((p) => p.imageUrl) : cover ? [cover] : []).map(
              (src, i) => (
                <img key={i} src={src} alt={label + ' 신고 사진 ' + (i + 1)} />
              ),
            )}
            <p>
              {label} 일시:{' '}
              {new Date(data.occurredDttm).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })}
              <br />
              {label} 장소: {data.address}
            </p>
            {data.landmarkDescription && <p>{data.landmarkDescription}</p>}
            <p>{data.description || '등록된 설명이 없어요.'}</p>
            {(data.user || data.reporter) && (
              <p>작성자: {(data.user || data.reporter)?.displayName}</p>
            )}
          </div>
        </Modal>
      )}
    </>
  );
}
