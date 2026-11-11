import type { RemoteMessage } from '@react-native-firebase/messaging';
import { AppState } from 'react-native';
import ReactTestRenderer from 'react-test-renderer';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { handleRemoteMessage } from '../services/notifications/remoteMessage';
import { PushPopup } from './PushPopup';

const message = {
  data: {
    notificationSeq: '7',
    notificationType: 'COMMENT',
    targetSeq: '3',
    targetType: 'MISSING',
  },
  notification: { body: '댓글이 도착했어요', title: '새 댓글' },
} as RemoteMessage;

it('앱이 켜져 있으면 알림을 잠시 보여 준 뒤 닫는다', async () => {
  const previous = AppState.currentState;
  AppState.currentState = 'active';
  jest.useFakeTimers();
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  try {
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <SafeAreaProvider
          initialMetrics={{
            frame: { height: 800, width: 400, x: 0, y: 0 },
            insets: { bottom: 0, left: 0, right: 0, top: 24 },
          }}
        >
          <PushPopup />
        </SafeAreaProvider>,
      );
    });

    await ReactTestRenderer.act(async () => {
      await handleRemoteMessage(message);
    });
    expect(renderer.root.findAllByProps({ children: '새 댓글' }).length).toBeGreaterThan(0);

    await ReactTestRenderer.act(async () => {
      jest.advanceTimersByTime(2500);
    });
    expect(renderer.root.findAllByProps({ children: '새 댓글' })).toHaveLength(0);
  } finally {
    if (renderer) {
      await ReactTestRenderer.act(() => renderer.unmount());
    }
    AppState.currentState = previous;
    jest.useRealTimers();
  }
});
