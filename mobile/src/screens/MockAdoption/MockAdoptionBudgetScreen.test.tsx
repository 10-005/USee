import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import AsyncStorage from '@react-native-async-storage/async-storage';
import ReactTestRenderer from 'react-test-renderer';
import type { RootStackParamList } from '../../app/navigation/types';
import { Button, TextInput } from '../../components/common';
import { getActiveMockAdoption } from '../../services/storage/mockAdoptionStorage';
import { getBudgetError } from './budget';
import { MockAdoptionBudgetScreen } from './MockAdoptionBudgetScreen';

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
  const replace = jest.fn();
  const props = {
    navigation: { replace, goBack: jest.fn() },
    route: { key: 'budget', name: 'MockAdoptionBudget', params: { petId: 'dog-1' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionBudget'>;
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionBudgetScreen {...props} />);
  });
  const input = () => renderer.root.findByType(TextInput);
  const button = () => renderer.root.findByType(Button);
  expect(button().props.disabled).toBe(true);
  await ReactTestRenderer.act(() => input().props.onChangeText('35000'));
  expect(button().props.disabled).toBe(false);
  await ReactTestRenderer.act(() => input().props.onChangeText('35a000'));
  expect(input().props.value).toBe('35000');
  await expect(getActiveMockAdoption()).resolves.toBeNull();
  const save = jest.spyOn(AsyncStorage, 'setItem').mockRejectedValueOnce(new Error('disk full'));
  await ReactTestRenderer.act(async () => {
    await button().props.onPress();
  });
  expect(replace).not.toHaveBeenCalled();
  expect(button().props.loading).toBe(false);
  expect(
    renderer!.root.findAllByProps({ accessibilityLiveRegion: 'polite' }).length,
  ).toBeGreaterThan(0);
  await ReactTestRenderer.act(async () => {
    await button().props.onPress();
  });
  expect(replace).toHaveBeenCalledWith('MockAdoptionActivity', { petId: 'dog-1' });
  await expect(getActiveMockAdoption()).resolves.toMatchObject({
    petId: 'dog-1',
    weeklyBudget: 35000,
  });
  save.mockRestore();
  await ReactTestRenderer.act(() => renderer.unmount());
});
