import { describe, expect, it } from 'vitest';
import { parseApiBaseUrl } from './env';

describe('API 환경 변수', () => {
  it('기본 주소와 끝 슬래시를 정규화한다', () => {
    expect(parseApiBaseUrl(undefined)).toBe('/api');
    expect(parseApiBaseUrl('https://example.com/api/')).toBe('https://example.com/api');
  });
  it.each([
    '',
    '//example.com',
    'javascript:alert(1)',
    'https://user:secret@example.com',
    '/api?x=1',
  ])('잘못된 주소 %s를 거부한다', (value) => {
    expect(() => parseApiBaseUrl(value)).toThrow('VITE_API_BASE_URL');
  });
});
