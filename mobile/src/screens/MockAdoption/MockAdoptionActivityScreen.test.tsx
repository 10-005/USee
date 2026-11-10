import { STORAGE_KEYS } from '../../constants/storageKeys';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ApiClientError } from '../../services/api';
import { abandonSimulation } from '../../services/simulation/abandon';
import { performCareAction } from '../../services/simulation/care';
import { invalidateSimulationReports } from '../../services/simulation/reports';
import {
  getActiveMockAdoption,
  startMockAdoption,
} from '../../services/storage/mockAdoptionStorage';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import ReactTestRenderer from 'react-test-renderer';
import { Alert, Animated, BackHandler, StyleSheet } from 'react-native';
import type { RootStackParamList } from '../../app/navigation/types';
import { MockAdoptionActivityScreen } from './MockAdoptionActivityScreen';
import { PetStatsDialog } from './PetStatsDialog';
import { VetVisitDialog } from './VetVisitDialog';
import { useShopStore } from '../../stores/shopStore';

jest.mock('../../services/simulation/shopItems', () => {
  const actual = jest.requireActual('../../services/simulation/shopItems');
  return {
    ...actual,
    ensureSessionShopCatalog: jest.fn().mockResolvedValue(undefined),
  };
});

jest.mock('../../services/simulation/care', () => {
  const actual = jest.requireActual('../../services/simulation/care');
  return {
    ...actual,
    performCareAction: jest.fn(),
  };
});

jest.mock('../../services/simulation/reports', () => ({
  invalidateSimulationReports: jest.fn(),
}));

jest.mock('../../services/simulation/abandon', () => {
  const actual = jest.requireActual('../../services/simulation/abandon');
  return {
    ...actual,
    abandonSimulation: jest.fn(),
  };
});

jest.mock('./RoomPet', () => ({ RoomPet: 'RoomPet' }));

beforeEach(() => {
  jest.mocked(invalidateSimulationReports).mockClear();
});

test('hospital close preserves issues, payment debits budget, and ignore advances the queue', async () => {
  await AsyncStorage.clear();
  const session = await startMockAdoption('dog-1', 50000);
  useShopStore.getState().open(session);
  const props = {
    navigation: {},
    route: { key: 'hospital', name: 'MockAdoptionActivity', params: { petId: 'dog-1' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  const hospital = () => renderer.root.findAllByProps({ accessibilityLabel: '동물병원' })[0];
  expect(hospital().props.disabled).toBe(true);
  await ReactTestRenderer.act(() =>
    renderer.root
      .findAllByProps({ accessibilityLabel: '돌발 이벤트 팝업 미리보기' })[0]
      .props.onPress(),
  );
  const event = useShopStore.getState().hospitalEvents![0];
  await ReactTestRenderer.act(() => {
    useShopStore
      .getState()
      .enqueueHospitalEvent({ ...event, occurrenceSeq: event.occurrenceSeq + 1 });
    hospital().props.onPress();
  });
  expect(renderer.root.findByType(VetVisitDialog).props.remaining).toBe(2);
  await ReactTestRenderer.act(() => renderer.root.findByType(VetVisitDialog).props.onClose());
  expect(renderer.root.findAllByType(VetVisitDialog)).toHaveLength(0);
  expect(useShopStore.getState().spent).toBe(0);
  await ReactTestRenderer.act(() => hospital().props.onPress());
  await ReactTestRenderer.act(() => renderer.root.findByType(VetVisitDialog).props.onPay());
  expect(useShopStore.getState().spent).toBe(120000);
  expect(renderer.root.findByType(VetVisitDialog).props.remaining).toBe(1);
  await ReactTestRenderer.act(() => renderer.root.findByType(VetVisitDialog).props.onIgnore());
  expect(useShopStore.getState().spent).toBe(120000);
  expect(hospital().props.disabled).toBe(true);
  expect(renderer.root.findAllByType(VetVisitDialog)).toHaveLength(0);
  await ReactTestRenderer.act(() => renderer.unmount());
});
let mockFocusRoom: () => () => void;
jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (effect: () => () => void) => {
    mockFocusRoom = effect;
    require('react').useEffect(effect, [effect]);
  },
}));

jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);

test('system back waits for the model surface to disappear and ignores repeated requests', async () => {
  let systemBack: () => boolean | null | undefined = () => false;
  const subscribe = jest
    .spyOn(BackHandler, 'addEventListener')
    .mockImplementation((_event, callback) => {
      systemBack = () => callback({ type: 'hardwareBackPress', timeStamp: 0 });
      return { remove: jest.fn() };
    });
  const goBack = jest.fn();
  const props = {
    navigation: { goBack },
    route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId: 'dog-1' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  await ReactTestRenderer.act(() =>
    renderer.root
      .findAll(node => typeof node.props.onLayout === 'function')[0]
      .props.onLayout({
        nativeEvent: { layout: { width: 390, height: 844 } },
      }),
  );
  await ReactTestRenderer.act(() => {
    expect(systemBack()).toBe(true);
    expect(systemBack()).toBe(true);
  });
  expect(goBack).not.toHaveBeenCalled();
  const onHidden = renderer!.root.findByType('RoomPet' as never).props.onHidden;
  await ReactTestRenderer.act(() => {
    onHidden();
    onHidden();
  });
  expect(goBack).toHaveBeenCalledTimes(1);
  await ReactTestRenderer.act(() => renderer.unmount());
  subscribe.mockRestore();
});

test('activity screen has one action connected to the existing AR walk', async () => {
  const navigate = jest.fn();
  const goBack = jest.fn();
  const props = {
    navigation: { navigate, goBack },
    route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId: 'cat-2' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  const button = renderer!.root.findAllByProps({ accessibilityLabel: 'AR 산책하기' })[0];
  expect(button.props.accessibilityRole).toBe('button');
  await ReactTestRenderer.act(() => {
    renderer.root
      .findAll(node => typeof node.props.onLayout === 'function')[0]
      .props.onLayout({
        nativeEvent: { layout: { width: 390, height: 844 } },
      });
  });
  const roomPet = () => renderer.root.findByType('RoomPet' as never);
  expect(roomPet().props.returnSequence).toBe(0);
  await ReactTestRenderer.act(() => roomPet().props.onPress());
  expect(renderer!.root.findByType(PetStatsDialog).props.visible).toBe(true);
  await ReactTestRenderer.act(() => renderer.root.findByType(PetStatsDialog).props.onClose());
  expect(renderer!.root.findByType(PetStatsDialog).props.visible).toBe(false);
  let blurRoom: () => void;
  await ReactTestRenderer.act(() => {
    blurRoom = mockFocusRoom();
  });
  await ReactTestRenderer.act(() => button.props.onPress());
  expect(navigate).not.toHaveBeenCalled();
  expect(roomPet().props.leaving).toBe(true);
  await ReactTestRenderer.act(() => roomPet().props.onHidden());
  expect(navigate).toHaveBeenCalledWith('ArWalk', { petName: expect.any(String) });
  await ReactTestRenderer.act(() => blurRoom());
  expect(renderer!.root.findAllByType('RoomPet' as never)).toHaveLength(0);
  await ReactTestRenderer.act(() => {
    mockFocusRoom();
  });
  expect(roomPet().props.returnSequence).toBe(1);
  // Refocusing without another AR visit must not request a second entrance.
  await ReactTestRenderer.act(() => {
    mockFocusRoom();
  });
  expect(roomPet().props.returnSequence).toBe(1);
  expect(roomPet().props.entryOffset.x).toBeGreaterThan(0);
  expect(roomPet().props.entryOffset.y).toBeGreaterThan(0);
  await ReactTestRenderer.act(() => {
    renderer.root.findAllByProps({ accessibilityLabel: '뒤로가기' })[0].props.onPress();
  });
  expect(goBack).not.toHaveBeenCalled();
  await ReactTestRenderer.act(() => roomPet().props.onHidden());
  expect(goBack).toHaveBeenCalledTimes(1);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('quitting clears the session and replaces the main screen with fresh selection', async () => {
  await AsyncStorage.clear();
  await startMockAdoption('dog-1', 35000);
  const navigate = jest.fn();
  const props = {
    navigation: { navigate },
    route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId: 'dog-1' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  await ReactTestRenderer.act(async () => {
    await renderer.root
      .findAllByProps({ accessibilityLabel: '모의입양 그만두기' })[0]
      .props.onPress();
  });
  expect(navigate).toHaveBeenCalledWith('Home', { tab: 'mockAdoption' });
  expect(abandonSimulation).not.toHaveBeenCalled();
  expect(invalidateSimulationReports).not.toHaveBeenCalled();
  await expect(getActiveMockAdoption()).resolves.toBeNull();
  await expect(startMockAdoption('cat-2', 50000)).resolves.toMatchObject({
    petId: 'cat-2',
    weeklyBudget: 50000,
  });
  await ReactTestRenderer.act(() => renderer.unmount());
  await AsyncStorage.clear();
});

test('quitting abandons the server session before leaving the activity', async () => {
  await AsyncStorage.clear();
  jest.mocked(abandonSimulation).mockResolvedValue({
    sessionSeq: 3001,
    status: 'ABANDONED',
    endedDttm: '2026-11-09T18:00:00',
    report: { summary: null },
  });
  await startMockAdoption('dog-1', 35000, '초코', 3001);
  const navigate = jest.fn();
  const props = {
    navigation: { navigate },
    route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId: 'dog-1' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  await ReactTestRenderer.act(async () => {
    await renderer.root
      .findAllByProps({ accessibilityLabel: '모의입양 그만두기' })[0]
      .props.onPress();
  });
  expect(abandonSimulation).toHaveBeenCalledWith(3001);
  expect(invalidateSimulationReports).toHaveBeenCalledTimes(1);
  expect(navigate).toHaveBeenCalledWith('Home', { tab: 'mockAdoption' });
  await expect(getActiveMockAdoption()).resolves.toBeNull();
  await ReactTestRenderer.act(() => renderer.unmount());
  await AsyncStorage.clear();
});

test('quitting leaves when the server session is already closed', async () => {
  await AsyncStorage.clear();
  jest
    .mocked(abandonSimulation)
    .mockRejectedValue(
      new ApiClientError({ code: 'SIMULATION_NOT_IN_PROGRESS', message: 'server' }),
    );
  await startMockAdoption('dog-1', 35000, '초코', 3001);
  const navigate = jest.fn();
  const props = {
    navigation: { navigate },
    route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId: 'dog-1' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  await ReactTestRenderer.act(async () => {
    await renderer.root
      .findAllByProps({ accessibilityLabel: '모의입양 그만두기' })[0]
      .props.onPress();
  });
  expect(navigate).toHaveBeenCalledWith('Home', { tab: 'mockAdoption' });
  expect(invalidateSimulationReports).toHaveBeenCalledTimes(1);
  await expect(getActiveMockAdoption()).resolves.toBeNull();
  await ReactTestRenderer.act(() => renderer.unmount());
  await AsyncStorage.clear();
});

test('quitting keeps the session when abandon fails', async () => {
  await AsyncStorage.clear();
  jest.mocked(abandonSimulation).mockRejectedValue(new Error('offline'));
  const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
  await startMockAdoption('dog-1', 35000, '초코', 3001);
  const navigate = jest.fn();
  const props = {
    navigation: { navigate },
    route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId: 'dog-1' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  await ReactTestRenderer.act(async () => {
    await renderer.root
      .findAllByProps({ accessibilityLabel: '모의입양 그만두기' })[0]
      .props.onPress();
  });
  expect(navigate).not.toHaveBeenCalled();
  expect(invalidateSimulationReports).not.toHaveBeenCalled();
  expect(alert).toHaveBeenCalledWith(
    '모의입양',
    '진행 상태를 초기화하지 못했어요. 다시 시도해주세요.',
  );
  await expect(getActiveMockAdoption()).resolves.toMatchObject({ sessionSeq: 3001 });
  alert.mockRestore();
  await ReactTestRenderer.act(() => renderer.unmount());
  await AsyncStorage.clear();
});

test.each([
  ['dog-1', '빈 배변 패드', '빈 고양이 화장실'],
  ['cat-2', '빈 고양이 화장실', '빈 배변 패드'],
])('shows the empty toilet for %s', async (petId, expected, absent) => {
  await AsyncStorage.clear();
  const session = await startMockAdoption(petId, 35000);
  const species = petId.startsWith('dog') ? 'dog' : 'cat';
  useShopStore.getState().open(session);
  useShopStore.getState().setQuantity(species === 'dog' ? 'pads' : 'litterBox', 1, species);
  useShopStore.getState().checkout(species);
  const props = {
    navigation: {},
    route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  expect(renderer!.root.findAllByProps({ accessibilityLabel: expected }).length).toBeGreaterThan(0);
  expect(renderer!.root.findAllByProps({ accessibilityLabel: absent })).toHaveLength(0);
  await ReactTestRenderer.act(() => {
    renderer.root
      .findAll(node => typeof node.props.onLayout === 'function')[0]
      .props.onLayout({
        nativeEvent: { layout: { width: 390, height: 844 } },
      });
  });
  expect(renderer!.root.findByType('RoomPet' as never).props.petId).toBe(petId);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test.each(['food', 'water'] as const)(
  'serves %s and refuses the fifth consecutive request',
  async kind => {
    await AsyncStorage.clear();
    const session = await startMockAdoption('dog-1', 35000);
    useShopStore.getState().open(session);
    useShopStore.getState().setQuantity('food', 1, 'dog');
    useShopStore.getState().setQuantity('bowl', 1, 'dog');
    useShopStore.getState().setQuantity('waterBowl', 1, 'dog');
    useShopStore.getState().checkout('dog');
    jest.useFakeTimers();
    const props = {
      navigation: {},
      route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId: 'dog-1' } },
    } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
    let renderer!: ReactTestRenderer.ReactTestRenderer;
    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />, {
        createNodeMock: () => ({
          measureInWindow: (callback: (...args: number[]) => void) => callback(20, 500, 80, 80),
        }),
      });
    });
    await ReactTestRenderer.act(() =>
      renderer.root
        .findAll(node => typeof node.props.onLayout === 'function')[0]
        .props.onLayout({ nativeEvent: { layout: { width: 390, height: 844 } } }),
    );
    const bowl = () =>
      renderer.root.findAllByProps({
        accessibilityLabel: kind === 'food' ? '사료 그릇' : '물그릇',
      })[0];
    const pet = () => renderer.root.findByType('RoomPet' as never);
    expect(bowl().props.accessibilityValue.text).toBe('빈 그릇');
    for (let count = 0; count < 4; count++) {
      await ReactTestRenderer.act(() => bowl().props.onPress());
      expect(bowl().props.accessibilityValue.text).toBe('가득 찬 그릇');
      await ReactTestRenderer.act(() => pet().props.onConsumed());
      await ReactTestRenderer.act(() => pet().props.onCareComplete());
    }
    await ReactTestRenderer.act(() => bowl().props.onPress());
    expect(pet().props.care).toBeNull();
    expect(
      renderer.root.findAllByProps({
        children: kind === 'food' ? '지금은 배불러요' : '지금은 목마르지 않아요',
      }).length,
    ).toBeGreaterThan(0);
    await ReactTestRenderer.act(() => renderer.unmount());
    jest.useRealTimers();
  },
);

test('records water on the server and keeps feed local until an item id exists', async () => {
  await AsyncStorage.clear();
  jest.mocked(performCareAction).mockReset();
  jest.mocked(performCareAction).mockResolvedValue({} as never);
  const session = await startMockAdoption('dog-1', 35000, '초코', 3001);
  useShopStore.getState().open(session);
  useShopStore.getState().setQuantity('food', 1, 'dog');
  useShopStore.getState().setQuantity('bowl', 1, 'dog');
  useShopStore.getState().setQuantity('waterBowl', 1, 'dog');
  useShopStore.getState().checkout('dog');
  const props = {
    navigation: {},
    route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId: 'dog-1' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />, {
      createNodeMock: () => ({
        measureInWindow: (callback: (...args: number[]) => void) => callback(20, 500, 80, 80),
      }),
    });
  });
  await ReactTestRenderer.act(() =>
    renderer.root
      .findAll(node => typeof node.props.onLayout === 'function')[0]
      .props.onLayout({ nativeEvent: { layout: { width: 390, height: 844 } } }),
  );
  const pet = () => renderer.root.findByType('RoomPet' as never);
  await ReactTestRenderer.act(() =>
    renderer.root.findAllByProps({ accessibilityLabel: '물그릇' })[0].props.onPress(),
  );
  expect(performCareAction).toHaveBeenCalledWith(3001, { actionCode: 'WATER' });
  await ReactTestRenderer.act(() => pet().props.onConsumed());
  await ReactTestRenderer.act(() => pet().props.onCareComplete());
  await ReactTestRenderer.act(() =>
    renderer.root.findAllByProps({ accessibilityLabel: '사료 그릇' })[0].props.onPress(),
  );
  expect(performCareAction).toHaveBeenCalledTimes(1);
  await ReactTestRenderer.act(() => renderer.unmount());
  await AsyncStorage.clear();
});

test('shows purchased room items only and hides depleted consumable controls', async () => {
  await AsyncStorage.clear();
  const session = await startMockAdoption('dog-1', 50000);
  useShopStore.getState().open(session);
  const props = {
    navigation: {},
    route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId: 'dog-1' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  await ReactTestRenderer.act(() =>
    renderer.root
      .findAll(node => typeof node.props.onLayout === 'function')[0]
      .props.onLayout({ nativeEvent: { layout: { width: 390, height: 844 } } }),
  );
  const count = (accessibilityLabel: string) =>
    renderer.root.findAllByProps({ accessibilityLabel }).length;
  expect(count('사료 그릇')).toBe(0);
  expect(count('물그릇')).toBe(0);
  expect(count('잡아서 위로 던져 간식 주기')).toBe(0);
  expect(count('잡아서 위로 던져 공놀이하기')).toBe(0);
  await ReactTestRenderer.act(() => {
    for (const id of ['bowl', 'waterBowl', 'food', 'treat', 'toy']) {
      useShopStore.getState().setQuantity(id, 1, 'dog');
    }
  });
  expect(count('사료 그릇')).toBe(0);
  await ReactTestRenderer.act(() => {
    useShopStore.getState().checkout('dog');
  });
  expect(count('사료 그릇')).toBeGreaterThan(0);
  expect(count('물그릇')).toBeGreaterThan(0);
  expect(count('잡아서 위로 던져 간식 주기')).toBeGreaterThan(0);
  expect(count('잡아서 위로 던져 공놀이하기')).toBeGreaterThan(0);
  await ReactTestRenderer.act(() => {
    useShopStore.getState().consume({ treat: 5 });
  });
  expect(count('잡아서 위로 던져 간식 주기')).toBe(0);
  expect(count('잡아서 위로 던져 공놀이하기')).toBeGreaterThan(0);
  expect(count('사료 그릇')).toBeGreaterThan(0);
  await ReactTestRenderer.act(() => renderer.unmount());
  await AsyncStorage.clear();
});

test.each([
  ['dog-1', '가득 찬 배변 패드', '빈 배변 패드'],
  ['cat-2', '가득 찬 고양이 화장실', '빈 고양이 화장실'],
])('cleans and persists the toilet for %s', async (petId, fullLabel, emptyLabel) => {
  await AsyncStorage.clear();
  const session = await startMockAdoption(petId, 35000);
  const species = petId.startsWith('dog') ? 'dog' : 'cat';
  useShopStore.getState().open(session);
  useShopStore.getState().setQuantity('cleaner', 1, species);
  if (species === 'cat') {
    useShopStore.getState().setQuantity('litterBox', 1, species);
  }
  useShopStore.getState().setQuantity(species === 'dog' ? 'pads' : 'litter', 1, species);
  useShopStore.getState().checkout(species);
  await AsyncStorage.setItem(
    STORAGE_KEYS.MOCK_ADOPTION_TOILET,
    JSON.stringify({
      petId,
      startedAt: session.startedAt,
      cleanedAt: session.startedAt - 2000,
      events: [session.startedAt - 1000, session.startedAt + 3600000],
    }),
  );
  const props = {
    navigation: {},
    route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  await ReactTestRenderer.act(async () => {
    await renderer.root.findAllByProps({ accessibilityLabel: fullLabel })[0].props.onPress();
  });
  expect(renderer.root.findAllByProps({ accessibilityLabel: emptyLabel }).length).toBeGreaterThan(
    0,
  );
  expect(renderer.root.findAllByProps({ children: '깨끗해져서 좋아요!' }).length).toBeGreaterThan(
    0,
  );
  await ReactTestRenderer.act(() => renderer.unmount());
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  expect(renderer.root.findAllByProps({ accessibilityLabel: fullLabel })).toHaveLength(0);
  expect(renderer.root.findAllByProps({ accessibilityLabel: emptyLabel }).length).toBeGreaterThan(
    0,
  );
  await ReactTestRenderer.act(() => renderer.unmount());
  await AsyncStorage.clear();
});

test('bath fades out and back in for two seconds each while blocking input', async () => {
  await AsyncStorage.clear();
  const session = await startMockAdoption('dog-1', 35000);
  useShopStore.getState().open(session);
  useShopStore.getState().setQuantity('pads', 1, 'dog');
  useShopStore.getState().checkout('dog');
  const timing = jest.spyOn(Animated, 'timing').mockImplementation((_value, config) => ({
    start: callback => {
      setTimeout(() => callback?.({ finished: true }), config.duration ?? 0);
    },
    stop: jest.fn(),
    reset: jest.fn(),
  }));
  jest.useFakeTimers();
  const goBack = jest.fn();
  const props = {
    navigation: { goBack },
    route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId: 'dog-1' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  await ReactTestRenderer.act(() =>
    renderer.root
      .findAll(node => typeof node.props.onLayout === 'function')[0]
      .props.onLayout({ nativeEvent: { layout: { width: 390, height: 844 } } }),
  );
  const model = () => renderer.root.findByType('RoomPet' as never);
  const originalFrame = StyleSheet.flatten(model().props.style);
  await ReactTestRenderer.act(() =>
    renderer.root.findAllByProps({ accessibilityLabel: '목욕하기' })[0].props.onPress(),
  );
  expect(model().props.bathing).toBe(true);
  expect(StyleSheet.flatten(model().props.style)).toEqual({
    ...originalFrame,
    top: originalFrame.top + 30,
  });
  expect(
    renderer.root.findAllByProps({ importantForAccessibility: 'no-hide-descendants' }).length,
  ).toBeGreaterThan(0);
  expect(renderer.root.findAllByProps({ accessibilityLabel: '빈 배변 패드' })).toHaveLength(0);
  await ReactTestRenderer.act(() =>
    renderer.root.findAllByProps({ accessibilityLabel: '뒤로가기' })[0].props.onPress(),
  );
  expect(goBack).not.toHaveBeenCalled();
  await ReactTestRenderer.act(() => jest.advanceTimersByTime(2999));
  expect(model().props.bathing).toBe(true);
  await ReactTestRenderer.act(() => jest.advanceTimersByTime(1));
  expect(model().props.bathing).toBe(true);
  expect(timing).toHaveBeenCalledWith(
    expect.anything(),
    expect.objectContaining({ duration: 2000, toValue: 0 }),
  );
  await ReactTestRenderer.act(() => jest.advanceTimersByTime(1999));
  expect(model().props.bathing).toBe(true);
  await ReactTestRenderer.act(() => jest.advanceTimersByTime(1));
  expect(model().props.bathing).toBe(false);
  expect(StyleSheet.flatten(model().props.style)).toEqual(originalFrame);
  expect(timing).toHaveBeenLastCalledWith(
    expect.anything(),
    expect.objectContaining({ duration: 2000, toValue: 1 }),
  );
  expect(
    renderer.root.findAllByProps({ importantForAccessibility: 'no-hide-descendants' }).length,
  ).toBeGreaterThan(0);
  await ReactTestRenderer.act(() =>
    renderer.root.findAllByProps({ accessibilityLabel: '뒤로가기' })[0].props.onPress(),
  );
  expect(goBack).not.toHaveBeenCalled();
  await ReactTestRenderer.act(() => jest.advanceTimersByTime(1999));
  expect(
    renderer.root.findAllByProps({ importantForAccessibility: 'no-hide-descendants' }).length,
  ).toBeGreaterThan(0);
  await ReactTestRenderer.act(() => jest.advanceTimersByTime(1));
  expect(
    renderer.root.findAllByProps({ importantForAccessibility: 'no-hide-descendants' }),
  ).toHaveLength(0);
  expect(
    renderer.root.findAllByProps({ accessibilityLabel: '빈 배변 패드' }).length,
  ).toBeGreaterThan(0);
  await ReactTestRenderer.act(() => renderer.unmount());
  timing.mockRestore();
  jest.useRealTimers();
});
