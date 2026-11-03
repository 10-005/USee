import { notices } from '../noticeData';

describe('noticeData', () => {
  it('공지사항 목록의 식별자가 중복되지 않는다', () => {
    expect(new Set(notices.map(notice => notice.id)).size).toBe(notices.length);
  });

  it('공지사항 상세에 필요한 내용을 제공한다', () => {
    expect(notices.every(notice => notice.title && notice.date && notice.content)).toBe(true);
  });
});
