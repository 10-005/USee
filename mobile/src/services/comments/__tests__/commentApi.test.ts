jest.mock('../../api/client', () => ({
  apiClient: { delete: jest.fn(), get: jest.fn(), patch: jest.fn(), post: jest.fn() },
}));

import { apiClient } from '../../api/client';
import {
  createComment,
  deleteComment,
  getCommentThreads,
  updateComment,
} from '../commentApi';

const mockDelete = apiClient.delete as jest.Mock;
const mockGet = apiClient.get as jest.Mock;
const mockPatch = apiClient.patch as jest.Mock;
const mockPost = apiClient.post as jest.Mock;

describe('commentApi', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('루트 댓글과 답글을 조회해 스레드로 구성한다', async () => {
    const root = { commentSeq: 44, commentType: 'FOUND_COMMENT', replyCount: 1 };
    const reply = { commentSeq: 45, commentType: 'FOUND_COMMENT', replyCount: 0 };
    mockGet
      .mockResolvedValueOnce({ data: { data: [root] } })
      .mockResolvedValueOnce({ data: { data: [reply] } });

    await expect(getCommentThreads('FOUND', 101)).resolves.toEqual([
      { ...root, replies: [reply] },
    ]);
    expect(mockGet).toHaveBeenNthCalledWith(1, '/comments', {
      params: { targetSeq: 101, targetType: 'FOUND' },
    });
    expect(mockGet).toHaveBeenNthCalledWith(2, '/comments/FOUND_COMMENT/44/replies');
  });

  it('댓글과 답글을 등록한다', async () => {
    const created = { commentSeq: 45, commentType: 'MISSING_COMMENT', parentCommentSeq: 44 };
    mockPost.mockResolvedValue({ data: { data: created } });
    const request = {
      body: '확인해볼게요.',
      imageSeq: 1701,
      parentCommentSeq: 44,
      targetSeq: 101,
      targetType: 'MISSING' as const,
    };

    await expect(createComment(request)).resolves.toEqual(created);
    expect(mockPost).toHaveBeenCalledWith('/comments', request);
  });

  it('댓글을 수정하고 삭제한다', async () => {
    const updated = { body: '수정된 댓글', commentSeq: 44 };
    mockPatch.mockResolvedValue({ data: { data: updated } });
    mockDelete.mockResolvedValue({});

    await expect(
      updateComment('FOUND_COMMENT', 44, { body: '수정된 댓글' }),
    ).resolves.toEqual(updated);
    await deleteComment('FOUND_COMMENT', 44);

    expect(mockPatch).toHaveBeenCalledWith('/comments/FOUND_COMMENT/44', {
      body: '수정된 댓글',
    });
    expect(mockDelete).toHaveBeenCalledWith('/comments/FOUND_COMMENT/44');
  });
});
