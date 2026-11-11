import { FlatList } from 'react-native';
import renderer, { act } from 'react-test-renderer';
import { NotificationListScreen } from '../NotificationListScreen';
import {
  getMyNotifications,
  type NotificationItem,
  type NotificationPage,
} from '../../../services/notifications/notificationApi';
import { subscribePushMessages } from '../../../services/notifications/remoteMessage';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (effect: () => void) => require('react').useEffect(effect, [effect]),
}));
jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);
jest.mock('../../../services/notifications/notificationApi', () => ({
  getMyNotifications: jest.fn(),
  markNotificationAsRead: jest.fn(),
  deleteNotification: jest.fn(),
  deleteAllNotifications: jest.fn(),
}));
jest.mock('../../../services/notifications/remoteMessage', () => ({
  subscribePushMessages: jest.fn(() => jest.fn()),
}));

const item: NotificationItem = {
  notificationSeq: 1,
  notificationType: 'COMMENT',
  title: '댓글 알림',
  message: '내용',
  isRead: false,
  commentSeq: 55,
  targetSeq: 101,
  targetType: 'MISSING',
  missingReportSeq: null,
  parentCommentSeq: null,
  readDttm: null,
  regDttm: null,
};
const page = (id: number): NotificationPage => ({
  content: [{ ...item, notificationSeq: id }],
  hasNext: false,
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
});
const received = {
  kind: 'server' as const,
  notificationType: 'COMMENT' as const,
  notificationSeq: '2',
  title: '새 알림',
  body: '내용',
};
let screen: renderer.ReactTestRenderer;
beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(getMyNotifications).mockResolvedValue(page(1));
});
afterEach(async () => {
  await act(async () => screen.unmount());
});
async function renderScreen() {
  await act(async () => {
    screen = renderer.create(
      <NotificationListScreen
        navigation={{ goBack: jest.fn() } as never}
        route={{ name: 'Notifications', key: 'notifications' }}
      />,
    );
  });
}
async function receive() {
  await act(async () => {
    jest.mocked(subscribePushMessages).mock.calls.at(-1)![0](received);
  });
}

it('새 알림 수신 시 목록 API를 다시 호출하고 응답으로 갱신한다', async () => {
  await renderScreen();
  jest.mocked(getMyNotifications).mockResolvedValueOnce(page(2));
  await receive();
  expect(getMyNotifications).toHaveBeenCalledTimes(2);
  expect(screen.root.findByType(FlatList).props.data[0].id).toBe('2');
});

it('현재 읽지 않음 필터로 새 알림을 다시 조회한다', async () => {
  await renderScreen();
  const filter = screen.root
    .findAllByProps({ accessibilityLabel: '읽지 않음' })
    .find(node => typeof node.props.onPress === 'function')!;
  await act(async () => filter.props.onPress());
  await receive();
  expect(getMyNotifications).toHaveBeenLastCalledWith({ isRead: false });
});

it('늦게 도착한 이전 조회 응답이 최신 알림 목록을 덮어쓰지 않는다', async () => {
  let completeOld!: (value: NotificationPage) => void;
  jest.mocked(getMyNotifications).mockReturnValueOnce(
    new Promise(resolve => {
      completeOld = resolve;
    }),
  );
  await renderScreen();
  jest.mocked(getMyNotifications).mockResolvedValueOnce(page(2));
  await receive();
  await act(async () => completeOld(page(1)));
  expect(screen.root.findByType(FlatList).props.data[0].id).toBe('2');
});

it('채팅 알림은 목록 재조회를 발생시키지 않는다', async () => {
  await renderScreen();
  await act(async () => {
    jest.mocked(subscribePushMessages).mock.calls.at(-1)![0]({
      kind: 'server',
      notificationType: 'CHAT',
      title: '채팅',
      body: '메시지',
      roomSeq: '501',
      messageSeq: '9001',
      senderDisplayName: '보호소',
      preview: '메시지',
    });
  });
  expect(getMyNotifications).toHaveBeenCalledTimes(1);
});
