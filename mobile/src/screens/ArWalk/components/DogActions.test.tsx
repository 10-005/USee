import { Modal } from 'react-native';
import ReactTestRenderer from 'react-test-renderer';

import { DogActions } from './DogActions';

test('selecting a gesture sends its action id and closes the menu', async () => {
  const onAction = jest.fn();
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(
      <DogActions available moving={false} bottom={0} onAction={onAction} />,
    );
  });
  await ReactTestRenderer.act(() => {
    renderer.root.findAllByProps({ accessibilityLabel: '강아지 동작 메뉴' })[0].props.onPress();
  });
  const drink = renderer!.root.findAllByProps({ accessibilityLabel: '물 마시기' })[0];
  await ReactTestRenderer.act(() => drink!.props.onPress());
  expect(onAction).toHaveBeenCalledWith('drink');
  expect(renderer!.root.findByType(Modal).props.visible).toBe(false);
  await ReactTestRenderer.act(() => renderer.unmount());
}, 20000);

test('walking disables the menu and losing tracking removes it', async () => {
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(
      <DogActions available moving bottom={0} onAction={jest.fn()} />,
    );
  });
  expect(
    renderer!.root.findAllByProps({ accessibilityLabel: '강아지 동작 메뉴' })[0].props.disabled,
  ).toBe(true);
  await ReactTestRenderer.act(() => {
    renderer.update(
      <DogActions available={false} moving={false} bottom={0} onAction={jest.fn()} />,
    );
  });
  expect(renderer!.toJSON()).toBeNull();
  await ReactTestRenderer.act(() => renderer.unmount());
});
