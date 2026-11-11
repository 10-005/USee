import { notificationNavigationRef, openPendingNotification } from '../notificationNavigation';
import {
  handleNotificationOpened,
  handleNotificationURL,
  subscribeNotificationOpens,
  takePendingNotificationOpen,
} from '../../../services/notifications/notificationOpen';

jest.mock('@react-navigation/native', () => ({
  createNavigationContainerRef: () => ({
    isReady: jest.fn(),
    getRootState: jest.fn(),
    resetRoot: jest.fn(),
  }),
}));
jest.mock('../../../services/notifications/notificationApi', () => ({
  markNotificationAsRead: jest.fn().mockResolvedValue(undefined),
}));

const chat = {
  notification: { title: '보호센터', body: '메시지' },
  data: { notificationType: 'CHAT', roomSeq: '501', messageSeq: '9001' },
};
const nav = jest.mocked(notificationNavigationRef);

beforeEach(() => {
  jest.clearAllMocks();
  takePendingNotificationOpen();
  nav.isReady.mockReturnValue(true);
  nav.getRootState.mockReturnValue({ routeNames: ['Home', 'ChatRoom'] } as never);
});

it('채팅 알림 클릭은 기존 스택을 홈과 해당 채팅방으로 교체한다', () => {
  handleNotificationOpened(chat);
  openPendingNotification(true);
  expect(nav.resetRoot).toHaveBeenCalledWith({
    index: 1,
    routes: [{ name: 'Home' }, { name: 'ChatRoom', params: { roomId: '501' } }],
  });
});

it('로그인과 홈 네비게이터 준비 전 클릭은 보관했다가 한 번만 이동한다', () => {
  handleNotificationOpened(chat);
  openPendingNotification(false);
  nav.isReady.mockReturnValue(false);
  openPendingNotification(true);
  nav.isReady.mockReturnValue(true);
  nav.getRootState.mockReturnValue({ routeNames: ['Login'] } as never);
  openPendingNotification(true);
  expect(nav.resetRoot).not.toHaveBeenCalled();
  nav.getRootState.mockReturnValue({ routeNames: ['Home'] } as never);
  openPendingNotification(true);
  openPendingNotification(true);
  expect(nav.resetRoot).toHaveBeenCalledTimes(1);
});

it.each(['COMMENT', 'COMMENT_REPLY'])(
  '%s 클릭은 홈 위 게시글과 해당 댓글을 연다',
  notificationType => {
    handleNotificationOpened({
      notification: chat.notification,
      data: {
        notificationType,
        notificationSeq: '40',
        targetSeq: '101',
        targetType: 'MISSING',
        commentSeq: '55',
      },
    });
    openPendingNotification(true);
    expect(nav.resetRoot).toHaveBeenCalledWith({
      index: 1,
      routes: [
        { name: 'Home' },
        { name: 'LostReportDetail', params: { reportId: '101', highlightCommentId: '55' } },
      ],
    });
  },
);

it.each(['ADOPTION_SUBMITTED', 'ADOPTION_APPROVED', 'ADOPTION_REJECTED', 'ADOPTION_COMPLETED'])(
  '%s 클릭은 신청 번호로 입양 신청 내역을 연다',
  notificationType => {
    handleNotificationOpened({
      notification: chat.notification,
      data: {
        notificationType,
        notificationSeq: '41',
        targetSeq: '301',
        targetType: 'ADOPTION',
      },
    });
    openPendingNotification(true);
    expect(nav.resetRoot).toHaveBeenCalledWith({
      index: 1,
      routes: [
        { name: 'Home' },
        { name: 'AdoptionApplicationHistory', params: { applicationSeq: 301 } },
      ],
    });
  },
);

it('네이티브 알림 URL의 클릭 정보를 복원하고 중복 이벤트는 한 번만 처리한다', () => {
  const stop = subscribeNotificationOpens(() => openPendingNotification(true));
  const url = `usee://notification/local-1?payload=${encodeURIComponent(JSON.stringify(chat))}`;
  handleNotificationURL(url);
  handleNotificationURL(url);
  expect(nav.resetRoot).toHaveBeenCalledTimes(1);
  expect(nav.resetRoot).toHaveBeenCalledWith(
    expect.objectContaining({
      routes: [{ name: 'Home' }, { name: 'ChatRoom', params: { roomId: '501' } }],
    }),
  );
  stop();
});

it('알림마다 보관된 방 번호를 사용하고 다른 앱 URL은 무시한다', () => {
  handleNotificationURL(
    `usee://notification/local-2?payload=${encodeURIComponent(
      JSON.stringify({ ...chat, data: { ...chat.data, roomSeq: '502' } }),
    )}`,
  );
  openPendingNotification(true);
  expect(nav.resetRoot).toHaveBeenLastCalledWith(
    expect.objectContaining({
      routes: [{ name: 'Home' }, { name: 'ChatRoom', params: { roomId: '502' } }],
    }),
  );
  handleNotificationURL('kakao://oauth');
  expect(takePendingNotificationOpen()).toBeNull();
});
