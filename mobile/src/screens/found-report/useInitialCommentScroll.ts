import { useCallback, useLayoutEffect, useRef, useState, type RefObject } from 'react';
import type {
  LayoutChangeEvent,
  NativeScrollEvent,
  NativeSyntheticEvent,
  ScrollViewInstance,
} from 'react-native';

export function useInitialCommentScroll(
  scrollRef: RefObject<ScrollViewInstance | null>,
  reportId: string,
  commentId?: string,
) {
  const key = `${reportId}:${commentId ?? ''}`;
  const [completedKey, setCompletedKey] = useState<string | null>(null);
  const geometry = useRef({ content: 0, viewport: 0, target: null as number | null, offset: 0 });
  const expectedOffset = useRef<number | null>(null);
  const finished = useRef(!commentId);
  const frame = useRef<number | null>(null);
  useLayoutEffect(() => {
    geometry.current = { content: 0, viewport: 0, target: null, offset: 0 };
    expectedOffset.current = null;
    finished.current = !commentId;
    return () => {
      if (frame.current !== null) {
        cancelAnimationFrame(frame.current);
      }
      frame.current = null;
    };
  }, [key, commentId]);

  const complete = useCallback(() => {
    finished.current = true;
    setCompletedKey(key);
  }, [key]);

  const position = useCallback(() => {
    if (finished.current || frame.current !== null) {
      return;
    }
    // Coalesce native layout events, then jump without displaying the top of the post.
    frame.current = requestAnimationFrame(() => {
      frame.current = null;
      const { content, viewport, target, offset } = geometry.current;
      if (
        finished.current ||
        target === null ||
        viewport <= 0 ||
        content <= target ||
        !scrollRef.current
      ) {
        return;
      }
      const y = Math.min(Math.max(0, target - 80), Math.max(0, content - viewport));
      expectedOffset.current = y;
      scrollRef.current.scrollTo({ animated: false, y });
      if (Math.abs(offset - y) <= 1) {
        complete();
      }
    });
  }, [complete, scrollRef]);

  return {
    waiting: Boolean(commentId) && completedKey !== key,
    onTargetUnavailable: complete,
    onTargetLayout: useCallback(
      (y: number) => {
        geometry.current.target = y;
        position();
      },
      [position],
    ),
    onContentSizeChange: useCallback(
      (_width: number, height: number) => {
        geometry.current.content = height;
        position();
      },
      [position],
    ),
    onLayout: useCallback(
      (event: LayoutChangeEvent) => {
        geometry.current.viewport = event.nativeEvent.layout.height;
        position();
      },
      [position],
    ),
    onScroll: useCallback(
      (event: NativeSyntheticEvent<NativeScrollEvent>) => {
        const y = event.nativeEvent.contentOffset.y;
        geometry.current.offset = y;
        if (
          !finished.current &&
          expectedOffset.current !== null &&
          Math.abs(y - expectedOffset.current) <= 1
        ) {
          complete();
        }
      },
      [complete],
    ),
  };
}
