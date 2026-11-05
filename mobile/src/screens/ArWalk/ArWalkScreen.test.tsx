import ReactTestRenderer from 'react-test-renderer';

import { ArWalkScreen } from './ArWalkScreen';
import { useShopStore } from '../../stores/shopStore';
import { ArFloorView, type FloorTracking } from './components/ArFloorView';

jest.mock('@react-navigation/elements', () => ({ useHeaderHeight: () => 80 }));
jest.mock('@react-navigation/native', () => ({ useIsFocused: () => true }));
jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
}));
jest.mock('react-native-vision-camera', () => ({
  useCameraPermission: () => ({ hasPermission: true, canRequestPermission: false }),
}));
jest.mock('./components/ArFloorView', () => ({ ArFloorView: 'ArFloorView' }));
jest.mock('./components/VoiceGlowText', () => ({ VoiceGlowText: 'VoiceGlowText' }));

test('deducts a bag once per cleanup request and acknowledges only a saved deduction', async () => {
  const session = {
    petId: 'dog-1',
    startedAt: Date.now(),
    expiresAt: Date.now() + 86400000,
    weeklyBudget: 50000,
  };
  useShopStore.getState().open(session);
  useShopStore.getState().setQuantity('bags', 1, 'dog');
  useShopStore.getState().checkout('dog');
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<ArWalkScreen />);
  });
  const tracking = () => renderer.root.findByType(ArFloorView);
  const event = {
    nativeEvent: { status: 'anchored', visible: true, onScreen: true, cleanupRequestId: 1234 },
  };
  await ReactTestRenderer.act(() => tracking().props.onTracking(event));
  await ReactTestRenderer.act(() => tracking().props.onTracking(event));
  expect(useShopStore.getState().inventory.bags).toBe(9);
  expect(tracking().props.cleanupResponse).toBe('1234:1');
  await ReactTestRenderer.act(() => {
    useShopStore.getState().consume({ bags: 9 });
  });
  await ReactTestRenderer.act(() =>
    tracking().props.onTracking({ nativeEvent: { ...event.nativeEvent, cleanupRequestId: 5678 } }),
  );
  expect(tracking().props.cleanupResponse).toBe('5678:0');
  expect(useShopStore.getState().inventory.bags).toBe(0);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('walk timer starts on user movement and keeps elapsed time through stops', async () => {
  jest.useFakeTimers();
  let renderer: ReactTestRenderer.ReactTestRenderer | undefined;
  try {
    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(<ArWalkScreen />);
    });
    const time = (label: string) =>
      renderer!.root.findAllByProps({ accessibilityLabel: `산책 시간 ${label}` });
    const tracking = async (userMoving: boolean) => {
      await ReactTestRenderer.act(() => {
        renderer!.root.findByType(ArFloorView).props.onTracking({
          nativeEvent: { status: 'anchored', visible: true, onScreen: true, userMoving },
        });
      });
    };
    await tracking(false);
    await ReactTestRenderer.act(() => jest.advanceTimersByTime(5000));
    expect(time('00:00').length).toBeGreaterThan(0);
    await tracking(true);
    await ReactTestRenderer.act(() => jest.advanceTimersByTime(61000));
    expect(time('01:01').length).toBeGreaterThan(0);
    await tracking(false);
    await tracking(true);
    await ReactTestRenderer.act(() => jest.advanceTimersByTime(2000));
    expect(time('01:03').length).toBeGreaterThan(0);
  } finally {
    await ReactTestRenderer.act(() => renderer?.unmount());
    jest.useRealTimers();
  }
});

test('placement remains visible during analysis and hides only for visible dogs or moving users', async () => {
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<ArWalkScreen />);
  });
  const buttons = () => renderer.root.findAllByProps({ accessibilityLabel: '강아지 배치하기' });
  const tracking = async (state: Partial<FloorTracking>) => {
    await ReactTestRenderer.act(() => {
      renderer.root.findByType(ArFloorView).props.onTracking({ nativeEvent: state });
    });
  };
  await tracking({ status: 'analyzing', onScreen: false, userMoving: false });
  expect(buttons().length).toBeGreaterThan(0);
  expect(buttons()[0].props.disabled).toBe(true);
  await tracking({ status: 'anchored', onScreen: false, userMoving: false });
  expect(buttons()[0].props.disabled).toBe(false);
  await tracking({ status: 'anchored', onScreen: true, userMoving: false });
  expect(buttons()).toHaveLength(0);
  await tracking({ status: 'anchored', onScreen: false, userMoving: true });
  expect(buttons()).toHaveLength(0);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('area toggle remains available without a notices toggle', async () => {
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<ArWalkScreen />);
  });
  const native = () => renderer.root.findByType(ArFloorView).props;
  const toggle = () =>
    renderer.root.findAllByProps({ accessibilityLabel: '이동 가능 영역 표시' })[0];
  expect(native().showTraversalDebug).toBe(false);
  expect(renderer!.root.findAllByProps({ accessibilityLabel: '상단 안내 표시' })).toHaveLength(0);
  await ReactTestRenderer.act(() => toggle().props.onPress());
  expect(native().showTraversalDebug).toBe(true);
  await ReactTestRenderer.act(() => toggle().props.onPress());
  expect(native().showTraversalDebug).toBe(false);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('startup loading animates until the dog first appears on screen', async () => {
  jest.useFakeTimers();
  let renderer: ReactTestRenderer.ReactTestRenderer | undefined;
  try {
    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(<ArWalkScreen />);
    });
    const loading = () => renderer!.root.findAllByProps({ accessibilityLabel: '지형 인식 중' });
    expect(loading()[0].props.children.join('')).toBe('지형 인식 중..');
    await ReactTestRenderer.act(() => jest.advanceTimersByTime(400));
    expect(loading()[0].props.children.join('')).toBe('지형 인식 중...');
    const tracking = async (visible: boolean, onScreen: boolean) => {
      await ReactTestRenderer.act(() => {
        renderer!.root.findByType(ArFloorView).props.onTracking({
          nativeEvent: { status: 'anchored', visible, onScreen, userMoving: false },
        });
      });
    };
    await tracking(true, false);
    expect(loading().length).toBeGreaterThan(0);
    await tracking(true, true);
    expect(loading()).toHaveLength(0);
    await tracking(false, false);
    expect(loading()).toHaveLength(0);
  } finally {
    await ReactTestRenderer.act(() => renderer?.unmount());
    jest.useRealTimers();
  }
});
