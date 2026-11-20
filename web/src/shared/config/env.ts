export function parseApiBaseUrl(value: string | undefined): string {
  const base = (value ?? '/api').trim().replace(/\/+$/, '');
  if (base.startsWith('/') && !base.startsWith('//') && !/[?#\\]/.test(base)) return base;
  try {
    const url = new URL(base);
    if (
      ['http:', 'https:'].includes(url.protocol) &&
      !url.username &&
      !url.password &&
      !url.search &&
      !url.hash
    )
      return base;
  } catch {
    /* 아래의 일관된 설정 오류로 처리 */
  }
  throw new Error('VITE_API_BASE_URL은 /api 또는 http(s) API 주소여야 합니다.');
}

export const env = { apiBaseUrl: parseApiBaseUrl(import.meta.env.VITE_API_BASE_URL) };
