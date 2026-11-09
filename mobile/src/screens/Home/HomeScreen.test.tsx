import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { FlatList } from 'react-native';
import ReactTestRenderer from 'react-test-renderer';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { startMockAdoption } from '../../services/storage/mockAdoptionStorage';

import type { RootStackParamList } from '../../app/navigation/types';
import { HomeScreen } from './HomeScreen';
import { MockAdoptionScreen } from '../MockAdoption/MockAdoptionScreen';
import { Button } from '../../components/common';

const mockNavigate = jest.fn();

beforeEach(async () => {
  mockNavigate.mockClear();
  await AsyncStorage.clear();
});

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
  expect(renderer!.root.findAllByProps({ accessibilityLabel: '기간 필터' })).toHaveLength(0);
  expect(renderer!.root.findAllByProps({ accessibilityRole: 'radio' })).toHaveLength(0);
  expect(mockNavigate).not.toHaveBeenCalled();
  expect(renderer!.root.findAllByProps({ children: '받은 레포트' }).length).toBeGreaterThan(0);
  await ReactTestRenderer.act(async () => {
    await renderer.root.findAllByType(Button)[0].props.onPress();
  });
  expect(mockNavigate).toHaveBeenCalledWith('MockAdoptionSelection');
  await ReactTestRenderer.act(() => {
    renderer.root.findAllByProps({ accessibilityLabel: '입양' })[0].props.onPress();
  });
  expect(
    renderer!.root.findAllByProps({ placeholder: '특징으로 검색해보세요' })[0].props.value,
  ).toBe('진도');
  await ReactTestRenderer.act(() => renderer.unmount());
}, 20000);

test('opens the mock adoption overview from a home tab request', async () => {
  const setParams = jest.fn();
  const props = {
    navigation: { navigate: mockNavigate, setParams },
    route: { key: 'home', name: 'Home', params: { tab: 'mockAdoption' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'Home'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<HomeScreen {...props} />);
    await Promise.resolve();
  });
  expect(renderer.root.findAllByProps({ children: '받은 레포트' }).length).toBeGreaterThan(0);
  expect(setParams).toHaveBeenCalledWith({ tab: undefined });
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('my page resumes an active adoption without asking for the budget again', async () => {
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
  await ReactTestRenderer.act(async () => {
    await renderer.root.findAllByProps({ accessibilityLabel: '모의입양' })[0].props.onPress();
    await Promise.resolve();
  });
  expect(mockNavigate).not.toHaveBeenCalled();
  expect(renderer!.root.findAllByType(Button)[0].props.children).toBe('초코 보러가기');
  await ReactTestRenderer.act(async () => {
    await renderer.root.findAllByType(Button)[0].props.onPress();
  });
  expect(mockNavigate).toHaveBeenCalledWith('MockAdoptionActivity', { petId: 'dog-3' });
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

  expect(renderer!.root.findAll(node => node.props.pointerEvents === 'none')).toHaveLength(0);
  expect(
    renderer!.root.findAllByProps({ placeholder: '특징으로 검색해보세요' }).length,
  ).toBeGreaterThan(0);
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
