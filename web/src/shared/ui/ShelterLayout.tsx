import { useId } from 'react';
import type { ReactNode } from 'react';
import './shelter-ui.css';

export function ShelterLayout({
  header,
  sidebar,
  children,
}: {
  header: ReactNode;
  sidebar?: ReactNode;
  children: ReactNode;
}) {
  const mainId = useId();
  return (
    <div className="shelter-layout">
      <a className="skip-link" href={`#${mainId}`}>
        본문으로 건너뛰기
      </a>
      {header}
      <div className="shelter-layout__body">
        <main id={mainId} tabIndex={-1} className="shelter-layout__content">
          {children}
        </main>
        {sidebar}
      </div>
    </div>
  );
}
