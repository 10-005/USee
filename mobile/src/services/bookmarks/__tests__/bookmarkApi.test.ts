jest.mock('../../api/client', () => ({
  apiClient: { delete: jest.fn(), get: jest.fn(), post: jest.fn() },
}));

import { apiClient } from '../../api/client';
import { addBookmark, getBookmarks, removeBookmark } from '../bookmarkApi';

describe('bookmarkApi', () => {
  it('목록 조회와 등록·해제를 요청한다', async () => {
    (apiClient.get as jest.Mock).mockResolvedValue({ data: { data: { content: [] } } });
    (apiClient.post as jest.Mock).mockResolvedValue({
      data: { data: { bookmarkSeq: 1, targetSeq: 21, targetType: 'FOUND' } },
    });
    (apiClient.delete as jest.Mock).mockResolvedValue({});

    await getBookmarks('FOUND');
    await addBookmark('FOUND', 21);
    await removeBookmark('FOUND', 21);

    expect(apiClient.get).toHaveBeenCalledWith('/users/me/bookmarks', {
      params: { page: 0, size: 100, sort: 'regDttm,desc', targetType: 'FOUND' },
    });
    expect(apiClient.post).toHaveBeenCalledWith('/users/me/bookmarks', {
      targetSeq: 21,
      targetType: 'FOUND',
    });
    expect(apiClient.delete).toHaveBeenCalledWith('/users/me/bookmarks/FOUND/21');
  });
});
