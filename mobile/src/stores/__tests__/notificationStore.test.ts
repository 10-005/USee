import { initialNotifications, useNotificationStore } from '../notificationStore';

describe('notificationStore', () => {
  beforeEach(() => {
    useNotificationStore.getState().reset();
  });

  it('알림을 읽음 처리한다', () => {
    const unread = initialNotifications.find(notification => !notification.isRead)!;

    useNotificationStore.getState().markAsRead(unread.id);

    expect(
      useNotificationStore
        .getState()
        .notifications.find(notification => notification.id === unread.id)?.isRead,
    ).toBe(true);
  });

  it('개별 알림을 삭제한다', () => {
    const targetId = initialNotifications[0].id;

    useNotificationStore.getState().deleteNotification(targetId);

    expect(
      useNotificationStore
        .getState()
        .notifications.some(notification => notification.id === targetId),
    ).toBe(false);
  });

  it('모든 알림을 삭제한다', () => {
    useNotificationStore.getState().deleteAll();

    expect(useNotificationStore.getState().notifications).toEqual([]);
  });

  it('댓글과 답글 알림에 이동 대상 ID가 포함된다', () => {
    const commentNotifications = initialNotifications.filter(
      notification => notification.category === 'reply',
    );

    expect(commentNotifications.map(notification => notification.title)).toEqual(
      expect.arrayContaining(['댓글에 답글이 달렸어요!', '내 게시물에 댓글이 달렸어요!']),
    );
    expect(commentNotifications.every(notification => Boolean(notification.targetCommentId))).toBe(
      true,
    );
  });
});
