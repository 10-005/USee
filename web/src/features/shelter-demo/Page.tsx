import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { ShelterPageLayout } from '@/app/layouts/ShelterPageLayout';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import './pages.css';

export function DemoPage({
  title,
  titleAction,
  children,
  narrow = false,
  back,
  hideTitle = false,
}: {
  title: ReactNode;
  titleAction?: ReactNode;
  children: ReactNode;
  narrow?: boolean;
  back?: string;
  hideTitle?: boolean;
}) {
  return (
    <ShelterPageLayout>
      <div
        className={`work-page ${narrow ? 'work-page--narrow' : ''} ${hideTitle ? 'work-page--list' : ''}`}
      >
        {back && (
          <Link className="work-page__back" to={back}>
            <svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none">
              <path
                d="m14 6-6 6 6 6"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
            이전
          </Link>
        )}
        {titleAction ? (
          <div className="work-page__title-row">
            <h1>{title}</h1>
            {titleAction}
          </div>
        ) : (
          <h1 className={hideTitle ? 'shelter-sr-only' : undefined}>{title}</h1>
        )}
        {children}
      </div>
    </ShelterPageLayout>
  );
}
export function RecordBadge({ status, children }: { status: string; children?: ReactNode }) {
  const tone = ['최종 승인', '완료', '보호 중', '신고 진행 중'].includes(status)
    ? 'green'
    : status === '입양 완료'
      ? 'blue'
      : ['반환 완료', '거절'].includes(status)
        ? 'orange'
        : ['보호 종료', '신고 종료'].includes(status)
          ? 'gray'
    : ['실패', '심사 대기'].includes(status)
      ? 'red'
      : status === '취소' || !status
        ? 'gray'
        : 'blue';
  return <StatusBadge className={`record-badge--${tone}`}>{children ?? status}</StatusBadge>;
}
export function InfoRows({ rows }: { rows: [string, ReactNode][] }) {
  return (
    <dl className="info-rows">
      {rows.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value || '미등록'}</dd>
        </div>
      ))}
    </dl>
  );
}
export function MissingRecord({ back }: { back: string }) {
  return (
    <DemoPage title="정보를 찾을 수 없습니다" back={back}>
      <p>예시 데이터가 초기화되었거나 존재하지 않는 주소입니다.</p>
    </DemoPage>
  );
}
