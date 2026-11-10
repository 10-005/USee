import { shelterAnimalLabel } from '@/entities/shelter/animal-label';
import type { ChatMessage } from '@/entities/chat/model';
import type { ReactNode } from 'react';
import { AnimalPhoto } from '@/shared/ui/AnimalPhoto';
import { isSupportedImage, readImageDataUrl } from '@/shared/lib/image';
import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import {
  useChatMessages,
  useSendMessage,
  useLeaveConversation,
  useChatDraft,
  useSetChatDraft,
} from '@/entities/chat/data';
import { useAnimals, type AnimalRecord } from '@/entities/shelter/data';
import { Link } from 'react-router';
import type { ChatSummary } from './ChatSidebar';
import backIcon from '@/shared/assets/icons/chat-back.svg';
import moreIcon from '@/shared/assets/icons/chat-more.svg';
import plusIcon from '@/shared/assets/icons/chat-plus.svg';
import sendIcon from '@/shared/assets/icons/chat-send.svg';
import reportIcon from '@/shared/assets/icons/chat-report.svg';
import leaveIcon from '@/shared/assets/icons/chat-leave.svg';
import './chat-room.css';

export function ChatRoom({ chat, onBack }: { chat: ChatSummary; onBack: () => void }) {
  const messages = useChatMessages(chat.id);
  const animals = useAnimals();
  const send = useSendMessage();
  const leave = useLeaveConversation();
  const text = useChatDraft(chat.id);
  const setDraft = useSetChatDraft();
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
  return (
    <ChatRoomView
      chat={chat}
      onBack={onBack}
      messages={messages}
      text={text}
      setText={(value) => setDraft(chat.id, value)}
      sendText={(value) => addMessage({ text: value })}
      sendPhoto={async (file) => addMessage({ image: await readImageDataUrl(file) })}
      leave={() => leave(chat.id)}
      renderPost={(id) => <ChatAnimalPost animal={animals.find((a) => a.id === id)} />}
    />
  );
}
export function ChatRoomView({
  chat,
  onBack,
  messages,
  text,
  setText,
  sendText,
  sendPhoto,
  leave,
  report,
  renderPost,
  renderProfile,
  historyControls,
  connectionNotice,
}: {
  chat: ChatSummary;
  onBack: () => void;
  messages: ChatMessage[];
  text: string;
  setText: (text: string) => void;
  sendText: (text: string) => void | Promise<void>;
  sendPhoto: (file: File) => Promise<void>;
  leave: () => void | Promise<void>;
  report?: (reason: string) => Promise<void>;
  renderPost: (id: string) => ReactNode;
  renderProfile?: (
    close: () => void,
    identity: (displayName?: string, avatarSrc?: string) => ReactNode,
  ) => ReactNode;
  historyControls?: ReactNode;
  connectionNotice?: string;
}) {
  const [busy, setBusy] = useState(false);
  const identity = (displayName = chat.name, avatarSrc = chat.avatarSrc) => (
    <>
      <div className="chat-dialog__heading">
        <h2 id="chat-dialog-title">상대방 정보</h2>
        <button
          type="button"
          className="chat-dialog__close"
          aria-label="닫기"
          onClick={closeDialog}
        >
          ×
        </button>
      </div>
      <div className="chat-profile__identity">
        <img src={avatarSrc} alt="" />
        <strong>{displayName}</strong>
      </div>
    </>
  );
  const [menu, setMenu] = useState(false);
  const [dialog, setDialog] = useState<'report' | 'leave' | 'profile'>();
  const [reason, setReason] = useState('');
  const [feedback, setFeedback] = useState('');
  const imageInput = useRef<HTMLInputElement>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const profileTrigger = useRef<HTMLButtonElement>(null);
  const openProfile = (trigger: HTMLButtonElement) => {
    profileTrigger.current = trigger;
    setMenu(false);
    setDialog('profile');
  };
  const lastMessageId = messages.at(-1)?.id;
  const scrollToLatest = () => {
    endRef.current?.scrollIntoView?.({ block: 'nearest' });
  };
  useEffect(scrollToLatest, [lastMessageId]);
  useEffect(() => {
    if (dialog) dialogRef.current?.focus();
  }, [dialog]);
  const closeDialog = () => {
    setDialog(undefined);
    requestAnimationFrame(() =>
      (dialog === 'profile' ? profileTrigger : menuButtonRef).current?.focus(),
    );
  };
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!text.trim() || busy || connectionNotice) return;
    setBusy(true);
    setFeedback('');
    try {
      await sendText(text.trim());
      setText('');
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : '전송하지 못했어요.');
    } finally {
      setBusy(false);
    }
  };
  const upload = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    if (!isSupportedImage(file)) {
      setFeedback('JPG 또는 PNG 사진을 10MB 이하로 선택해 주세요.');
      return;
    }
    setBusy(true);
    setFeedback('');
    void sendPhoto(file)
      .catch((error) =>
        setFeedback(error instanceof Error ? error.message : '사진을 보내지 못했어요.'),
      )
      .finally(() => setBusy(false));
  };
  return (
    <div className="chat-room">
      <div className="chat-room__main" inert={!!dialog}>
        <header className="chat-room__header">
          <button type="button" onClick={onBack} aria-label="채팅 목록으로">
            <img src={backIcon} alt="" />
          </button>
          <button
            className="chat-room__profile-name"
            aria-label={chat.name + ' 상대방 정보'}
            aria-haspopup="dialog"
            onClick={(e) => openProfile(e.currentTarget)}
          >
            {chat.name}
          </button>
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
          {historyControls}
          {messages.map((message) =>
            message.type === 'post' ? (
              <div key={message.id}>{renderPost(message.animalId)}</div>
            ) : (
              <div className={`chat-message chat-message--${message.sender}`} key={message.id}>
                {message.sender === 'other' && (
                  <button
                    type="button"
                    className="chat-profile__trigger"
                    aria-label={chat.name + ' 프로필 보기'}
                    aria-haspopup="dialog"
                    onClick={(e) => openProfile(e.currentTarget)}
                  >
                    <img className="chat-message__avatar" src={chat.avatarSrc} alt="" />
                  </button>
                )}
                <div className="chat-message__content">
                  {message.image ? (
                    <img
                      className="chat-message__image"
                      src={message.image}
                      alt="보낸 사진"
                      onLoad={message.id === lastMessageId ? scrollToLatest : undefined}
                    />
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
        {connectionNotice && (
          <p className="chat-room__feedback" role="status">
            {connectionNotice}
          </p>
        )}
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
              disabled={busy || !!connectionNotice}
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
            readOnly={busy}
            onChange={(e) => setText(e.target.value)}
          />
          <button
            type="submit"
            aria-label="메시지 보내기"
            disabled={!text.trim() || busy || !!connectionNotice}
          >
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
                    'button:not(:disabled), input:not(:disabled), a[href]',
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
            {dialog !== 'profile' && (
              <div className="chat-dialog__heading">
                <h2 id="chat-dialog-title">
                  {dialog === 'report' ? '사용자 신고' : '채팅방을 나가시겠습니까?'}
                </h2>
                {dialog !== 'leave' && (
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
            )}{' '}
            {dialog === 'profile' ? (
              renderProfile?.(() => setDialog(undefined), identity) || identity()
            ) : (
              <>
                <p>
                  {dialog === 'report'
                    ? '이 사용자를 신고하는 이유를 선택해 주세요.'
                    : '나가면 이전 대화 내역을 볼 수 없습니다.'}
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
                    disabled={busy || (dialog === 'report' && !reason)}
                    onClick={async () => {
                      setBusy(true);
                      try {
                        if (dialog === 'leave') {
                          await leave();
                          onBack();
                        } else if (report) {
                          await report(reason);
                          setFeedback('신고가 접수되었습니다.');
                        } else
                          setFeedback('예시 신고가 선택되었어요. 실제 신고는 접수되지 않았습니다.');
                        closeDialog();
                      } catch (error) {
                        closeDialog();
                        setFeedback(
                          error instanceof Error ? error.message : '처리하지 못했습니다.',
                        );
                      } finally {
                        setBusy(false);
                      }
                    }}
                  >
                    {dialog === 'report' ? '신고하기' : '채팅방 나가기'}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export function ChatAnimalPost({ animal }: { animal?: AnimalRecord }) {
  if (!animal)
    return <p className="chat-post--missing">연결된 보호동물 게시글을 찾을 수 없어요.</p>;
  return (
    <Link className="chat-post" to={`/animals/${encodeURIComponent(animal.id)}`}>
      <AnimalPhoto src={animal.imageSrc} alt="" />
      <span className="chat-post__content">
        <span className="chat-post__heading">
          <span className="chat-post__badge">
            {
              { protected: '보호', closed: '종료', returned: '반환', adopted: '입양' }[
                animal.status
              ]
            }
          </span>
          <strong>{shelterAnimalLabel(animal)}</strong>
        </span>
        <span className="chat-post__summary">{animal.summary}</span>
        <span className="chat-post__description">{animal.description || animal.location}</span>
      </span>
    </Link>
  );
}
