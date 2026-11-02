import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { Link, NavLink, useNavigate } from 'react-router';
import { useShelterDemo } from '@/features/shelter-demo/store';
import { ShelterLayout } from '@/shared/ui/ShelterLayout';
import { ShelterHeader } from '@/shared/ui/ShelterHeader';
import { ChatSidebar } from '@/shared/ui/ChatSidebar';
import { Modal } from '@/shared/ui/Modal';
import { Button } from '@/shared/ui/Button';
import shelterAvatar from '@/shared/assets/figma/shelter-avatar.png';
import { sampleConversations } from '@/shared/model/sample-conversations';
import './shelter-page.css';

export function ShelterPageLayout({ children }: { children: ReactNode }) {
  const profile = useShelterDemo((s) => s.profile);
  const navigate = useNavigate();
  const [chatOpen, setChatOpen] = useState(false);
  const [selectedChat, setSelectedChat] = useState<string>();
  const [notice, setNotice] = useState('');
  const [showBackToTop, setShowBackToTop] = useState(false);
  useEffect(() => {
    const updateVisibility = () => setShowBackToTop(window.scrollY > 80);
    updateVisibility();
    window.addEventListener('scroll', updateVisibility, { passive: true });
    return () => window.removeEventListener('scroll', updateVisibility);
  }, []);
  const headerRef = useRef<HTMLDivElement>(null);
  const pageRef = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    const header = headerRef.current;
    if (!header) return;
    const syncHeight = () => {
      pageRef.current?.style.setProperty('--header-height', `${header.offsetHeight}px`);
    };
    syncHeight();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(syncHeight);
    observer.observe(header);
    return () => observer.disconnect();
  }, []);
  return (
    <div className="shelter-page" ref={pageRef}>
      <ShelterLayout
        header={
          <div ref={headerRef} className="shelter-page__fixed-header">
            <ShelterHeader
              homeHref="/"
              onHomeClick={(event) => {
                event.preventDefault();
                navigate('/');
              }}
              navigation={
                <>
                  <NavLink to="/animals">보호동물</NavLink>
                  <NavLink to="/adoptions">입양신청 목록</NavLink>
                  <NavLink to="/rescues">구조일지</NavLink>
                  <NavLink to="/reports">제보 목록</NavLink>
                </>
              }
              profile={
                <Link to="/profile" className="profile-identity" aria-label="마이페이지">
                  <img src={shelterAvatar} alt="" width="36" height="36" />
                  <span>{profile.name}</span>
                </Link>
              }
            />
          </div>
        }
        sidebar={
          <ChatSidebar
            open={chatOpen}
            onOpenChange={setChatOpen}
            conversations={sampleConversations}
            selectedId={selectedChat}
            onSelect={(id) => {
              setSelectedChat(id);
              setNotice('예시 대화입니다. 메시지 송수신 기능은 준비 중입니다.');
            }}
          />
        }
      >
        {children}
        {showBackToTop && (
          <div className="back-to-top">
            <button
              type="button"
              aria-label="맨 위로"
              title="맨 위로"
              onClick={() =>
                window.scrollTo({
                  top: 0,
                  behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
                    ? 'instant'
                    : 'smooth',
                })
              }
            >
              <svg
                aria-hidden="true"
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M12 20V5m-7 7 7-7 7 7" />
              </svg>
            </button>
          </div>
        )}
        <Modal
          open={!!notice}
          title="안내"
          description={notice}
          onClose={() => setNotice('')}
          actions={<Button onClick={() => setNotice('')}>닫기</Button>}
        />
      </ShelterLayout>
    </div>
  );
}
