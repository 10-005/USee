import { foundReportComments, getCommentContent, getCommentCount } from '../comments';

describe('found report comments', () => {
  it('최상위 댓글 개수를 계산한다', () => {
    expect(getCommentCount(foundReportComments)).toBe(5);
  });

  it('삭제된 댓글은 안내 문구로 표시한다', () => {
    const deletedComment = foundReportComments.find(comment => comment.deleted);

    expect(deletedComment).toBeDefined();
    expect(getCommentContent(deletedComment!)).toBe('삭제된 댓글입니다.');
  });

  it('답글은 한 단계 배열로 유지한다', () => {
    const commentWithReplies = foundReportComments.find(comment => comment.replies.length > 0);

    expect(commentWithReplies?.replies).toHaveLength(2);
    expect(commentWithReplies?.replies.every(reply => !('replies' in reply))).toBe(true);
  });
});
