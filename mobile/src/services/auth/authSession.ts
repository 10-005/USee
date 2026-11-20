import type { AuthTokens } from '../api';
import { clearStoredTokens, getStoredTokens, setStoredTokens } from './tokenStorage';

let currentTokens: AuthTokens | null = null;
const clearedListeners = new Set<() => void>();

export const getAccessToken = () => currentTokens?.accessToken ?? null;
export const getRefreshToken = () => currentTokens?.refreshToken ?? null;

export async function restoreAuthSession(): Promise<AuthTokens | null> {
  currentTokens = await getStoredTokens();
  return currentTokens;
}

export async function saveAuthSession(tokens: AuthTokens): Promise<void> {
  currentTokens = tokens;
  await setStoredTokens(tokens);
}

export async function clearAuthSession(): Promise<void> {
  currentTokens = null;
  await clearStoredTokens();
  clearedListeners.forEach(listener => listener());
}

export function subscribeAuthSessionCleared(listener: () => void): () => void {
  clearedListeners.add(listener);
  return () => clearedListeners.delete(listener);
}
