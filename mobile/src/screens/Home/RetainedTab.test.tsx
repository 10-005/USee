import { useEffect, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import ReactTestRenderer from 'react-test-renderer';
import { RetainedTab } from './RetainedTab';

it('mounts lazily and preserves screen state without repeating mount work', async () => {
  const mount = jest.fn();
  const cleanup = jest.fn();
  function Screen() {
    const [count, setCount] = useState(0);
    useEffect(() => {
      mount();
      return cleanup;
    }, []);
    return (
      <Pressable accessibilityLabel="탭 상태 변경" onPress={() => setCount(value => value + 1)}>
        <Text>{count}</Text>
      </Pressable>
    );
  }
  const render = (active: boolean) => (
    <RetainedTab active={active}>
      <Screen />
    </RetainedTab>
  );
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  try {
    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(render(false));
    });
    expect(mount).not.toHaveBeenCalled();
    await ReactTestRenderer.act(() => renderer.update(render(true)));
    await ReactTestRenderer.act(() =>
      renderer.root.findAllByProps({ accessibilityLabel: '탭 상태 변경' })[0].props.onPress(),
    );
    await ReactTestRenderer.act(() => renderer.update(render(false)));
    expect(cleanup).not.toHaveBeenCalled();
    const hidden = renderer.root
      .findAllByType(View)
      .find(node => node.props.importantForAccessibility === 'no-hide-descendants');
    expect(hidden?.props.pointerEvents).toBe('none');
    await ReactTestRenderer.act(() => renderer.update(render(true)));
    expect(renderer.root.findByType(Text).props.children).toBe(1);
    expect(mount).toHaveBeenCalledTimes(1);
  } finally {
    await ReactTestRenderer.act(() => renderer?.unmount());
  }
  expect(cleanup).toHaveBeenCalledTimes(1);
});
