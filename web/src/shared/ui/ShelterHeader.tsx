import type { ReactNode } from 'react';
import logo from '@/shared/assets/figma/logo.png';
import './shelter-ui.css';

export type ShelterHeaderProps = {
  navigation: ReactNode;
  profile: ReactNode;
  homeHref?: string;
};

export function ShelterHeader({ navigation, profile, homeHref = '/' }: ShelterHeaderProps) {
  return (
    <header className="shelter-header">
      <div className="shelter-header__main">
        <a href={homeHref} aria-label="USEE 홈">
          <img src={logo} alt="USEE" width="76" height="38" />
        </a>
        <nav aria-label="보호소 메뉴">{navigation}</nav>
      </div>
      <div className="shelter-header__profile">{profile}</div>
    </header>
  );
}
