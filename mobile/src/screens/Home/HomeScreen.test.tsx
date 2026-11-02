import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import ReactTestRenderer from 'react-test-renderer';

import type { RootStackParamList } from '../../app/navigation/types';
import { HomeScreen } from './HomeScreen';
import { Button } from '../../components/common';

const mockNavigate = jest.fn();

jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useNavigation: () => ({ navigate: mockNavigate }),
}));

jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
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
    renderer.root.findAllByProps({ accessibilityLabel: '모의입양' })[0].props.onPress();
  });
  expect(
    renderer!.root.findAllByProps({ accessibilityLabel: '모의입양' })[0].props.accessibilityState,
  ).toEqual({ selected: true });
  expect(renderer!.root.findAllByProps({ accessibilityLabel: '기간 필터' })).toHaveLength(0);
  const startButton = () => renderer.root.findByType(Button);
  const options = () =>
    renderer.root
      .findAllByProps({ accessibilityRole: 'radio' })
      .filter(node => node.parent?.props.accessibilityRole !== 'radio');
  expect(options()).toHaveLength(6);
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
  expect(options()).toHaveLength(6);
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
  expect(mockNavigate).toHaveBeenCalledWith('MockAdoptionActivity', { petId: 'cat-2' });
  await ReactTestRenderer.act(() => {
    renderer.root.findAllByProps({ accessibilityLabel: '입양' })[0].props.onPress();
  });
  expect(
    renderer!.root.findAllByProps({ placeholder: '특징으로 검색해보세요' })[0].props.value,
  ).toBe('진도');
  await ReactTestRenderer.act(() => renderer.unmount());
}, 20000);
