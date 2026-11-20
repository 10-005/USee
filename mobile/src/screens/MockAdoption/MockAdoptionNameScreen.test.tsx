import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import ReactTestRenderer from 'react-test-renderer';
import { Keyboard, ScrollView, TextInput as NativeTextInput } from 'react-native';
import type { RootStackParamList } from '../../app/navigation/types';
import { Button, TextInput } from '../../components/common';
import { MockAdoptionNameScreen } from './MockAdoptionNameScreen';

jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);

test('requires a complete Korean name before proceeding with selected animal and name', async () => {
  const navigate = jest.fn();
  const props = {
    navigation: { navigate, goBack: jest.fn() },
    route: { key: 'name', name: 'MockAdoptionName', params: { petId: 'dog-5' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionName'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionNameScreen {...props} />);
  });
  const input = () => renderer.root.findByType(TextInput);
  const button = () => renderer.root.findByType(Button);
  expect(button().props.disabled).toBe(true);
  expect(input().props.maxLength).toBe(6);
  await ReactTestRenderer.act(() => input().props.onChangeText('초ㅋ'));
  expect(button().props.disabled).toBe(true);
  await ReactTestRenderer.act(() => input().props.onSubmitEditing());
  expect(navigate).not.toHaveBeenCalled();
  await ReactTestRenderer.act(() => input().props.onChangeText('초코'));
  expect(button().props.disabled).toBe(false);
  await ReactTestRenderer.act(() => button().props.onPress());
  expect(navigate).toHaveBeenCalledWith('MockAdoptionBudget', { petId: 'dog-5', petName: '초코' });
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('dismisses the keyboard and clears focus when the surrounding area is pressed', async () => {
  const dismiss = jest.spyOn(Keyboard, 'dismiss').mockImplementation(() => undefined);
  const focusedInput = {};
  const blurTextInput = jest
    .spyOn(NativeTextInput.State, 'blurTextInput')
    .mockImplementation(() => undefined);
  jest.spyOn(NativeTextInput.State, 'currentlyFocusedInput').mockReturnValue(focusedInput as never);
  const props = {
    navigation: { navigate: jest.fn(), goBack: jest.fn() },
    route: { key: 'name', name: 'MockAdoptionName', params: { petId: 'dog-5' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionName'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionNameScreen {...props} />);
  });
  let outside = renderer.root.findByProps({ children: '이름 설정' }).parent;
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
