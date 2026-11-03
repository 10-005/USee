import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { useDemoChat } from '@/shared/model/demo-chat';
import { useShelterDemo } from '@/features/shelter-demo/store';
import { Link } from 'react-router';
import type { ChatSummary } from './ChatSidebar';
import backIcon from '@/shared/assets/figma/chat-back.svg';
import moreIcon from '@/shared/assets/figma/chat-more.svg';
import plusIcon from '@/shared/assets/figma/chat-plus.svg';
import sendIcon from '@/shared/assets/figma/chat-send.svg';
import reportIcon from '@/shared/assets/figma/chat-report.svg';
import leaveIcon from '@/shared/assets/figma/chat-leave.svg';
import './chat-room.css';

export function ChatRoom({ chat, onBack }: { chat: ChatSummary; onBack: () => void }) {
  const messages = useDemoChat((state) => state.messages[chat.id] ?? []);
  const animals = useShelterDemo((state) => state.animals);
  const send = useDemoChat((state) => state.send);
  const leave = useDemoChat((state) => state.leave);
  const text = useDemoChat((state) => state.drafts[chat.id] ?? '');
  const setDraft = useDemoChat((state) => state.setDraft);
  const [menu, setMenu] = useState(false);
  const [dialog, setDialog] = useState<'report' | 'leave'>();
  const [reason, setReason] = useState('');
  const [feedback, setFeedback] = useState('');
  const imageInput = useRef<HTMLInputElement>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: 'nearest' });
  }, [messages.length]);
  useEffect(() => {
    if (dialog) dialogRef.current?.focus();
  }, [dialog]);
  const closeDialog = () => {
    setDialog(undefined);
    requestAnimationFrame(() => menuButtonRef.current?.focus());
  };
  const addMessage = (message: { text?: string; image?: string }) => {
    send(chat.id, {
      id: crypto.randomUUID(),
      type: 'message',
      sender: 'me',
      readByRecipient: false,
      ...message,
      time: new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit' }).format(
        new Date(),
      ),
    });
  };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!text.trim()) return;
    addMessage({ text: text.trim() });
    setDraft(chat.id, '');
  };
  const upload = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 10 * 1024 * 1024) {
      setFeedback('JPG 또는 PNG 사진을 10MB 이하로 선택해 주세요.');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => addMessage({ image: String(reader.result) });
    reader.onerror = () => setFeedback('사진을 읽지 못했어요. 다시 선택해 주세요.');
    reader.readAsDataURL(file);
  };
  return (
    <div className="chat-room">
      <div className="chat-room__main" inert={!!dialog}>
        <header className="chat-room__header">
          <button type="button" onClick={onBack} aria-label="채팅 목록으로">
            <img src={backIcon} alt="" />
          </button>
          <strong>{chat.name}</strong>
          <button
            ref={menuButtonRef}
            type="button"
            aria-label="채팅방 메뉴"
            aria-expanded={menu}
            onClick={() => setMenu(!menu)}
          >
            <img src={moreIcon} alt="" />
          </button>
          {menu && (
            <div className="chat-room__menu">
              <button
                type="button"
                className="chat-room__menu-danger"
                onClick={() => {
                  setMenu(false);
                  setReason('');
                  setDialog('report');
                }}
              >
                <img src={reportIcon} alt="" />
                신고하기
              </button>
              <button
                type="button"
                className="chat-room__menu-danger"
                onClick={() => {
                  setMenu(false);
                  setDialog('leave');
                }}
              >
                <img src={leaveIcon} alt="" />
                채팅방 나가기
              </button>
            </div>
          )}
        </header>
        <div className="chat-room__messages" aria-label="대화 내용">
          <div className="chat-room__date">대화 내역</div>
          {messages.map((message) =>
            message.type === 'post' ? (
              <ChatAnimalPost
                key={message.id}
                animal={animals.find((animal) => animal.id === message.animalId)}
              />
            ) : (
              <div className={`chat-message chat-message--${message.sender}`} key={message.id}>
                {message.sender === 'other' && (
                  <img className="chat-message__avatar" src={chat.avatarSrc} alt="" />
                )}
                <div className="chat-message__content">
                  {message.image ? (
                    <img className="chat-message__image" src={message.image} alt="보낸 사진" />
                  ) : (
                    <p>{message.text}</p>
                  )}
                  <span className="chat-message__meta">
                    {message.sender === 'me' && message.readByRecipient === false && (
                      <span className="chat-message__unread">안읽음</span>
                    )}
                    <time>{message.time}</time>
                  </span>
                </div>
              </div>
            ),
          )}
          <div ref={endRef} />
        </div>
        {feedback && (
          <p className="chat-room__feedback" role="status">
            {feedback}
          </p>
        )}
        <form className="chat-room__composer" onSubmit={submit}>
          <div className="chat-room__attach">
            <button
              type="button"
              aria-label="사진 첨부"
              onClick={() => imageInput.current?.click()}
            >
              <img src={plusIcon} alt="" />
            </button>
            <input
              ref={imageInput}
              type="file"
              accept="image/jpeg,image/png"
              hidden
              onChange={upload}
            />
          </div>
          <input
            aria-label="메시지"
            placeholder="메시지를 입력하세요"
            value={text}
            onChange={(e) => setDraft(chat.id, e.target.value)}
          />
          <button type="submit" aria-label="메시지 보내기" disabled={!text.trim()}>
            <img src={sendIcon} alt="" />
          </button>
        </form>
      </div>
      {dialog && (
        <div className="chat-dialog-overlay">
          <div
            className="chat-dialog"
            role="dialog"
            aria-labelledby="chat-dialog-title"
            ref={dialogRef}
            tabIndex={-1}
            onKeyDown={(event) => {
              if (event.key === 'Escape') {
                event.stopPropagation();
                closeDialog();
              }
              if (event.key === 'Tab') {
                const elements = Array.from(
                  event.currentTarget.querySelectorAll<HTMLElement>(
                    'button:not(:disabled), input:not(:disabled)',
                  ),
                );
                const first = elements[0];
                const last = elements[elements.length - 1];
                if (
                  event.shiftKey &&
                  (document.activeElement === first ||
                    document.activeElement === event.currentTarget)
                ) {
                  event.preventDefault();
                  last?.focus();
                } else if (!event.shiftKey && document.activeElement === last) {
                  event.preventDefault();
                  first?.focus();
                }
              }
            }}
          >
            <div className="chat-dialog__heading">
              <h2 id="chat-dialog-title">
                {dialog === 'report' ? '사용자 신고' : '채팅방을 나가시겠습니까?'}
              </h2>
              {dialog === 'report' && (
                <button
                  type="button"
                  className="chat-dialog__close"
                  aria-label="닫기"
                  onClick={closeDialog}
                >
                  ×
                </button>
              )}
            </div>
            <p>
              {dialog === 'report'
                ? '이 사용자를 신고하는 이유를 선택해 주세요.'
                : '삭제된 예시 대화 내역은 복원할 수 없습니다.'}
            </p>
            {dialog === 'report' && (
              <>
                <div className="chat-dialog__target">신고 대상 · {chat.name}</div>
                <div className="chat-room__reasons">
                  {['욕설 / 비방 / 괴롭힘', '스팸 / 광고', '음란 / 부적절한 내용', '기타'].map(
                    (item) => (
                      <label key={item} className={reason === item ? 'is-selected' : ''}>
                        <input
                          type="radio"
                          name="report-reason"
                          checked={reason === item}
                          onChange={() => setReason(item)}
                        />
                        <span>{item}</span>
                      </label>
                    ),
                  )}
                </div>
              </>
            )}
            <div className="chat-dialog__actions">
              {dialog === 'leave' && (
                <button type="button" className="chat-dialog__cancel" onClick={closeDialog}>
                  취소
                </button>
              )}
              <button
                type="button"
                className="chat-dialog__confirm"
                disabled={dialog === 'report' && !reason}
                onClick={() => {
                  if (dialog === 'leave') {
                    leave(chat.id);
                    onBack();
                  } else setFeedback('예시 신고가 선택되었어요. 실제 신고는 접수되지 않았습니다.');
                  closeDialog();
                }}
              >
                {dialog === 'report' ? '신고하기' : '채팅방 나가기'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function ChatAnimalPost({
  animal,
}: {
  animal?: ReturnType<typeof useShelterDemo.getState>['animals'][number];
}) {
  if (!animal)
    return <p className="chat-post--missing">연결된 보호동물 게시글을 찾을 수 없어요.</p>;
  return (
    <Link className="chat-post" to={`/animals/${encodeURIComponent(animal.id)}`}>
      <img src={animal.imageSrc} alt="" />
      <span className="chat-post__content">
        <span className="chat-post__heading">
          <span className="chat-post__badge">보호</span>
          <strong>{animal.name}</strong>
        </span>
        <span className="chat-post__summary">{animal.summary}</span>
        <span className="chat-post__description">{animal.description || animal.location}</span>
      </span>
    </Link>
  );
}
