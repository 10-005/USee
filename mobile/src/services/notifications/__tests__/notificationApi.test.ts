jest.mock('../../api/client', () => ({
  notificationClient: { get: jest.fn(), patch: jest.fn() },
}));

import { notificationClient } from '../../api/client';
import { getMyNotifications, markNotificationAsRead } from '../notificationApi';

const mockGet = notificationClient.get as jest.Mock;
const mockPatch = notificationClient.patch as jest.Mock;

const page = {
  content: [],
  hasNext: false,
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
};

describe('markNotificationAsRead', () => {
  beforeEach(() => {
    mockPatch.mockReset();
  });

  it('알림 번호로 읽음 처리하고 읽은 시각을 반환한다', async () => {
    const data = {
      isRead: true,
      notificationSeq: 805,
      readDttm: '2026-10-29T18:00:00+09:00',
    };
    mockPatch.mockResolvedValue({ data: { data } });

    await expect(markNotificationAsRead(805)).resolves.toEqual(data);
    expect(mockPatch).toHaveBeenCalledWith('/notifications/805/read');
  });
});

describe('getMyNotifications', () => {
  beforeEach(() => {
    mockGet.mockReset();
  });

  it('기본 정렬로 내 알림 목록을 조회한다', async () => {
    mockGet.mockResolvedValue({ data: { data: page } });

    await expect(getMyNotifications()).resolves.toEqual(page);
    expect(mockGet).toHaveBeenCalledWith('/notifications', {
      params: { page: 0, size: 20, sort: 'regDttm,desc' },
    });
  });

  it('읽지 않음과 AI 매칭 필터를 query로 보낸다', async () => {
    mockGet.mockResolvedValue({ data: { data: page } });

    await getMyNotifications({ isRead: false, notificationType: 'AI_MATCH' });

    expect(mockGet).toHaveBeenCalledWith('/notifications', {
      params: {
        isRead: false,
        notificationType: 'AI_MATCH',
        page: 0,
        size: 20,
        sort: 'regDttm,desc',
      },
    });
  });
});
