import type { NotificationItem } from '../notificationApi';
import { resolveNotificationDestination } from '../pushDestination';
import {
  formatNotificationTime,
  isListedNotification,
  toNotificationListEntry,
} from '../notificationListItem';

const reply: NotificationItem = {
  commentSeq: 45,
  isRead: false,
  message: '와 정말 다행이네요!',
  missingReportSeq: null,
  notificationSeq: 806,
  notificationType: 'COMMENT_REPLY',
  parentCommentSeq: 44,
  readDttm: null,
  regDttm: '2026-10-30T09:12:00+09:00',
  targetSeq: 101,
  targetType: 'MISSING',
  title: '회원님의 댓글에 답글이 달렸어요',
};

describe('notificationListItem', () => {
  it('채팅 알림은 목록에 넣지 않는다', () => {
    expect(isListedNotification({ notificationType: 'CHAT' })).toBe(false);
    expect(isListedNotification(reply)).toBe(true);
    expect(
      toNotificationListEntry({
        ...reply,
        notificationType: 'CHAT' as NotificationItem['notificationType'],
      }),
    ).toBeNull();
  });

  it('목록 항목을 화면 모델로 바꾸고 댓글 상세로 이동한다', () => {
    const entry = toNotificationListEntry(reply, new Date('2026-10-30T11:12:00+09:00'));

    expect(entry).toMatchObject({
      body: '와 정말 다행이네요!',
      category: 'reply',
      createdAtLabel: '2시간 전',
      id: '806',
      isRead: false,
      notificationType: 'COMMENT_REPLY',
      targetCommentId: '45',
      targetId: '101',
      targetType: 'MISSING',
      title: '회원님의 댓글에 답글이 달렸어요',
    });
    expect(
      resolveNotificationDestination({
        body: entry!.body,
        commentSeq: entry!.targetCommentId,
        kind: 'server',
        notificationSeq: entry!.id,
        notificationType: entry!.notificationType,
        targetSeq: entry!.targetId,
        targetType: entry!.targetType,
        title: entry!.title,
      }),
    ).toEqual({
      highlightCommentId: '45',
      name: 'LostReportDetail',
      reportId: '101',
    });
  });

  it('생성 시각을 목록 시각으로 표시한다', () => {
    const now = new Date('2026-10-30T09:12:30+09:00');

    expect(formatNotificationTime('2026-10-30T09:12:10+09:00', now)).toBe('방금');
    expect(formatNotificationTime('2026-10-30T09:02:30+09:00', now)).toBe('10분 전');
    expect(formatNotificationTime('2026-10-28T12:00:00+09:00', now)).toBe('2026.10.28');
    expect(formatNotificationTime(null, now)).toBe('');
  });
});
