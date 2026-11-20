import { describe, expect, it } from 'vitest';
import { isDemoMode } from '@/shared/config/demo';
import { localDate } from './date';
import { isSupportedImage } from './image';

describe('API 연결 전 공통 처리', () => {
  it('데모 모드는 명시적으로 활성화한다', () => {
    expect(isDemoMode(undefined)).toBe(false);
    expect(isDemoMode('true')).toBe(true);
    expect(isDemoMode('false')).toBe(false);
  });
  it('로컬 달력 날짜를 생성한다', () => {
    expect(localDate(new Date(2026, 0, 2, 23, 59))).toBe('2026-01-02');
  });
  it('지원되는 이미지 형식과 크기를 확인한다', () => {
    expect(isSupportedImage(new File(['x'], 'photo.png', { type: 'image/png' }))).toBe(true);
    expect(isSupportedImage(new File(['x'], 'file.txt', { type: 'text/plain' }))).toBe(false);
    expect(
      isSupportedImage(
        new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'photo.jpg', { type: 'image/jpeg' }),
      ),
    ).toBe(false);
  });
});
