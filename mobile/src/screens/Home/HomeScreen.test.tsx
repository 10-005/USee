import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { FlatList } from 'react-native';
import ReactTestRenderer, { type ReactTestInstance } from 'react-test-renderer';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  applyActiveSimulation,
  getActiveSimulation,
} from '../../services/simulation/activeSession';
import { startMockAdoption } from '../../services/storage/mockAdoptionStorage';

import type { RootStackParamList } from '../../app/navigation/types';
import { hasUnreadListNotification } from '../../services/notifications/notificationApi';
import { HomeScreen } from './HomeScreen';
import { MockAdoptionScreen } from '../MockAdoption/MockAdoptionScreen';
import { Button } from '../../components/common';

const mockNavigate = jest.fn();

beforeEach(async () => {
  mockNavigate.mockClear();
  jest.mocked(hasUnreadListNotification).mockReset();
  jest.mocked(hasUnreadListNotification).mockResolvedValue(false);
  jest.mocked(getActiveSimulation).mockReset();
  jest.mocked(getActiveSimulation).mockRejectedValue(new Error('offline'));
  await AsyncStorage.clear();
});

jest.mock('../../services/simulation/activeSession', () => {
  const actual = jest.requireActual('../../services/simulation/activeSession');
  return {
    ...actual,
    getActiveSimulation: jest.fn().mockRejectedValue(new Error('offline')),
  };
});

jest.mock('../../services/notifications/notificationApi', () => ({
  hasUnreadListNotification: jest.fn().mockResolvedValue(false),
}));

jest.mock('../../services/simulation/reports', () => ({
  loadSimulationReports: jest.fn().mockResolvedValue({ reports: [], freshSessionSeqs: [] }),
  clearSimulationReports: jest.fn(),
  invalidateSimulationReports: jest.fn(),
  getSimulationReportErrorMessage: () => '리포트를 불러오지 못했어요. 다시 시도해주세요.',
}));

jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useNavigation: () => ({ navigate: mockNavigate }),
  useFocusEffect: (callback: () => void) => {
    require('react').useEffect(callback, [callback]);
  },
}));

jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);

test.each(['입양', '찾아요', '채팅', '마이페이지'])(
  '%s main tab provides a shortcut to the active pet',
  async tab => {
    await startMockAdoption('dog-5', 35000, '코코');
    const props = {
      navigation: { navigate: mockNavigate },
      route: { key: 'home', name: 'Home' },
    } as unknown as NativeStackScreenProps<RootStackParamList, 'Home'>;
    let renderer!: ReactTestRenderer.ReactTestRenderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(<HomeScreen {...props} />);
    });
    await ReactTestRenderer.act(async () => {
      renderer.root.findAllByProps({ accessibilityLabel: tab })[0].props.onPress();
    });
    await ReactTestRenderer.act(async () => {
      await renderer.root
        .findAllByProps({ accessibilityLabel: '코코 모의입양 바로가기' })[0]
        .props.onPress();
    });
    expect(mockNavigate).toHaveBeenCalledWith('MockAdoptionActivity', { petId: 'dog-5' });
    await ReactTestRenderer.act(() => renderer.unmount());
  },
);

test('shows a red dot when an unread notification exists', async () => {
  jest.mocked(hasUnreadListNotification).mockResolvedValue(true);
  const props = {
    navigation: { navigate: mockNavigate },
    route: { key: 'home', name: 'Home' },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'Home'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<HomeScreen {...props} />);
    await Promise.resolve();
  });

  expect(
    renderer.root.findAllByProps({ accessibilityLabel: '읽지 않은 알림' }).length,
  ).toBeGreaterThan(0);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('hides the shortcut when the server has no active adoption', async () => {
  await startMockAdoption('dog-5', 35000, '코코');
  jest.mocked(getActiveSimulation).mockResolvedValueOnce(null);
  const props = {
    navigation: { navigate: mockNavigate },
    route: { key: 'home', name: 'Home' },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'Home'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<HomeScreen {...props} />);
    await Promise.resolve();
  });
  expect(
    renderer.root.findAllByProps({ accessibilityLabel: '코코 모의입양 바로가기' }),
  ).toHaveLength(0);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('shows the shortcut from the server session on launch', async () => {
  jest.mocked(getActiveSimulation).mockResolvedValueOnce({
    sessionSeq: 3001,
    userSeq: 17,
    animalName: '초코',
    breed: {
      breedSeq: '000018',
      breedName: '비숑 프리제',
      speciesType: 'DOG',
      representativeImageUrl: null,
    },
    speciesType: 'DOG',
    personality: 'FRIENDLY',
    status: 'IN_PROGRESS',
    expectedCost: 150000,
    balance: 118000,
    startedDttm: '2026-11-01T14:00:00+09:00',
    endsDttm: '2026-11-08T14:00:00+09:00',
    endedDttm: null,
    stats: {
      satiety: 64,
      hydration: 58,
      cleanliness: 77,
      bond: 31,
      calmness: 46,
      health: 90,
      energy: 55,
      calculatedDttm: '2026-11-01T17:00:00+09:00',
    },
    lastActionDttm: null,
    equipment: { foodBowl: false, waterBowl: false, litterBox: false, toy: false, leash: false },
    pendingEvent: null,
    activeWalkSeq: null,
  });
  const props = {
    navigation: { navigate: mockNavigate },
    route: { key: 'home', name: 'Home' },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'Home'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  try {
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(<HomeScreen {...props} />);
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(
      renderer.root.findAllByProps({ accessibilityLabel: '초코 모의입양 바로가기' }).length,
    ).toBeGreaterThan(0);
  } finally {
    await ReactTestRenderer.act(() => renderer?.unmount());
    await applyActiveSimulation(null);
  }
});

test('mock adoption selects one pet across categories and preserves adoption filters', async () => {
  const props = {
    navigation: { navigate: mockNavigate },
    route: { key: 'home', name: 'Home' },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'Home'>;
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<HomeScreen {...props} />);
  });
  expect(renderer!.root.findAllByProps({ accessibilityLabel: '기간 필터' }).length).toBeGreaterThan(
    0,
  );
  await ReactTestRenderer.act(() => {
    renderer.root
      .findAllByProps({ placeholder: '특징으로 검색해보세요' })[0]
      .props.onChangeText('진도');
  });
  await ReactTestRenderer.act(() => {
    renderer.root.findAllByProps({ accessibilityLabel: '마이페이지' })[0].props.onPress();
  });
  await ReactTestRenderer.act(async () => {
    await renderer.root.findAllByProps({ accessibilityLabel: '모의입양' })[0].props.onPress();
    await Promise.resolve();
  });
  expect(mockNavigate).toHaveBeenCalledWith('MockAdoptionOverview');
  expect(renderer!.root.findAllByProps({ children: '받은 레포트' })).toHaveLength(0);
  await ReactTestRenderer.act(() => {
    renderer.root.findAllByProps({ accessibilityLabel: '입양' })[0].props.onPress();
  });
  expect(
    renderer!.root.findAllByProps({ placeholder: '특징으로 검색해보세요' })[0].props.value,
  ).toBe('진도');
  await ReactTestRenderer.act(() => renderer.unmount());
}, 20000);

test('my page opens mock adoption as its own screen', async () => {
  await startMockAdoption('dog-3', 35000, '초코');
  const props = {
    navigation: { navigate: mockNavigate },
    route: { key: 'home', name: 'Home' },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'Home'>;
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<HomeScreen {...props} />);
  });
  await ReactTestRenderer.act(() => {
    renderer.root.findAllByProps({ accessibilityLabel: '마이페이지' })[0].props.onPress();
  });
  await ReactTestRenderer.act(() => {
    renderer.root.findAllByProps({ accessibilityLabel: '모의입양' })[0].props.onPress();
  });
  expect(mockNavigate).toHaveBeenCalledWith('MockAdoptionOverview');
  expect(renderer!.root.findAllByProps({ children: '마이페이지' }).length).toBeGreaterThan(0);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('returning to adoption resets the hidden search and filter header', async () => {
  const props = {
    navigation: { navigate: mockNavigate },
    route: { key: 'home', name: 'Home' },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'Home'>;
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<HomeScreen {...props} />);
  });

  await ReactTestRenderer.act(() => {
    renderer.root.findByType(FlatList).props.onScroll({
      nativeEvent: { contentOffset: { y: 180 } },
    });
  });
  expect(renderer!.root.findAll(node => node.props.pointerEvents === 'none')).not.toHaveLength(0);

  await ReactTestRenderer.act(() => {
    renderer.root.findAllByProps({ accessibilityLabel: '찾아요' })[0].props.onPress();
  });
  await ReactTestRenderer.act(() => {
    renderer.root.findAllByProps({ accessibilityLabel: '입양' })[0].props.onPress();
  });

  const searchInput = renderer!.root.findByProps({ placeholder: '특징으로 검색해보세요' });
  let searchHeader: ReactTestInstance | null = searchInput;
  while (searchHeader && searchHeader.props.pointerEvents === undefined) {
    searchHeader = searchHeader.parent;
  }
  expect(searchHeader?.props.pointerEvents).toBe('auto');
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('selection keeps one pet across categories and opens budget', async () => {
  mockNavigate.mockClear();
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionScreen />);
  });
  const startButton = () => renderer.root.findByType(Button);
  const options = () =>
    renderer.root
      .findAllByProps({ accessibilityRole: 'radio' })
      .filter(node => node.parent?.props.accessibilityRole !== 'radio');
  expect(options().map(node => node.props.accessibilityLabel)).toEqual([
    '비숑 프리제',
    '도베르만',
    '푸들',
    '골든 리트리버',
    '시바',
    '시츄',
  ]);
  expect(startButton().props.disabled).toBe(true);
  await ReactTestRenderer.act(() => startButton().props.onPress());
  expect(mockNavigate).not.toHaveBeenCalled();
  await ReactTestRenderer.act(() => options()[0].props.onPress());
  expect(startButton().props.disabled).toBe(false);
  await ReactTestRenderer.act(() => {
    renderer.root
      .findAllByProps({ accessibilityRole: 'tab' })
      .filter(node => node.parent?.props.accessibilityRole !== 'tab')[1]
      .props.onPress();
  });
  expect(options().map(node => node.props.accessibilityLabel)).toEqual([
    '브리티시 쇼트헤어',
    '러시안 블루',
    '사바나캣',
    '페르시안',
    '스핑크스',
    '샴',
  ]);
  expect(options().every(node => !node.props.accessibilityState.checked)).toBe(true);
  await ReactTestRenderer.act(() => options()[1].props.onPress());
  expect(options().filter(node => node.props.accessibilityState.checked)).toHaveLength(1);
  await ReactTestRenderer.act(() => {
    renderer.root
      .findAllByProps({ accessibilityRole: 'tab' })
      .filter(node => node.parent?.props.accessibilityRole !== 'tab')[0]
      .props.onPress();
  });
  expect(options().every(node => !node.props.accessibilityState.checked)).toBe(true);
  await ReactTestRenderer.act(() => startButton().props.onPress());
  expect(mockNavigate).toHaveBeenCalledWith('MockAdoptionName', { petId: 'cat-2' });
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('selection starts empty again after leaving the creation flow', async () => {
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionScreen />);
  });
  await ReactTestRenderer.act(() => {
    renderer.root.findAllByProps({ accessibilityRole: 'radio' })[0].props.onPress();
  });
  expect(renderer.root.findByType(Button).props.disabled).toBe(false);
  await ReactTestRenderer.act(() => renderer.unmount());
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionScreen />);
  });
  expect(renderer.root.findByType(Button).props.disabled).toBe(true);
  expect(
    renderer.root.findAllByProps({ children: '함께할 반려동물 한 마리를 선택해주세요.' }).length,
  ).toBeGreaterThan(0);
  await ReactTestRenderer.act(() => renderer.unmount());
});
