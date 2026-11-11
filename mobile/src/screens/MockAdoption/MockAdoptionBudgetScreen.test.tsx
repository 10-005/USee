import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import AsyncStorage from '@react-native-async-storage/async-storage';
import ReactTestRenderer from 'react-test-renderer';
import { Keyboard, ScrollView, TextInput as NativeTextInput } from 'react-native';
import type { RootStackParamList } from '../../app/navigation/types';
import { Button, TextInput } from '../../components/common';
import { ApiClientError } from '../../services/api';
import { startSimulationSession } from '../../services/simulation/session';
import { ensureSessionShopCatalog } from '../../services/simulation/shopItems';
import { resolveSimulationBreedSeq } from '../../services/simulation/breeds';
import { getActiveMockAdoption } from '../../services/storage/mockAdoptionStorage';
import { getBudgetError } from './budget';
import { MockAdoptionBudgetScreen } from './MockAdoptionBudgetScreen';

jest.mock('../../services/simulation/session', () => {
  const actual = jest.requireActual('../../services/simulation/session');
  return {
    ...actual,
    startSimulationSession: jest.fn(),
  };
});

const startSession = startSimulationSession as jest.MockedFunction<typeof startSimulationSession>;
const ensureCatalog = ensureSessionShopCatalog as jest.MockedFunction<
  typeof ensureSessionShopCatalog
>;

jest.mock('../../services/simulation/breeds', () => ({
  resolveSimulationBreedSeq: jest.fn().mockResolvedValue(501),
}));

beforeEach(() => {
  jest.mocked(resolveSimulationBreedSeq).mockReset().mockResolvedValue(501);
});

jest.mock('../../services/simulation/shopItems', () => {
  const actual = jest.requireActual('../../services/simulation/shopItems');
  return {
    ...actual,
    ensureSessionShopCatalog: jest.fn().mockResolvedValue(undefined),
  };
});

jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);

test.each(['', '9999', '100000001', '1e5', '-10000', '10000.5', '10,000', '한글'])(
  'rejects invalid budget %s',
  value => {
    expect(getBudgetError(value)).toBeDefined();
  },
);
test.each(['10000', '100000000', '035000'])('accepts valid budget %s', value => {
  expect(getBudgetError(value)).toBeUndefined();
});

test('accepts digits only and persists only when starting, with retry on save failure', async () => {
  await AsyncStorage.clear();
  startSession.mockReset();
  ensureCatalog.mockClear();
  startSession.mockResolvedValue({ sessionSeq: 3001 });
  const reset = jest.fn();
  const props = {
    navigation: { reset, goBack: jest.fn() },
    route: {
      key: 'budget',
      name: 'MockAdoptionBudget',
      params: { petId: 'dog-1', petName: '초코' },
    },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionBudget'>;
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionBudgetScreen {...props} />);
  });
  const input = () => renderer.root.findByType(TextInput);
  const button = () => renderer.root.findByType(Button);
  expect(button().props.disabled).toBe(true);
  expect(input().props.placeholder).toBe('0');
  for (const [label, expected] of [
    ['10,000원 추가', '10,000'],
    ['50,000원 추가', '60,000'],
    ['100,000원 추가', '160,000'],
    ['1,000,000원 추가', '1,160,000'],
  ]) {
    await ReactTestRenderer.act(() => {
      renderer.root.findAllByProps({ accessibilityLabel: label })[0].props.onPress();
    });
    expect(input().props.value).toBe(expected);
  }
  await ReactTestRenderer.act(() => input().props.onChangeText('100000000'));
  await ReactTestRenderer.act(() => {
    renderer.root.findAllByProps({ accessibilityLabel: '10,000원 추가' })[0].props.onPress();
  });
  expect(input().props.value).toBe('100,010,000');
  expect(button().props.disabled).toBe(true);
  await ReactTestRenderer.act(() => input().props.onChangeText('35,000'));
  expect(button().props.disabled).toBe(false);
  await ReactTestRenderer.act(() => input().props.onChangeText('35a000'));
  expect(input().props.value).toBe('35,000');
  await expect(getActiveMockAdoption()).resolves.toBeNull();
  expect(startSession).not.toHaveBeenCalled();
  const save = jest.spyOn(AsyncStorage, 'setItem').mockRejectedValueOnce(new Error('disk full'));
  await ReactTestRenderer.act(async () => {
    await button().props.onPress();
  });
  expect(startSession).toHaveBeenCalledTimes(1);
  expect(startSession).toHaveBeenCalledWith({
    breedSeq: 501,
    animalName: '초코',
    expectedCost: 35000,
  });
  expect(reset).not.toHaveBeenCalled();
  expect(button().props.loading).toBe(false);
  expect(
    renderer!.root.findAllByProps({ accessibilityLiveRegion: 'polite' }).length,
  ).toBeGreaterThan(0);
  await ReactTestRenderer.act(async () => {
    await button().props.onPress();
  });
  expect(startSession).toHaveBeenCalledTimes(1);
  expect(reset).toHaveBeenCalledWith({
    index: 1,
    routes: [
      { name: 'Home', key: 'budget-home' },
      { name: 'MockAdoptionActivity', params: { petId: 'dog-1' } },
    ],
  });
  await expect(getActiveMockAdoption()).resolves.toMatchObject({
    petId: 'dog-1',
    weeklyBudget: 35000,
    petName: '초코',
    sessionSeq: 3001,
  });
  expect(ensureCatalog).toHaveBeenCalledTimes(1);
  expect(ensureCatalog).toHaveBeenCalledWith(expect.stringMatching(/^dog-1:/), 'DOG');
  save.mockRestore();
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('does not reuse a server response after leaving the budget screen', async () => {
  await AsyncStorage.clear();
  startSession.mockReset();
  startSession.mockResolvedValue({ sessionSeq: 3001 });
  const props = {
    navigation: { reset: jest.fn(), goBack: jest.fn() },
    route: {
      key: 'budget',
      name: 'MockAdoptionBudget',
      params: { petId: 'dog-1', petName: '초코' },
    },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionBudget'>;
  const save = jest.spyOn(AsyncStorage, 'setItem').mockRejectedValue(new Error('disk full'));
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionBudgetScreen {...props} />);
  });
  await ReactTestRenderer.act(() => {
    renderer.root.findByType(TextInput).props.onChangeText('35000');
  });
  await ReactTestRenderer.act(async () => {
    await renderer.root.findByType(Button).props.onPress();
  });
  await ReactTestRenderer.act(() => renderer.unmount());
  save.mockRestore();
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionBudgetScreen {...props} />);
  });
  await ReactTestRenderer.act(() => {
    renderer.root.findByType(TextInput).props.onChangeText('35000');
  });
  await ReactTestRenderer.act(async () => {
    await renderer.root.findByType(Button).props.onPress();
  });
  expect(startSession).toHaveBeenCalledTimes(2);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('stays on the budget screen when the shop list fails and retries it', async () => {
  await AsyncStorage.clear();
  startSession.mockReset();
  ensureCatalog.mockClear();
  startSession.mockResolvedValue({ sessionSeq: 3001 });
  ensureCatalog.mockRejectedValueOnce(
    new ApiClientError({ code: 'UNKNOWN_ERROR', message: 'down' }),
  );
  const reset = jest.fn();
  const props = {
    navigation: { reset, goBack: jest.fn() },
    route: {
      key: 'budget',
      name: 'MockAdoptionBudget',
      params: { petId: 'cat-2', petName: '나비' },
    },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionBudget'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionBudgetScreen {...props} />);
  });
  await ReactTestRenderer.act(() => {
    renderer.root.findByType(TextInput).props.onChangeText('20000');
  });
  await ReactTestRenderer.act(async () => {
    await renderer.root.findByType(Button).props.onPress();
  });
  expect(reset).not.toHaveBeenCalled();
  expect(ensureCatalog).toHaveBeenCalledWith(expect.stringMatching(/^cat-2:/), 'CAT');
  expect(
    renderer.root.findAllByProps({ children: '상점 물품을 불러오지 못했어요. 다시 시도해주세요.' })
      .length,
  ).toBeGreaterThan(0);
  await ReactTestRenderer.act(async () => {
    await renderer.root.findByType(Button).props.onPress();
  });
  expect(startSession).toHaveBeenCalledTimes(1);
  expect(ensureCatalog).toHaveBeenCalledTimes(2);
  expect(reset).toHaveBeenCalled();
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('does not open the activity when the server already has a session', async () => {
  await AsyncStorage.clear();
  startSession.mockReset();
  startSession.mockRejectedValue(
    new ApiClientError({
      code: 'SIMULATION_ACTIVE_ALREADY_EXISTS',
      message: 'exists',
    }),
  );
  const reset = jest.fn();
  const props = {
    navigation: { reset, goBack: jest.fn() },
    route: {
      key: 'budget',
      name: 'MockAdoptionBudget',
      params: { petId: 'cat-4', petName: '나비' },
    },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionBudget'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionBudgetScreen {...props} />);
  });
  await ReactTestRenderer.act(() => {
    renderer.root.findByType(TextInput).props.onChangeText('20000');
  });
  await ReactTestRenderer.act(async () => {
    await renderer.root.findByType(Button).props.onPress();
  });
  expect(startSession).toHaveBeenCalledWith({
    breedSeq: 501,
    animalName: '나비',
    expectedCost: 20000,
  });
  expect(reset).not.toHaveBeenCalled();
  await expect(getActiveMockAdoption()).resolves.toBeNull();
  expect(
    renderer.root.findAllByProps({ children: '이미 진행 중인 모의입양이 있어요.' }).length,
  ).toBeGreaterThan(0);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('sends the resolved numeric shiba id and blocks creation if lookup fails', async () => {
  await AsyncStorage.clear();
  startSession.mockReset().mockResolvedValue({ sessionSeq: 3001 });
  const reset = jest.fn();
  const props = {
    navigation: { reset, goBack: jest.fn() },
    route: {
      key: 'budget',
      name: 'MockAdoptionBudget',
      params: { petId: 'dog-5', petName: '초코' },
    },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionBudget'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  try {
    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(<MockAdoptionBudgetScreen {...props} />);
    });
    await ReactTestRenderer.act(() =>
      renderer.root.findByType(TextInput).props.onChangeText('35000'),
    );
    jest.mocked(resolveSimulationBreedSeq).mockRejectedValueOnce(new Error('offline'));
    await ReactTestRenderer.act(async () => {
      await renderer.root.findByType(Button).props.onPress();
    });
    expect(startSession).not.toHaveBeenCalled();
    expect(reset).not.toHaveBeenCalled();
    jest.mocked(resolveSimulationBreedSeq).mockResolvedValueOnce(812);
    await ReactTestRenderer.act(async () => {
      await renderer.root.findByType(Button).props.onPress();
    });
    expect(resolveSimulationBreedSeq).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'dog-5', breedCode: '000100' }),
    );
    expect(startSession).toHaveBeenCalledWith({
      breedSeq: 812,
      animalName: '초코',
      expectedCost: 35000,
    });
  } finally {
    await ReactTestRenderer.act(() => renderer?.unmount());
  }
});

test('dismisses the keyboard and clears focus when the surrounding area is pressed', async () => {
  const dismiss = jest.spyOn(Keyboard, 'dismiss').mockImplementation(() => undefined);
  const focusedInput = {};
  const blurTextInput = jest
    .spyOn(NativeTextInput.State, 'blurTextInput')
    .mockImplementation(() => undefined);
  jest.spyOn(NativeTextInput.State, 'currentlyFocusedInput').mockReturnValue(focusedInput as never);
  const props = {
    navigation: { reset: jest.fn(), goBack: jest.fn() },
    route: {
      key: 'budget',
      name: 'MockAdoptionBudget',
      params: { petId: 'dog-1', petName: '초코' },
    },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionBudget'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionBudgetScreen {...props} />);
  });
  let outside = renderer.root.findByProps({ children: '예산 설정' }).parent;
  while (outside && typeof outside.props.onPress !== 'function') {
    outside = outside.parent;
  }
  await ReactTestRenderer.act(() => outside?.props.onPress());
  expect(blurTextInput).toHaveBeenCalledWith(focusedInput);
  expect(dismiss).toHaveBeenCalled();
  blurTextInput.mockClear();
  dismiss.mockClear();
  await ReactTestRenderer.act(() => renderer.root.findByType(ScrollView).props.onScrollBeginDrag());
  expect(blurTextInput).toHaveBeenCalledWith(focusedInput);
  expect(dismiss).toHaveBeenCalled();
  jest.restoreAllMocks();
  await ReactTestRenderer.act(() => renderer.unmount());
});
