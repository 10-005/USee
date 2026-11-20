import type { ReactNode } from 'react';
import { Button } from './Button';

export type LoadState =
  | { status: 'ready' }
  | { status: 'loading' }
  | { status: 'error'; message?: string; retry?: () => void };

export function LoadStateMessage({ state, children }: { state: LoadState; children: ReactNode }) {
  if (state.status === 'loading')
    return (
      <p className="shelter-empty" role="status">
        불러오는 중입니다.
      </p>
    );
  if (state.status === 'error')
    return (
      <div className="shelter-empty" role="alert">
        <p>{state.message || '정보를 불러오지 못했습니다.'}</p>
        {state.retry && (
          <Button variant="secondary" onClick={state.retry}>
            다시 시도
          </Button>
        )}
      </div>
    );
  return <>{children}</>;
}
