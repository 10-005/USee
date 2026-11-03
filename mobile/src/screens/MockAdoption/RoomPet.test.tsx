import ReactTestRenderer from 'react-test-renderer';
import { AppState, type AppStateStatus } from 'react-native';
import { RoomPet } from './RoomPet';

let mockFocused = true;
jest.mock('@react-navigation/native', () => ({ useIsFocused: () => mockFocused }));
jest.mock('react-native', () => {
  const actual = jest.requireActual('react-native');
  return {
    AppState: actual.AppState,
    Platform: { OS: 'android' },
    requireNativeComponent: () => 'RoomPetView',
  };
});

test('releases the native renderer on blur and background, restores it when visible', async () => {
  const remove = jest.fn();
  let changeState: (state: AppStateStatus) => void = () => {};
  const listener = jest
    .spyOn(AppState, 'addEventListener')
    .mockImplementation((_event, callback) => {
      changeState = callback;
      return { remove };
    });
  const originalState = AppState.currentState;
  AppState.currentState = 'active';
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<RoomPet petId="cat-2" />);
  });
  expect(renderer!.toJSON()).toMatchObject({
    type: 'RoomPetView',
    props: { petId: 'cat-2', active: true },
  });
  mockFocused = false;
  await ReactTestRenderer.act(() => renderer.update(<RoomPet petId="cat-2" />));
  expect(renderer!.toJSON()).toBeNull();
  mockFocused = true;
  await ReactTestRenderer.act(() => renderer.update(<RoomPet petId="cat-2" />));
  expect(renderer!.toJSON()).not.toBeNull();
  await ReactTestRenderer.act(() => changeState('background'));
  expect(renderer!.toJSON()).toBeNull();
  await ReactTestRenderer.act(() => changeState('active'));
  expect(renderer!.toJSON()).not.toBeNull();
  await ReactTestRenderer.act(() => renderer.unmount());
  expect(remove).toHaveBeenCalled();
  AppState.currentState = originalState;
  listener.mockRestore();
});
