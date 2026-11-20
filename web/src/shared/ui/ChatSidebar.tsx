import { useId, useRef, useState, type ReactNode } from 'react';
import mascot from '@/shared/assets/chat-peek.png';
import chevron from '@/shared/assets/icons/sidebar-chevron.svg';
import { ChatRoom } from './ChatRoom';
import './shelter-ui.css';

export type ChatSummary = {
  id: string;
  name: string;
  avatarSrc: string;
  preview: string;
  timeLabel: string;
  unreadCount: number;
};
export type ChatSidebarProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  conversations: ChatSummary[];
  onSelect: (id: string) => void;
  selectedId?: string;
  roomView?: ReactNode;
  listFooter?: ReactNode;
  listStatus?: ReactNode;
  totalUnread?: number;
  onFilterChange?: (unread: boolean) => void;
  onBack?: () => void;
};

export function ChatSidebar({
  open,
  onOpenChange,
  conversations,
  onSelect,
  selectedId,
  roomView,
  listFooter,
  listStatus,
  totalUnread,
  onFilterChange,
  onBack,
}: ChatSidebarProps) {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const panelId = useId();
  const toggleRef = useRef<HTMLButtonElement>(null);
  const unreadCount =
    totalUnread ?? conversations.reduce((sum, item) => sum + Math.max(0, item.unreadCount), 0);
  const visible = unreadOnly ? conversations.filter((item) => item.unreadCount > 0) : conversations;
  const selected = conversations.find((item) => item.id === selectedId);
  return (
    <aside
      className={`chat-sidebar ${open ? 'chat-sidebar--open' : ''}`}
      aria-label="채팅"
      onKeyDown={(event) => {
        if (event.key === 'Escape' && open && !(event.target as HTMLElement).closest('dialog')) {
          onOpenChange(false);
          toggleRef.current?.focus();
        }
      }}
    >
      <button
        type="button"
        ref={toggleRef}
        className="chat-sidebar__toggle"
        aria-label={open ? '채팅 닫기' : `채팅 열기, 안 읽은 메시지 ${unreadCount}개`}
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => onOpenChange(!open)}
      >
        {!open && (
          <span className="chat-sidebar__mascot">
            <img src={mascot} alt="" width="57" height="57" />
            {unreadCount > 0 && <span className="chat-sidebar__count">{unreadCount}</span>}
          </span>
        )}
        <img className={open ? 'chat-sidebar__chevron--open' : ''} src={chevron} alt="" />
      </button>
      <div id={panelId} inert={!open} aria-hidden={!open} className="chat-sidebar__panel">
        {roomView ??
          (selected && onBack ? (
            <ChatRoom key={selected.id} chat={selected} onBack={onBack} />
          ) : (
            <>
              <div className="chat-sidebar__filters" role="group" aria-label="채팅 필터">
                <button
                  type="button"
                  aria-pressed={!unreadOnly}
                  onClick={() => {
                    setUnreadOnly(false);
                    onFilterChange?.(false);
                  }}
                >
                  전체
                </button>
                <button
                  type="button"
                  aria-pressed={unreadOnly}
                  onClick={() => {
                    setUnreadOnly(true);
                    onFilterChange?.(true);
                  }}
                >
                  안 읽음 <span className="chat-unread">{unreadCount}</span>
                </button>
              </div>
              {listStatus}
              {visible.length ? (
                <ul className="chat-sidebar__list">
                  {visible.map((item) => (
                    <li key={item.id}>
                      <button
                        type="button"
                        className="chat-summary"
                        aria-pressed={selectedId === item.id}
                        onClick={() => onSelect(item.id)}
                      >
                        <img src={item.avatarSrc} alt="" width="60" height="60" />
                        <span className="chat-summary__body">
                          <span className="chat-summary__line">
                            <strong>{item.name}</strong>
                            <time>{item.timeLabel}</time>
                          </span>
                          <span className="chat-summary__line">
                            <span className="chat-summary__preview">{item.preview}</span>
                            {item.unreadCount > 0 && (
                              <span
                                className="chat-unread"
                                aria-label={`안 읽음 ${item.unreadCount}개`}
                              >
                                {item.unreadCount}
                              </span>
                            )}
                          </span>
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              ) : (
                !listStatus && (
                  <p className="shelter-empty" role="status">
                    표시할 대화가 없습니다.
                  </p>
                )
              )}
              {listFooter}
            </>
          ))}
      </div>
    </aside>
  );
}
