import AsyncStorage from '@react-native-async-storage/async-storage';
import ReactTestRenderer from 'react-test-renderer';
import { startMockAdoption } from '../../services/storage/mockAdoptionStorage';
import { mockAdoptionPets } from './data';
import { MockAdoptionShortcut } from './MockAdoptionShortcut';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (callback: () => void) => {
    require('react').useEffect(callback, [callback]);
  },
}));

beforeEach(async () => {
  jest.useFakeTimers();
  await AsyncStorage.clear();
});

afterEach(() => {
  jest.useRealTimers();
});

test('hides the shortcut without an active session', async () => {
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(
      <MockAdoptionShortcut bottom={104} onOpen={jest.fn()} session={null} />,
    );
  });
  expect(renderer.toJSON()).toBeNull();
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('shows the selected pet and opens its activity screen', async () => {
  const session = await startMockAdoption('cat-2', 35000, '나비');
  const onOpen = jest.fn();
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(
      <MockAdoptionShortcut bottom={104} onOpen={onOpen} session={session} />,
    );
  });
  expect(
    renderer.root.findAllByProps({ source: mockAdoptionPets[7].image }).length,
  ).toBeGreaterThan(0);
  await ReactTestRenderer.act(async () => {
    await renderer.root
      .findAllByProps({ accessibilityLabel: '나비 모의입양 바로가기' })[0]
      .props.onPress();
  });
  expect(onOpen).toHaveBeenCalledWith('cat-2');
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('hides at the exact seven-day boundary while the screen stays open', async () => {
  const session = await startMockAdoption('dog-5', 35000, '코코');
  jest.setSystemTime(session.expiresAt - 1);
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(
      <MockAdoptionShortcut bottom={104} onOpen={jest.fn()} session={session} />,
    );
  });
  expect(renderer.toJSON()).not.toBeNull();
  await ReactTestRenderer.act(() => jest.advanceTimersByTime(1));
  expect(renderer.toJSON()).toBeNull();
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('rechecks expiry before navigating', async () => {
  const session = await startMockAdoption('dog-5', 35000, '코코');
  const onOpen = jest.fn();
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(
      <MockAdoptionShortcut bottom={104} onOpen={onOpen} session={session} />,
    );
  });
  jest.setSystemTime(session.expiresAt);
  await ReactTestRenderer.act(async () => {
    await renderer.root
      .findAllByProps({ accessibilityLabel: '코코 모의입양 바로가기' })[0]
      .props.onPress();
  });
  expect(onOpen).not.toHaveBeenCalled();
  expect(renderer.toJSON()).toBeNull();
  await ReactTestRenderer.act(() => renderer.unmount());
});
