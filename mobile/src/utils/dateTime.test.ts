import { formatReportDateTime } from './dateTime';

describe('formatReportDateTime', () => {
  it('신고 일시를 날짜와 오전·오후 시간으로 표시한다', () => {
    const date = new Date(2026, 10, 10, 17, 7);

    expect(formatReportDateTime(date.toISOString())).toBe('2026-11-10 오후 5:07');
  });

  it('해석할 수 없는 값은 원문을 유지한다', () => {
    expect(formatReportDateTime('알 수 없음')).toBe('알 수 없음');
  });
});
