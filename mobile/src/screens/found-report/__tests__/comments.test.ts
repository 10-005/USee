import {
  commentReducer,
  foundReportComments,
  getCommentContent,
  getCommentCount,
} from '../comments';

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

  it('댓글과 답글을 추가하고 수정한다', () => {
    const added = commentReducer(foundReportComments, {
      comment: {
        content: '새 댓글',
        createdAt: '방금 전',
        id: 'new-comment',
        isMine: true,
        nickname: '내 닉네임',
        replies: [],
      },
      type: 'addComment',
    });
    const replied = commentReducer(added, {
      commentId: 'new-comment',
      reply: {
        content: '새 답글',
        createdAt: '방금 전',
        id: 'new-reply',
        isMine: true,
        nickname: '내 닉네임',
      },
      type: 'addReply',
    });
    const updated = commentReducer(replied, {
      commentId: 'new-reply',
      content: '수정된 답글',
      type: 'update',
    });

    expect(updated[0].replies[0].content).toBe('수정된 답글');
  });

  it('삭제한 댓글은 복구 불가능한 표시 상태로 변경한다', () => {
    const deleted = commentReducer(foundReportComments, { commentId: 'comment-1', type: 'delete' });

    expect(deleted[0].deleted).toBe(true);
    expect(deleted[0].content).toBe('');
  });
});
