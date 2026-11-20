import { act, create, type ReactTestRenderer } from 'react-test-renderer';
import type { ScrollViewInstance } from 'react-native';
import { useInitialCommentScroll } from '../useInitialCommentScroll';

const scrollTo = jest.fn();
const scrollRef = { current: { scrollTo } as unknown as ScrollViewInstance };
let jump: ReturnType<typeof useInitialCommentScroll>;
let screen: ReactTestRenderer;
let frames: Array<(time: number) => void>;
function Probe({ commentId = '55' }: { commentId?: string }) {
  jump = useInitialCommentScroll(scrollRef, '101', commentId);
  return null;
}
beforeEach(() => {
  frames = [];
  scrollTo.mockClear();
  jest.spyOn(globalThis, 'requestAnimationFrame').mockImplementation(callback => {
    frames.push(callback);
    return frames.length;
  });
  jest.spyOn(globalThis, 'cancelAnimationFrame').mockImplementation(() => {});
  act(() => {
    screen = create(<Probe />);
  });
});
afterEach(() => {
  act(() => screen.unmount());
  jest.restoreAllMocks();
});
function layout(target = 1200, height = 2000) {
  act(() => {
    jump.onLayout({ nativeEvent: { layout: { height: 600 } } } as never);
    jump.onContentSizeChange(400, height);
    jump.onTargetLayout(target);
  });
  act(() => frames.splice(0).forEach(callback => callback(0)));
}

it('위치를 계산하면 대기 타이머나 애니메이션 없이 이동하고 도착 후 화면을 공개한다', () => {
  layout();
  expect(scrollTo).toHaveBeenCalledWith({ animated: false, y: 1120 });
  expect(jump.waiting).toBe(true);
  act(() => jump.onScroll({ nativeEvent: { contentOffset: { y: 1120 } } } as never));
  expect(jump.waiting).toBe(false);
  layout(1400);
  expect(scrollTo).toHaveBeenCalledTimes(1);
});

it('마지막 댓글은 실제 스크롤 가능한 범위에서 보여준다', () => {
  layout(1900);
  expect(scrollTo).toHaveBeenCalledWith({ animated: false, y: 1400 });
  act(() => jump.onScroll({ nativeEvent: { contentOffset: { y: 1400 } } } as never));
  expect(jump.waiting).toBe(false);
});

it('전체 콘텐츠 크기가 아직 없으면 이동하지 않고 배치 완료 후 이동한다', () => {
  layout(1200, 0);
  expect(scrollTo).not.toHaveBeenCalled();
  act(() => jump.onContentSizeChange(400, 2000));
  act(() => frames.splice(0).forEach(callback => callback(0)));
  expect(scrollTo).toHaveBeenCalledWith({ animated: false, y: 1120 });
});

it('댓글 삭제나 조회 실패 시 대기 화면에 갇히지 않는다', () => {
  act(() => jump.onTargetUnavailable());
  expect(jump.waiting).toBe(false);
  expect(scrollTo).not.toHaveBeenCalled();
});

it('스크롤할 필요가 없는 짧은 콘텐츠는 바로 표시한다', () => {
  layout(200, 500);
  expect(jump.waiting).toBe(false);
});

it('다른 댓글로 다시 진입하면 새 위치를 기다린다', () => {
  act(() => jump.onTargetUnavailable());
  act(() => screen.update(<Probe commentId="56" />));
  expect(jump.waiting).toBe(true);
  layout(1300);
  expect(scrollTo).toHaveBeenCalledWith({ animated: false, y: 1220 });
});
