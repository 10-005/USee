import { useState } from 'react';
import { DataTable, Pagination, type TableColumn } from '@/shared/ui/DataTable';
import { ConfirmModal } from '@/shared/ui/Modal';
import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import animalImage from '@/shared/assets/figma/animal-example.png';

type Application = { id: string; name: string; date: string };
const applications: Application[] = Array.from({ length: 9 }, (_, index) => ({
  id: `A-1029-${String(index + 1).padStart(2, '0')}`,
  name: `예시 신청자 ${index + 1}`,
  date: '2026-10-10',
}));

export function TableModalPreview() {
  const [open, setOpen] = useState(false);
  const [page, setPage] = useState(1);
  const [empty, setEmpty] = useState(false);
  const [message, setMessage] = useState('');
  const columns: TableColumn<Application>[] = [
    {
      id: 'status',
      header: '상태',
      width: '122px',
      render: () => <StatusBadge>서류 승인</StatusBadge>,
    },
    { id: 'id', header: '신청번호', width: '158px', render: (row) => row.id },
    {
      id: 'animal',
      header: '동물 정보',
      width: '325px',
      render: () => (
        <div className="ui-table__animal">
          <img src={animalImage} alt="신청 동물 예시" />
          <div>
            <strong>[개] 믹스견</strong>
            <small>10000</small>
          </div>
        </div>
      ),
    },
    { id: 'date', header: '신청일자', width: '186px', render: (row) => row.date },
    { id: 'name', header: '신청자', render: (row) => row.name },
    {
      id: 'action',
      header: '상세',
      width: '122px',
      render: (row) => (
        <Button
          size="small"
          variant="secondary"
          aria-label={`${row.id} 상세보기`}
          onClick={() => setMessage(`${row.id} 선택`)}
        >
          상세보기
        </Button>
      ),
    },
  ];
  return (
    <section aria-label="모달과 테이블 미리보기">
      <h2>모달·테이블</h2>
      <div className="ui-preview__row">
        <Button onClick={() => setOpen(true)}>작성 취소 모달 열기</Button>
        <Button
          variant="secondary"
          onClick={() => {
            setEmpty(!empty);
            setPage(1);
          }}
        >
          {empty ? '목록 표시' : '빈 결과 표시'}
        </Button>
      </div>
      <p>총 {empty ? 0 : applications.length}건 · 미리보기용 데이터</p>
      <DataTable
        caption="입양 신청 목록"
        columns={columns}
        rows={empty ? [] : applications.slice((page - 1) * 8, page * 8)}
        rowKey={(row) => row.id}
      />
      {!empty && <Pagination page={page} pageCount={2} onPageChange={setPage} />}
      <output aria-live="polite">{message}</output>
      <ConfirmModal
        open={open}
        title="작성을 취소할까요?"
        description="입력하거나 수정한 내용은 저장되지 않아요."
        onClose={() => setOpen(false)}
        onConfirm={() => {
          setMessage('작성 취소 선택 (예시)');
          setOpen(false);
        }}
      />
    </section>
  );
}
