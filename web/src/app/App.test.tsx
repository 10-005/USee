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
  beforeEach(() => {
    sessionStorage.setItem('usee-demo-login', 'true');
    useUiStore.setState({ navigationOpen: false });
  });

  it('로그인 전에는 로그인 화면으로 이동하고 로그아웃하면 다시 로그인해야 한다', () => {
    sessionStorage.removeItem('usee-demo-login');
    renderApp('/animals');
    expect(screen.getByRole('heading', { name: '보호소 로그인' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));
    expect(screen.getByRole('heading', { name: '보호소 홈' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('link', { name: '마이페이지' }));
    fireEvent.click(screen.getByRole('link', { name: '로그아웃하기' }));
    expect(sessionStorage.getItem('usee-demo-login')).toBeNull();
    expect(screen.getByRole('heading', { name: '보호소 로그인' })).toBeInTheDocument();
  });

  it('opens the animal list from the dashboard count', () => {
    renderApp('/');
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('보호소 홈');
    fireEvent.click(screen.getByRole('link', { name: /보호동물 12마리/ }));
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('보호동물 목록');
    expect(screen.getByRole('status')).toHaveTextContent('총 12마리');
  });

  it('알 수 없는 주소는 404를 표시하고 홈으로 복귀한다', () => {
    renderApp('/unknown/deep-link');
    expect(screen.getByRole('heading', { name: '페이지를 찾을 수 없습니다' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('link', { name: '홈으로 돌아가기' }));
    expect(screen.getByRole('heading', { name: '보호소 홈' })).toBeInTheDocument();
  });
});
