import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { Link, NavLink, useNavigate } from 'react-router';
import { useProfile } from '@/entities/shelter/data';
import { profileOptions } from '@/entities/shelter/data/profile';
import { demoMode } from '@/shared/config/demo';
import { useQuery } from '@tanstack/react-query';
import { ShelterLayout } from '@/shared/ui/ShelterLayout';
import { ShelterHeader } from '@/shared/ui/ShelterHeader';
import { LiveChatSidebar } from './LiveChatSidebar';
import { ChatSidebar } from '@/shared/ui/ChatSidebar';
import { ShelterPhoto } from '@/shared/ui/ShelterPhoto';
import {
  useChatOpen,
  useSetChatOpen,
  useSelectedChatId,
  useShowChatList,
  useConversations,
  useOpenConversation,
} from '@/entities/chat/data';
import './shelter-page.css';

function DemoIdentity() {
  const profile = useProfile();
  return <HeaderIdentity name={profile.name} image={profile.profileImage} />;
}
function LiveIdentity() {
  const { data } = useQuery(profileOptions);
  return <HeaderIdentity name={data?.name ?? '마이페이지'} image={data?.profileImage} />;
}
function HeaderIdentity({ name, image }: { name: string; image?: string }) {
  return (
    <Link to="/profile" className="profile-identity" aria-label="마이페이지">
      <ShelterPhoto kind="profileImage" src={image} alt="" width="36" height="36" />
      <span>{name}</span>
    </Link>
  );
}
function DemoChatSidebar() {
  const chatOpen = useChatOpen();
  const setChatOpen = useSetChatOpen();
  const selectedChat = useSelectedChatId();
  const showChatList = useShowChatList();
  const conversations = useConversations();
  const openConversation = useOpenConversation();
  return (
    <ChatSidebar
      open={chatOpen}
      onOpenChange={setChatOpen}
      conversations={conversations}
      selectedId={selectedChat}
      onSelect={openConversation}
      onBack={showChatList}
    />
  );
}
export function ShelterPageLayout({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
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
                  <NavLink to="/adoptions">입양 신청 목록</NavLink>
                  <NavLink to="/rescues">구조일지</NavLink>
                  <NavLink to="/reports">발견 신고 목록</NavLink>
                </>
              }
              profile={demoMode ? <DemoIdentity /> : <LiveIdentity />}
            />
          </div>
        }
        sidebar={demoMode ? <DemoChatSidebar /> : <LiveChatSidebar />}
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
      </ShelterLayout>
    </div>
  );
}
