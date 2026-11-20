import { ApiError, requestApi } from './client';

type Tokens = { accessToken: string; refreshToken: string };
const storageKey = 'usee-shelter-session';
export const sessionEvent = 'shelter-session-changed';

function tokens(): Tokens | null {
  try {
    const value = JSON.parse(sessionStorage.getItem(storageKey) ?? 'null');
    return value?.accessToken && value?.refreshToken ? value : null;
  } catch {
    return null;
  }
}
export const hasSession = () => !!tokens();
export const chatAccessToken = () => tokens()?.accessToken;
export async function refreshChatToken(expired: string) {
  const current = tokens();
  if (current?.accessToken === expired) await refresh(current);
}
let sessionVersion = 0;
function clearSession() {
  sessionVersion++;
  sessionStorage.removeItem(storageKey);
  window.dispatchEvent(new Event(sessionEvent));
}
function saveTokens(value: Tokens | undefined) {
  if (!value?.accessToken || !value.refreshToken)
    throw new Error('인증 응답을 확인할 수 없습니다.');
  sessionStorage.setItem(
    storageKey,
    JSON.stringify({ accessToken: value.accessToken, refreshToken: value.refreshToken }),
  );
}
export async function login(loginId: string, password: string) {
  const { data } = await requestApi<Tokens & { role: string }>('/core/v1/auth/local/login', {
    method: 'POST',
    json: { loginId, password },
  });
  if (data?.role !== 'SHELTER') throw new Error('보호소 계정으로 로그인해 주세요.');
  sessionVersion++;
  saveTokens(data);
  window.dispatchEvent(new Event(sessionEvent));
}
export async function logout() {
  const current = tokens();
  clearSession();
  if (current)
    await requestApi('/core/v1/auth/logout', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + current.accessToken },
      json: { refreshToken: current.refreshToken },
    });
}
let refreshing: { token: string; promise: Promise<void> } | undefined;
function refresh(current: Tokens) {
  if (refreshing?.token === current.refreshToken) return refreshing.promise;
  const promise = requestApi<Tokens>('/core/v1/auth/refresh', {
    method: 'POST',
    json: { refreshToken: current.refreshToken },
  })
    .then(({ data }) => {
      if (tokens()?.refreshToken === current.refreshToken) saveTokens(data);
    })
    .catch((error: unknown) => {
      if (tokens()?.refreshToken === current.refreshToken) clearSession();
      throw error;
    })
    .finally(() => {
      if (refreshing?.token === current.refreshToken) refreshing = undefined;
    });
  refreshing = { token: current.refreshToken, promise };
  return promise;
}
export async function shelterRequest<T>(
  path: string,
  options: { method?: string; json?: unknown; signal?: AbortSignal } = {},
  service: 'core' | 'simulation' | 'chat' = 'core',
) {
  const current = tokens();
  if (!current) throw new Error('로그인이 필요합니다.');
  const version = sessionVersion;
  const send = (accessToken: string) =>
    requestApi<T>('/' + service + '/v1' + path, {
      ...options,
      headers: { Authorization: 'Bearer ' + accessToken },
    });
  try {
    return (await send(current.accessToken)).data;
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
    if (error.code !== 'AUTH_TOKEN_EXPIRED') {
      if (tokens()?.accessToken === current.accessToken) clearSession();
      throw error;
    }
    if (tokens()?.accessToken === current.accessToken) await refresh(current);
    const next = tokens();
    if (!next || version !== sessionVersion) throw error;
    try {
      return (await send(next.accessToken)).data;
    } catch (retryError) {
      if (
        retryError instanceof ApiError &&
        retryError.status === 401 &&
        tokens()?.accessToken === next.accessToken
      )
        clearSession();
      throw retryError;
    }
  }
}
