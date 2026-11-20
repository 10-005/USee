import type { ComponentProps } from 'react';
import ReactTestRenderer from 'react-test-renderer';
import { useFoundReportStore } from '../../../stores/foundReportStore';
import { useMissingReportStore } from '../../../stores/missingReportStore';
import { useAdoptionBookmarkStore } from '../../../stores/adoptionBookmarkStore';
import { foundReports } from '../data';
import { lostReports } from '../../lost-report/data';
import { FoundReportDetailScreen } from '../FoundReportDetailScreen';
import { CommentComposer } from '../FoundReportCommentList';

jest.mock('react-native-safe-area-context', () =>
  require('react-native-safe-area-context/jest/mock').default,
);
jest.mock('../FoundReportCommentList', () => ({
  ...jest.requireActual('../FoundReportCommentList'),
  FoundReportCommentList: () => null,
}));

it.each([
  ['FoundReportDetail', true],
  ['FoundReportDetail', false],
  ['LostReportDetail', true],
  ['LostReportDetail', false],
] as const)('%s hides post chat only for its owner (isMine=%s)', async (name, isMine) => {
  const foundState = useFoundReportStore.getState();
  const lostState = useMissingReportStore.getState();
  const bookmarkState = useAdoptionBookmarkStore.getState();
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  try {
    useFoundReportStore.setState({
      reportById: { '1': { ...foundReports[0], id: '1', isMine, status: '진행 중' } },
      fetchReport: jest.fn().mockResolvedValue(undefined),
    });
    useMissingReportStore.setState({
      reportById: { '1': { ...lostReports[0], id: '1', isMine, status: '실종' } },
      fetchReport: jest.fn().mockResolvedValue(undefined),
    });
    useAdoptionBookmarkStore.setState({ loadBookmarks: jest.fn().mockResolvedValue(undefined) });
    const props = {
      navigation: { goBack: jest.fn(), navigate: jest.fn(), isFocused: () => true },
      route: { key: 'detail', name, params: { reportId: '1' } },
    } as unknown as ComponentProps<typeof FoundReportDetailScreen>;
    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(<FoundReportDetailScreen {...props} />);
    });
    const chatButtons = renderer.root.findAllByProps({ accessibilityLabel: '채팅 보내기' });
    expect(chatButtons.length > 0).toBe(!isMine);
    expect(renderer.root.findAllByType(CommentComposer)).toHaveLength(1);
  } finally {
    await ReactTestRenderer.act(() => renderer?.unmount());
    useFoundReportStore.setState(foundState, true);
    useMissingReportStore.setState(lostState, true);
    useAdoptionBookmarkStore.setState(bookmarkState, true);
  }
});
