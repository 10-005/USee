import ReactTestRenderer from 'react-test-renderer';

import { ArWalkScreen } from './ArWalkScreen';
import { ArFloorView, type FloorTracking } from './components/ArFloorView';

jest.mock('@react-navigation/native', () => ({ useIsFocused: () => true }));
jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
}));
jest.mock('react-native-vision-camera', () => ({
  useCameraPermission: () => ({ hasPermission: true, canRequestPermission: false }),
}));
jest.mock('./components/ArFloorView', () => ({ ArFloorView: 'ArFloorView' }));

test('placement remains visible during analysis and hides only for visible dogs or moving users', async () => {
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<ArWalkScreen />);
  });
  const buttons = () => renderer.root.findAllByProps({ accessibilityLabel: '강아지 배치하기' });
  const tracking = async (state: Partial<FloorTracking>) => {
    await ReactTestRenderer.act(() => {
      renderer.root.findByType(ArFloorView).props.onTracking({ nativeEvent: state });
    });
  };
  await tracking({ status: 'analyzing', onScreen: false, userMoving: false });
  expect(buttons().length).toBeGreaterThan(0);
  expect(buttons()[0].props.disabled).toBe(true);
  await tracking({ status: 'anchored', onScreen: false, userMoving: false });
  expect(buttons()[0].props.disabled).toBe(false);
  await tracking({ status: 'anchored', onScreen: true, userMoving: false });
  expect(buttons()).toHaveLength(0);
  await tracking({ status: 'anchored', onScreen: false, userMoving: true });
  expect(buttons()).toHaveLength(0);
  await ReactTestRenderer.act(() => renderer.unmount());
});
