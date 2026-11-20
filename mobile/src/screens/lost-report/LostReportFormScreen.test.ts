import { formatLostDateTime, isValidLostReportPhone } from './LostReportFormScreen';

it('연락처는 하이픈을 제외한 휴대전화 번호가 정확히 11자리여야 한다', () => {
  expect(isValidLostReportPhone('01012345678')).toBe(true);
  expect(isValidLostReportPhone('010-1234-5678')).toBe(true);
  expect(isValidLostReportPhone('010-123-4567')).toBe(false);
  expect(isValidLostReportPhone('010123456789')).toBe(false);
});

describe('formatLostDateTime', () => {
  it('실종 날짜와 시간을 사용자 표시 형식으로 변환한다', () => {
    const date = new Date(2026, 10, 4, 17, 7);

    expect(formatLostDateTime(date.toISOString())).toBe('2026-11-04 오후 5:07');
  });

  it('값이 없으면 선택 문구를 표시한다', () => {
    expect(formatLostDateTime('')).toBe('선택');
  });
});
