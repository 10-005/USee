import ReactTestRenderer from 'react-test-renderer';
import {
  Alert,
  PanResponder,
  type GestureResponderEvent,
  type PanResponderGestureState,
} from 'react-native';

import { ApiClientError } from '../../services/api';
import {
  SimulationEventStatus,
  SimulationTriggerContext,
  SimulationTriggerType,
} from '../../services/simulation/events';
import { purchaseSimulationItems } from '../../services/simulation/purchase';
import { invalidateSimulationInventory } from '../../services/simulation/inventory';
import { SimulationWalkAction } from '../../services/simulation/actions';
import {
  performSimulationWalkAction,
  sendSimulationWalkHeartbeat,
  startSimulationWalk,
} from '../../services/simulation/walk';
import { replaceSessionShopCatalog } from '../../services/shop/shopDatabase';
import { endActiveSimulationWalk } from '../../services/simulation/walkLifecycle';
import {
  getActiveMockAdoption,
  saveMockAdoptionWalk,
} from '../../services/storage/mockAdoptionStorage';
import { ArWalkScreen } from './ArWalkScreen';
import { styles } from './ArWalkScreen.styles';
import { AppDialog } from '../../components/common/AppDialog';
import { useShopStore } from '../../stores/shopStore';
import { ArFloorView, type FloorTracking } from './components/ArFloorView';
import * as voiceHook from './useWalkVoice';
import { VoiceGlowText } from './components/VoiceGlowText';

jest.mock('@react-navigation/elements', () => ({ useHeaderHeight: () => 80 }));
const mockWalkNavigation = {
  dispatch: jest.fn(),
  listeners: new Map<
    string,
    (event: { preventDefault: () => void; data: { action: unknown } }) => void
  >(),
};

jest.mock('@react-navigation/native', () => ({
  useIsFocused: () => true,
  useNavigation: () => ({
    addListener: (
      event: string,
      callback: (event: { preventDefault: () => void; data: { action: unknown } }) => void,
    ) => {
      mockWalkNavigation.listeners.set(event, callback);
      return () => {
        mockWalkNavigation.listeners.delete(event);
      };
    },
    dispatch: (action: unknown) => mockWalkNavigation.dispatch(action),
  }),
}));
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
jest.mock('../../services/simulation/walkLifecycle', () => ({
  endActiveSimulationWalk: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../../services/simulation/walk', () => ({
  ...jest.requireActual('../../services/simulation/walk'),
  startSimulationWalk: jest.fn(),
  sendSimulationWalkHeartbeat: jest.fn().mockResolvedValue({
    walkSeq: 8001,
    elapsedMinutes: 1,
    stats: {},
    newEvent: null,
    pendingEvent: null,
  }),
  performSimulationWalkAction: jest.fn().mockResolvedValue({ resolvedEvent: null }),
}));
jest.mock('../../services/simulation/inventory', () => ({
  ...jest.requireActual('../../services/simulation/inventory'),
  invalidateSimulationInventory: jest.fn(),
}));

jest.mock('../../services/simulation/purchase', () => ({
  ...jest.requireActual('../../services/simulation/purchase'),
  purchaseSimulationItems: jest.fn(),
}));
jest.mock('../../services/storage/mockAdoptionStorage', () => ({
  getActiveMockAdoption: jest.fn().mockResolvedValue(null),
  saveMockAdoptionWalk: jest.fn(),
}));

const activeSession = {
  version: 1 as const,
  petId: 'dog-1',
  weeklyBudget: 35000,
  startedAt: 1,
  expiresAt: Date.now() + 86400000,
  sessionSeq: 3001,
};

beforeEach(() => {
  mockWalkNavigation.dispatch.mockReset();
  mockWalkNavigation.listeners.clear();
  jest.mocked(getActiveMockAdoption).mockReset();
  jest.mocked(getActiveMockAdoption).mockResolvedValue(null);
  jest.mocked(startSimulationWalk).mockReset();
  jest.mocked(sendSimulationWalkHeartbeat).mockReset();
  jest.mocked(sendSimulationWalkHeartbeat).mockResolvedValue({
    walkSeq: 8001,
    elapsedMinutes: 1,
    stats: {
      satiety: 0,
      hydration: 0,
      cleanliness: 0,
      bond: 0,
      calmness: 0,
      health: 0,
      energy: 0,
      calculatedDttm: '2026-11-09T10:00:00+09:00',
    },
    newEvent: null,
    pendingEvent: null,
  });
  jest.mocked(purchaseSimulationItems).mockReset();
  jest.mocked(performSimulationWalkAction).mockReset();
  jest.mocked(performSimulationWalkAction).mockResolvedValue({
    actionLogSeq: 1,
    actionCode: SimulationWalkAction.GiveWater,
    walkSeq: 8001,
    consumedItem: null,
    statDelta: {},
    stats: {
      satiety: 0,
      hydration: 0,
      cleanliness: 0,
      bond: 0,
      calmness: 0,
      health: 0,
      energy: 0,
      calculatedDttm: '2026-11-09T10:00:00+09:00',
    },
    balance: 0,
    resolvedEvent: null,
  });
  jest.mocked(saveMockAdoptionWalk).mockReset();
  jest.mocked(endActiveSimulationWalk).mockReset();
  jest.mocked(endActiveSimulationWalk).mockResolvedValue(undefined);
});

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
  await ReactTestRenderer.act(async () => {
    confirm();
    confirm();
    await Promise.resolve();
    await Promise.resolve();
  });
  expect(renderer.root.findAllByType(AppDialog)).toHaveLength(0);
  expect(native().props.bagAvailable).toBe(true);
  expect(useShopStore.getState().spent).toBe(500);
  expect(useShopStore.getState().inventory.bags).toBe(10);
  expect(useShopStore.getState().cart).toEqual({ food: 1 });
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('negative budget still purchases bags and closes the dialog', async () => {
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
  expect(renderer.root.findByType(ArFloorView).props.bagAvailable).toBe(true);
  expect(useShopStore.getState().spent).toBe(500);
  expect(useShopStore.getState().budget - useShopStore.getState().spent).toBe(-400);
  expect(renderer.root.findAllByType(AppDialog)).toHaveLength(0);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('buys server poop bags through the cart purchase api with a negative balance', async () => {
  const product = {
    id: '31',
    name: '배변봉투',
    qty: 10,
    unit: '장',
    species: 'dog' as const,
    kind: 'consumable' as const,
    category: '위생',
    description: '산책할 때 1장씩 사용해요.',
    sprite: 0,
    price: 500,
    placement: 'inventory',
    itemSeq: 31,
    serverCategory: 'POOP_BAG',
    imageUrl: null,
    market: { gamePrice: 500, basis: '', sources: [] },
  };
  const session = {
    petId: 'dog-1',
    startedAt: 31,
    expiresAt: Date.now() + 86400000,
    weeklyBudget: 50000,
  };
  replaceSessionShopCatalog(`${session.petId}:${session.startedAt}`, 'DOG', [
    { displayOrder: 0, product },
  ]);
  useShopStore.getState().open(session);
  useShopStore.setState({ spent: 50100 });
  jest
    .mocked(getActiveMockAdoption)
    .mockResolvedValue({ version: 1, ...session, sessionSeq: 3001 });
  jest.mocked(purchaseSimulationItems).mockResolvedValue({
    purchases: [
      {
        transactionSeq: 70003,
        item: { itemSeq: 31, itemName: '배변봉투', category: 'POOP_BAG' },
        quantity: 1,
        amount: 500,
        balanceAfter: -600,
        inventoryQuantity: 10,
      },
    ],
    totalAmount: 500,
    balanceAfter: -600,
  });
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<ArWalkScreen />);
  });
  await ReactTestRenderer.act(() => renderer.root.findByType(ArFloorView).props.onBagPurchase());
  expect(renderer.root.findByType(AppDialog).props.message).toContain('배변봉투');
  await ReactTestRenderer.act(async () => {
    renderer.root.findByType(AppDialog).props.onConfirm();
    await Promise.resolve();
    await Promise.resolve();
  });
  expect(purchaseSimulationItems).toHaveBeenCalledWith(3001, [{ itemSeq: 31, quantity: 1 }]);
  expect(useShopStore.getState().inventory['31']).toBe(10);
  expect(useShopStore.getState().spent).toBe(50600);
  expect(useShopStore.getState().budget - useShopStore.getState().spent).toBe(-600);
  expect(renderer.root.findByType(ArFloorView).props.bagAvailable).toBe(true);
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

test('starts one server walk when the recognized dog starts the timer', async () => {
  jest.mocked(getActiveMockAdoption).mockResolvedValue(activeSession);
  jest.mocked(startSimulationWalk).mockResolvedValue({
    walkSeq: 8001,
    status: 'IN_PROGRESS',
    startedDttm: '2026-11-09T10:00:00',
    heartbeatIntervalSeconds: 60,
    stats: { energy: 80 },
  });
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<ArWalkScreen />);
  });
  const recognize = () =>
    renderer.root.findByType(ArFloorView).props.onTracking({
      nativeEvent: { status: 'anchored', visible: true, onScreen: true },
    });
  await ReactTestRenderer.act(async () => {
    recognize();
    await Promise.resolve();
  });
  expect(startSimulationWalk).toHaveBeenCalledTimes(1);
  expect(startSimulationWalk).toHaveBeenCalledWith(3001);
  expect(saveMockAdoptionWalk).toHaveBeenCalledWith(8001);
  await ReactTestRenderer.act(() => recognize());
  expect(startSimulationWalk).toHaveBeenCalledTimes(1);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('shows a heartbeat event and starts poop only when the event is poop', async () => {
  jest.useFakeTimers();
  jest.setSystemTime(new Date('2026-11-09T10:00:00+09:00'));
  jest.mocked(getActiveMockAdoption).mockResolvedValue(activeSession);
  jest.mocked(startSimulationWalk).mockResolvedValue({
    walkSeq: 8001,
    status: 'IN_PROGRESS',
    startedDttm: '2026-11-09T10:00:00',
    heartbeatIntervalSeconds: 60,
    stats: { energy: 80 },
  });
  const poop = {
    occurrenceSeq: 90010,
    scenario: {
      scenarioSeq: 16,
      title: '길에서 똥을 쌌어요',
      description: '산책하다 길 한가운데서 볼일을 봤어요.',
      triggerContext: SimulationTriggerContext.Walk,
      triggerType: SimulationTriggerType.Random,
    },
    vetVisit: null,
    walkSeq: 8001,
    status: SimulationEventStatus.Pending,
    occurredDttm: '2026-11-09T10:01:00+09:00',
    expiresDttm: '2026-11-09T10:06:00+09:00',
    resolvedDttm: null,
    resolvedActionCode: null,
    resultText: null,
  };
  jest.mocked(sendSimulationWalkHeartbeat).mockResolvedValue({
    walkSeq: 8001,
    elapsedMinutes: 1,
    stats: {
      satiety: 64,
      hydration: 58,
      cleanliness: 77,
      bond: 31,
      calmness: 46,
      health: 90,
      energy: 55,
      calculatedDttm: '2026-11-09T10:01:00+09:00',
    },
    newEvent: poop,
    pendingEvent: null,
  });
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  try {
    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(<ArWalkScreen />);
    });
    await ReactTestRenderer.act(async () => {
      renderer.root.findByType(ArFloorView).props.onTracking({
        nativeEvent: { status: 'anchored', visible: true, onScreen: true },
      });
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(renderer.root.findByType(ArFloorView).props.poopRequest).toBe(0);
    await ReactTestRenderer.act(async () => {
      jest.advanceTimersByTime(60000);
      await Promise.resolve();
    });
    expect(sendSimulationWalkHeartbeat).toHaveBeenCalledWith(3001, 8001);
    expect(renderer.root.findAllByProps({ children: '길에서 똥을 쌌어요' }).length).toBeGreaterThan(
      0,
    );
    expect(renderer.root.findByType(ArFloorView).props.poopRequest).toBe(1);
    jest.mocked(getActiveMockAdoption).mockResolvedValue({ ...activeSession, walkSeq: 8001 });
    jest.mocked(invalidateSimulationInventory).mockClear();
    await ReactTestRenderer.act(async () => {
      renderer.root.findByType(ArFloorView).props.onTracking({
        nativeEvent: { status: 'anchored', visible: true, onScreen: true, cleanupRequestId: 9876 },
      });
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(performSimulationWalkAction).toHaveBeenCalledWith(3001, 8001, {
      actionCode: SimulationWalkAction.CleanUpPoop,
    });
    expect(invalidateSimulationInventory).toHaveBeenCalledWith(3001);
  } finally {
    await ReactTestRenderer.act(() => renderer?.unmount());
    jest.useRealTimers();
  }
});

test('sends water and a snack through the walk action api', async () => {
  jest.mocked(getActiveMockAdoption).mockResolvedValue({ ...activeSession, walkSeq: 8001 });
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<ArWalkScreen />);
  });
  await ReactTestRenderer.act(() => {
    renderer.root.findAllByProps({ style: styles.screen })[0].props.onLayout({
      nativeEvent: { layout: { width: 400, height: 800 } },
    });
  });
  await ReactTestRenderer.act(async () => {
    renderer.root.findByType(ArFloorView).props.onTracking({
      nativeEvent: { status: 'anchored', visible: true, onScreen: true, canGiveCare: true },
    });
    await Promise.resolve();
    await Promise.resolve();
  });
  await ReactTestRenderer.act(async () => {
    renderer.root.findAllByProps({ accessibilityLabel: '물 주기' })[0].props.onPress();
    await Promise.resolve();
  });
  expect(performSimulationWalkAction).toHaveBeenCalledWith(3001, 8001, {
    actionCode: 'GIVE_WATER',
  });
  expect(renderer.root.findByType(ArFloorView).props.careRequest).toMatch(/^1:drink:/);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('keeps the timer when the walk is already recorded for this adoption', async () => {
  jest.mocked(getActiveMockAdoption).mockResolvedValue({ ...activeSession, walkSeq: 8001 });
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<ArWalkScreen />);
  });
  await ReactTestRenderer.act(async () => {
    renderer.root.findByType(ArFloorView).props.onTracking({
      nativeEvent: { status: 'anchored', visible: true, onScreen: true },
    });
    await Promise.resolve();
  });
  expect(startSimulationWalk).not.toHaveBeenCalled();
  expect(
    renderer.root.findAllByProps({ accessibilityLabel: '산책 시간 00:00' }).length,
  ).toBeGreaterThan(0);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('shows the walk start error without stopping the timer', async () => {
  jest.mocked(getActiveMockAdoption).mockResolvedValue(activeSession);
  jest
    .mocked(startSimulationWalk)
    .mockRejectedValue(
      new ApiClientError({ code: 'SIMULATION_WALK_NOT_FOR_SPECIES', message: 'server' }),
    );
  const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<ArWalkScreen />);
  });
  await ReactTestRenderer.act(async () => {
    renderer.root.findByType(ArFloorView).props.onTracking({
      nativeEvent: { status: 'anchored', visible: true, onScreen: true },
    });
    await Promise.resolve();
  });
  expect(alert).toHaveBeenCalledWith('산책', '이 동물은 산책할 수 없어요.');
  expect(
    renderer.root.findAllByProps({ accessibilityLabel: '산책 시간 00:00' }).length,
  ).toBeGreaterThan(0);
  alert.mockRestore();
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('ends the server walk before leaving the walk screen', async () => {
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<ArWalkScreen />);
  });
  const action = { type: 'GO_BACK' };
  const preventDefault = jest.fn();
  await ReactTestRenderer.act(async () => {
    mockWalkNavigation.listeners.get('beforeRemove')?.({
      preventDefault,
      data: { action },
    });
    await Promise.resolve();
  });
  expect(preventDefault).toHaveBeenCalledTimes(1);
  expect(endActiveSimulationWalk).toHaveBeenCalledTimes(1);
  expect(mockWalkNavigation.dispatch).toHaveBeenCalledWith(action);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('shows only the ending overlay when leaving during terrain recognition', async () => {
  let finish: () => void = () => undefined;
  jest.mocked(endActiveSimulationWalk).mockImplementation(
    () =>
      new Promise(resolve => {
        finish = () => resolve();
      }),
  );
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<ArWalkScreen />);
  });
  const recognition = () => renderer.root.findAllByProps({ accessibilityLabel: '지형 인식 중' });
  expect(recognition().length).toBeGreaterThan(0);
  const action = { type: 'GO_BACK' };
  const leave = (preventDefault = jest.fn()) => {
    mockWalkNavigation.listeners.get('beforeRemove')?.({
      preventDefault,
      data: { action },
    });
    return preventDefault;
  };
  await ReactTestRenderer.act(async () => {
    leave();
    await Promise.resolve();
  });
  expect(endActiveSimulationWalk).toHaveBeenCalledTimes(1);
  expect(
    renderer.root.findAllByProps({ accessibilityLabel: '산책 종료 중' }).length,
  ).toBeGreaterThan(0);
  expect(recognition()).toHaveLength(0);
  expect(mockWalkNavigation.dispatch).not.toHaveBeenCalled();
  const secondPrevent = jest.fn();
  await ReactTestRenderer.act(() => {
    leave(secondPrevent);
  });
  expect(secondPrevent).toHaveBeenCalledTimes(1);
  expect(endActiveSimulationWalk).toHaveBeenCalledTimes(1);
  await ReactTestRenderer.act(async () => {
    finish();
    await Promise.resolve();
  });
  expect(mockWalkNavigation.dispatch).toHaveBeenCalledWith(action);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('stays on the walk screen when the walk cannot be ended', async () => {
  jest.mocked(endActiveSimulationWalk).mockRejectedValue(new Error('offline'));
  const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<ArWalkScreen />);
  });
  await ReactTestRenderer.act(async () => {
    mockWalkNavigation.listeners.get('beforeRemove')?.({
      preventDefault: jest.fn(),
      data: { action: { type: 'GO_BACK' } },
    });
    await Promise.resolve();
  });
  expect(alert).toHaveBeenCalledWith('산책', '산책을 종료하지 못했어요. 다시 시도해주세요.');
  expect(mockWalkNavigation.dispatch).not.toHaveBeenCalled();
  expect(renderer.root.findAllByProps({ accessibilityLabel: '산책 종료 중' })).toHaveLength(0);
  expect(
    renderer.root.findAllByProps({ accessibilityLabel: '지형 인식 중' }).length,
  ).toBeGreaterThan(0);
  alert.mockRestore();
  await ReactTestRenderer.act(() => renderer.unmount());
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
