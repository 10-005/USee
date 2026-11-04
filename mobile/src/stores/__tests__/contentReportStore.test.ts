import { useContentReportStore } from '../contentReportStore';

describe('content report store', () => {
  beforeEach(() => {
    useContentReportStore.setState({ reports: [] });
  });

  it('게시글·댓글·답글 신고 내역을 대상별로 저장한다', () => {
    useContentReportStore.getState().addReport({
      reason: '허위 정보 / 잘못된 동물 정보',
      targetId: 'post:found-1',
      targetType: 'post',
    });
    useContentReportStore.getState().addReport({
      reason: '스팸 / 광고',
      targetId: 'found-1:comment:comment-1',
      targetType: 'comment',
    });

    expect(useContentReportStore.getState().reports).toHaveLength(2);
  });

  it('같은 대상을 중복 저장하지 않는다', () => {
    const report = {
      reason: '욕설 / 불쾌한 내용',
      targetId: 'post:found-2',
      targetType: 'post' as const,
    };

    useContentReportStore.getState().addReport(report);
    useContentReportStore.getState().addReport(report);

    expect(useContentReportStore.getState().reports).toEqual([report]);
  });
});
