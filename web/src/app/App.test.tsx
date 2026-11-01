import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import { App } from '@/app/App';
import { AppProviders } from '@/app/providers/AppProviders';
import { useUiStore } from '@/shared/model/ui-store';

function renderApp(path: string) {
  return render(
    <AppProviders>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </AppProviders>,
  );
}

describe('앱 탐색', () => {
  beforeEach(() => useUiStore.setState({ navigationOpen: false }));

  it('홈 화면에서 메뉴를 열고 홈 이동 시 닫는다', () => {
    renderApp('/');
    expect(screen.getByRole('heading', { name: '서비스 준비 중입니다' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '메뉴' }));
    expect(screen.getByRole('navigation', { name: '주 메뉴' })).toBeVisible();
    fireEvent.click(screen.getByRole('link', { name: '홈' }));
    expect(screen.getByRole('button', { name: '메뉴' })).toHaveAttribute('aria-expanded', 'false');
  });

  it('알 수 없는 주소는 404를 표시하고 홈으로 복귀한다', () => {
    renderApp('/unknown/deep-link');
    expect(screen.getByRole('heading', { name: '페이지를 찾을 수 없습니다' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('link', { name: '홈으로 돌아가기' }));
    expect(screen.getByRole('heading', { name: '서비스 준비 중입니다' })).toBeInTheDocument();
  });
});
