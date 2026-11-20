import { formatKoreanAddress } from './address';

describe('formatKoreanAddress', () => {
  it('대한민국 접두어와 불필요한 공백을 제거한다', () => {
    expect(formatKoreanAddress(' 대한민국 경상북도 구미시 인동동 ')).toBe('경상북도 구미시 인동동');
  });

  it('대한민국 접두어가 없는 주소는 유지한다', () => {
    expect(formatKoreanAddress('경상북도 구미시 인동동')).toBe('경상북도 구미시 인동동');
  });
});
