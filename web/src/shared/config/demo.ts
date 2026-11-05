/** 실제 로그인 연결 전까지 데모 모드를 기본 활성화합니다. */
export function isDemoMode(enabled: string | undefined): boolean {
  return enabled === undefined || enabled === 'true';
}
export const demoMode = isDemoMode(import.meta.env.VITE_DEMO_MODE);
