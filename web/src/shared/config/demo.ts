/** 데모 모드는 개발 및 테스트 환경에서만 기본 활성화합니다. */
export function isDemoMode(enabled: string | undefined, development: boolean): boolean {
  return enabled === 'true' || (enabled === undefined && development);
}
export const demoMode = isDemoMode(import.meta.env.VITE_DEMO_MODE, import.meta.env.DEV);
