import { Link, Outlet } from 'react-router';
import { useUiStore } from '@/shared/model/ui-store';
import { Button } from '@/shared/ui/Button';

export function AppLayout() {
  const navigationOpen = useUiStore((state) => state.navigationOpen);
  const toggleNavigation = useUiStore((state) => state.toggleNavigation);
  const closeNavigation = useUiStore((state) => state.closeNavigation);

  return (
    <>
      <a className="skip-link" href="#main-content">
        본문으로 건너뛰기
      </a>
      <header className="app-header">
        <Link className="brand" to="/" onClick={closeNavigation}>
          U See
        </Link>
        <Button
          variant="secondary"
          aria-expanded={navigationOpen}
          aria-controls="main-navigation"
          onClick={toggleNavigation}
        >
          메뉴
        </Button>
        <nav id="main-navigation" aria-label="주 메뉴" hidden={!navigationOpen}>
          <Link to="/" onClick={closeNavigation}>
            홈
          </Link>
        </nav>
      </header>
      <main id="main-content" className="page-container" tabIndex={-1}>
        <Outlet />
      </main>
    </>
  );
}
