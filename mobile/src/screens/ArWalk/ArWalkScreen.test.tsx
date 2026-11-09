import ReactTestRenderer from 'react-test-renderer';
import {
  PanResponder,
  type GestureResponderEvent,
  type PanResponderGestureState,
} from 'react-native';

import { ArWalkScreen } from './ArWalkScreen';
import { styles } from './ArWalkScreen.styles';
import { AppDialog } from '../../components/common/AppDialog';
import { useShopStore } from '../../stores/shopStore';
import { ArFloorView, type FloorTracking } from './components/ArFloorView';
import * as voiceHook from './useWalkVoice';
import { VoiceGlowText } from './components/VoiceGlowText';

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
jest.mock('./components/VoiceMicrophone', () => ({ VoiceMicrophone: 'VoiceMicrophone' }));
jest.mock('./useWalkRoute', () => ({
  useWalkRoute: () => ({ segments: [], meters: 0, message: '' }),
}));
jest.mock('./components/WalkRouteMap', () => ({ WalkRouteMap: 'WalkRouteMap' }));

test('water uses a tap and treats require an upward drag', async () => {
  const panResponder = jest.spyOn(PanResponder, 'create');
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  try {
    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(<ArWalkScreen />);
    });
    const native = () => renderer.root.findByType(ArFloorView);
    const waterButton = () => renderer.root.findAllByProps({ accessibilityLabel: '물 주기' });
    expect(waterButton()).toHaveLength(0);
    await ReactTestRenderer.act(() => {
      renderer.root.findAllByProps({ style: styles.screen })[0].props.onLayout({
        nativeEvent: { layout: { width: 400, height: 800 } },
      });
      native().props.onTracking({ nativeEvent: { status: 'anchored', canGiveCare: true } });
    });
    await ReactTestRenderer.act(() => waterButton()[0].props.onPress());
    expect(native().props.careRequest).toMatch(/^1:drink:/);

    const gesture = panResponder.mock.calls.at(-1)![0];
    const release = (dy: number) => {
      gesture.onPanResponderRelease!(
        {} as GestureResponderEvent,
        { dx: 12, dy } as PanResponderGestureState,
      );
    };
    await ReactTestRenderer.act(() => release(0));
    expect(native().props.careRequest).toMatch(/^1:drink:/);
    await ReactTestRenderer.act(() => release(-70));
    expect(native().props.careRequest).toBe('2:eat:0.6025:0.85125');
  } finally {
    await ReactTestRenderer.act(() => renderer?.unmount());
    panResponder.mockRestore();
  }
});

test('speech button waits for placement and subtitles exist only while listening', async () => {
  const startListening = jest.fn();
  const state = {
    startListening,
    isListening: false,
    isStarting: false,
    message: '',
    error: '',
    actionRequest: '',
  };
  const hook = jest.spyOn(voiceHook, 'useWalkVoice').mockReturnValue(state);
  let renderer: ReactTestRenderer.ReactTestRenderer | undefined;
  try {
    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(<ArWalkScreen />);
    });
    const button = () =>
      renderer!.root.findAllByProps({ accessibilityLabel: '산책 음성 말하기' })[0];
    expect(button().props.disabled).toBe(true);
    expect(renderer!.root.findAllByType(VoiceGlowText)).toHaveLength(0);
    await ReactTestRenderer.act(() =>
      renderer!.root.findByType(ArFloorView).props.onTracking({
        nativeEvent: { status: 'anchored', visible: true, onScreen: true },
      }),
    );
    expect(button().props.disabled).toBe(false);
    await ReactTestRenderer.act(() => button().props.onPress());
    expect(startListening).toHaveBeenCalledTimes(1);
    hook.mockReturnValue({ ...state, isListening: true, message: '“초코야 앉아”' });
    await ReactTestRenderer.act(() => renderer!.update(<ArWalkScreen />));
    expect(renderer!.root.findByType(VoiceGlowText).props.text).toBe('“초코야 앉아”');
    expect(
      renderer!.root.findAllByProps({ accessibilityLabel: '산책 음성 듣는 중' })[0].props.disabled,
    ).toBe(true);
    hook.mockReturnValue({ ...state, message: '“초코야 앉아”' });
    await ReactTestRenderer.act(() => renderer!.update(<ArWalkScreen />));
    expect(renderer!.root.findAllByType(VoiceGlowText)).toHaveLength(0);
  } finally {
    await ReactTestRenderer.act(() => renderer?.unmount());
    hook.mockRestore();
  }
});

test('empty-bag dialog buys only bags, debits the budget once and enables the native bag', async () => {
  useShopStore.getState().open({
    petId: 'walk-purchase-dog',
    startedAt: 123,
    expiresAt: Date.now() + 86400000,
    weeklyBudget: 50000,
  });
  useShopStore.getState().setQuantity('food', 1, 'dog');
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<ArWalkScreen />);
  });
  const native = () => renderer.root.findByType(ArFloorView);
  expect(native().props.bagAvailable).toBe(false);
  await ReactTestRenderer.act(() => native().props.onBagPurchase());
  const dialog = renderer.root.findByType(AppDialog);
  expect(dialog.props.message).toContain('10장');
  const confirm = dialog.props.onConfirm;
  await ReactTestRenderer.act(() => {
    confirm();
    confirm();
  });
  expect(renderer.root.findAllByType(AppDialog)).toHaveLength(0);
  expect(native().props.bagAvailable).toBe(true);
  expect(useShopStore.getState().spent).toBe(500);
  expect(useShopStore.getState().inventory.bags).toBe(10);
  expect(useShopStore.getState().cart).toEqual({ food: 1 });
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('insufficient budget keeps bag purchase dialog open without granting bags', async () => {
  useShopStore.getState().open({
    petId: 'walk-poor-dog',
    startedAt: 124,
    expiresAt: Date.now() + 86400000,
    weeklyBudget: 100,
  });
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<ArWalkScreen />);
  });
  await ReactTestRenderer.act(() => renderer.root.findByType(ArFloorView).props.onBagPurchase());
  await ReactTestRenderer.act(() => renderer.root.findByType(AppDialog).props.onConfirm());
  expect(renderer.root.findByType(AppDialog).props.message).toContain('남은 예산이 부족해요.');
  expect(renderer.root.findByType(ArFloorView).props.bagAvailable).toBe(false);
  expect(useShopStore.getState().spent).toBe(0);
  await ReactTestRenderer.act(() => renderer.root.findByType(AppDialog).props.onClose());
  expect(renderer.root.findAllByType(AppDialog)).toHaveLength(0);
  await ReactTestRenderer.act(() => renderer.unmount());
});

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

test('walk timer starts when the dog is first recognized and keeps elapsed time through stops', async () => {
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
    expect(time('00:05').length).toBeGreaterThan(0);
    await tracking(true);
    await ReactTestRenderer.act(() => jest.advanceTimersByTime(61000));
    expect(time('01:06').length).toBeGreaterThan(0);
    await tracking(false);
    await tracking(true);
    await ReactTestRenderer.act(() => jest.advanceTimersByTime(2000));
    expect(time('01:08').length).toBeGreaterThan(0);
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
