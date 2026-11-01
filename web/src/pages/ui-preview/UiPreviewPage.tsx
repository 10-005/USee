import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { TextField } from '@/shared/ui/TextField';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { StatCard } from '@/shared/ui/StatCard';
import { ActivityCard } from '@/shared/ui/ActivityCard';
import './ui-preview.css';

export function UiPreviewPage() {
  const [message, setMessage] = useState('');
  return (
    <main className="ui-preview">
      <h1>공통 컴포넌트</h1>
      <p>Figma 보호소 웹 기본 UI · 미리보기용 데이터</p>
      <section aria-label="버튼" className="ui-preview__row">
        <Button size="large" onClick={() => setMessage('로그인 버튼 선택')}>
          로그인
        </Button>
        <Button size="large" disabled>
          로그인
        </Button>
        <Button size="small" variant="secondary">
          최종 거절
        </Button>
        <Button size="small" variant="approval" onClick={() => setMessage('승인 버튼 선택')}>
          최종 승인
        </Button>
      </section>
      <section aria-label="입력 필드" className="ui-preview__fields">
        <TextField label="아이디" placeholder="ID" />
        <TextField
          label="비밀번호"
          type="password"
          autoComplete="current-password"
          placeholder="비밀번호"
        />
        <TextField label="오류 상태" error="입력값을 확인해 주세요." />
      </section>
      <section aria-label="상태 배지">
        <StatusBadge>서류 승인</StatusBadge>
      </section>
      <section aria-label="요약 카드" className="ui-preview__stats">
        <StatCard label="입양 신청" value="3건" />
        <StatCard label="보호동물" value="24마리" tone="green" />
        <StatCard label="진행 중인 구조" value="1건" tone="orange" />
      </section>
      <section aria-label="내역 카드" className="ui-preview__activities">
        <ActivityCard
          title="코리안숏헤어 · 흰색 / 이서준"
          description="서류 제출 완료 · 2026.10.28"
          timeLabel="1시간 전"
          onClick={() => setMessage('입양 신청 내역 선택')}
        />
        <ActivityCard
          title="월드컵공원 인근"
          description="구조 날짜 2026.10.28 · 발견 신고를 통한 접수"
          timeLabel="1시간 전"
        />
      </section>
      <output aria-live="polite">{message}</output>
    </main>
  );
}
