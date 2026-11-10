jest.mock('../../api/client', () => ({
  notificationClient: { get: jest.fn() },
}));

import { notificationClient } from '../../api/client';
import { getMyNotifications } from '../notificationApi';

const mockGet = notificationClient.get as jest.Mock;

const page = {
  content: [],
  hasNext: false,
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
};

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
