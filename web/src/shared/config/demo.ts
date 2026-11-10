export function isDemoMode(enabled: string | undefined): boolean {
  return enabled === 'true';
}
export const demoMode = isDemoMode(import.meta.env.VITE_DEMO_MODE);
