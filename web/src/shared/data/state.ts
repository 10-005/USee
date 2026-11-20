/** 화면에서 사용하는 비동기 상태. 서버 응답 형식과는 별개입니다. */
export type QueryState<T> =
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; error: Error; retry: () => void };

export type MutationState =
  | { status: 'idle' }
  | { status: 'pending' }
  | { status: 'success' }
  | { status: 'error'; error: Error };
