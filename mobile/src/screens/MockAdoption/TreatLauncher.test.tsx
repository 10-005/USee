import ReactTestRenderer from 'react-test-renderer';
import { Animated, PanResponder } from 'react-native';
import { TreatLauncher } from './TreatLauncher';

jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);

test.each([
  [-120, -1, true, 'treat'],
  [40, 1, false, 'treat'],
  [-120, -1, true, 'toy'],
  [40, 1, false, 'toy'],
] as const)('only upward throws launch the item (dy=%s)', async (dy, vy, accepted, item) => {
  jest.useFakeTimers();
  const create = jest.spyOn(PanResponder, 'create');
  const timing = jest.spyOn(Animated, 'timing').mockReturnValue({
    start: callback => callback?.({ finished: true }),
    stop: jest.fn(),
    reset: jest.fn(),
  });
  const onLanded = jest.fn();
  const onBusyChange = jest.fn();
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(
      <TreatLauncher
        category="dog"
        item={item}
        width={390}
        height={844}
        target={{ x: 200, y: 500 }}
        disabled={false}
        onLanded={onLanded}
        onBusyChange={onBusyChange}
      />,
    );
  });
  const handlers = create.mock.calls[create.mock.calls.length - 1][0];
  const event = { nativeEvent: { locationX: 36, locationY: 36 } } as never;
  await ReactTestRenderer.act(() => {
    handlers.onPanResponderGrant?.(event, {} as never);
  });
  expect(onBusyChange).toHaveBeenCalledWith(true);
  await ReactTestRenderer.act(() => {
    handlers.onPanResponderRelease?.(event, { dx: 0, dy, vy } as never);
  });
  expect(onLanded).toHaveBeenCalledTimes(accepted ? 1 : 0);
  if (accepted && item === 'toy') {
    expect(onLanded).toHaveBeenCalledWith({ x: 200, y: 500 });
  }
  if (!accepted) {
    expect(onBusyChange).toHaveBeenLastCalledWith(false);
  }
  await ReactTestRenderer.act(() => renderer.unmount());
  create.mockRestore();
  timing.mockRestore();
  jest.useRealTimers();
});
