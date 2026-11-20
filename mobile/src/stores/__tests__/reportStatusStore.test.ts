import { useReportStatusStore } from '../reportStatusStore';

describe('report status store', () => {
  beforeEach(() => {
    useReportStatusStore.setState({ statusById: {} });
  });

  it('게시글 상태 변경 결과를 신고별로 유지한다', () => {
    useReportStatusStore.getState().setStatus('lost-1', 'completed');
    useReportStatusStore.getState().setStatus('found-1', 'active');

    expect(useReportStatusStore.getState().statusById).toEqual({
      'found-1': 'active',
      'lost-1': 'completed',
    });
  });

  it('종료된 게시글을 다시 진행 중으로 변경할 수 있다', () => {
    useReportStatusStore.getState().setStatus('lost-3', 'completed');
    useReportStatusStore.getState().setStatus('lost-3', 'active');

    expect(useReportStatusStore.getState().statusById['lost-3']).toBe('active');
  });
});
