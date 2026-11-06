import ReactTestRenderer from 'react-test-renderer';
import { Animated, AppState, type AppStateStatus } from 'react-native';
import { RoomPet } from './RoomPet';

let mockFocused = true;
jest.mock('@react-navigation/native', () => ({ useIsFocused: () => mockFocused }));
jest.mock('react-native', () => {
  const actual = jest.requireActual('react-native');
  return {
    AppState: actual.AppState,
    Platform: { OS: 'android' },
    Animated: actual.Animated,
    Easing: actual.Easing,
    requireNativeComponent: () => 'RoomPetView',
  };
});

test('waits for model readiness and consumes each AR return once', async () => {
  const originalState = AppState.currentState;
  AppState.currentState = 'active';
  mockFocused = true;
  let finishReturn: ((result: { finished: boolean }) => void) | undefined;
  const timing = jest.spyOn(Animated, 'timing').mockReturnValue({
    start: callback => {
      finishReturn = callback;
    },
    stop: jest.fn(),
    reset: jest.fn(),
  });
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(
      <RoomPet petId="dog-1" returnSequence={1} entryOffset={{ x: 200, y: 240 }} />,
    );
  });
  expect(timing).not.toHaveBeenCalled();
  const native = () => renderer.root.findByType('RoomPetView' as never);
  expect(native().props.returningFromWalk).toBe(true);
  await ReactTestRenderer.act(() => native().props.onReady());
  expect(timing).toHaveBeenCalledWith(
    expect.anything(),
    expect.objectContaining({
      duration: 3000,
      toValue: { x: 0, y: 0 },
      useNativeDriver: true,
    }),
  );
  await ReactTestRenderer.act(() =>
    renderer.update(<RoomPet petId="dog-1" returnSequence={1} entryOffset={{ x: 220, y: 260 }} />),
  );
  await ReactTestRenderer.act(() => native().props.onReady());
  expect(timing).toHaveBeenCalledTimes(1);
  await ReactTestRenderer.act(() => finishReturn?.({ finished: true }));
  await ReactTestRenderer.act(() => renderer.update(<RoomPet petId="dog-1" returnSequence={1} />));
  expect(native().props.returningFromWalk).toBe(false);
  await ReactTestRenderer.act(() => renderer.update(<RoomPet petId="dog-1" returnSequence={2} />));
  expect(native().props.returningFromWalk).toBe(true);
  await ReactTestRenderer.act(() => renderer.unmount());
  timing.mockRestore();
  AppState.currentState = originalState;
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

test('eats a treat in place for two seconds without walking', async () => {
  const listener = jest.spyOn(AppState, 'addEventListener').mockReturnValue({ remove: jest.fn() });
  jest.useFakeTimers();
  const previous = AppState.currentState;
  AppState.currentState = 'active';
  mockFocused = true;
  const timing = jest.spyOn(Animated, 'timing');
  const onCareComplete = jest.fn();
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(
      <RoomPet
        petId="dog-1"
        care={{ kind: 'treat', offset: { x: 0, y: 0 } }}
        onCareComplete={onCareComplete}
      />,
    );
  });
  const native = () => renderer.root.findByType('RoomPetView' as never);
  expect(native().props.careMotion).toBe('treat');
  expect(timing).not.toHaveBeenCalled();
  await ReactTestRenderer.act(() => jest.advanceTimersByTime(1999));
  expect(onCareComplete).not.toHaveBeenCalled();
  await ReactTestRenderer.act(() => jest.advanceTimersByTime(1));
  expect(onCareComplete).toHaveBeenCalledTimes(1);
  expect(native().props.careMotion).toBe('');
  await ReactTestRenderer.act(() => renderer.unmount());
  timing.mockRestore();
  listener.mockRestore();
  AppState.currentState = previous;
  jest.useRealTimers();
});

test('fetch runs to the toy, picks it up, carries it home and drops it', async () => {
  jest.useFakeTimers();
  const previous = AppState.currentState;
  AppState.currentState = 'active';
  mockFocused = true;
  const listener = jest.spyOn(AppState, 'addEventListener').mockReturnValue({ remove: jest.fn() });
  const completions: Array<(result: { finished: boolean }) => void> = [];
  const timing = jest.spyOn(Animated, 'timing').mockReturnValue({
    start: callback => {
      if (callback) {completions.push(callback);}
    },
    stop: jest.fn(),
    reset: jest.fn(),
  });
  const onCareComplete = jest.fn();
  const onFetchPhase = jest.fn();
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(
      <RoomPet
        petId="dog-1"
        care={{ kind: 'fetch', offset: { x: -100, y: 80 } }}
        onCareComplete={onCareComplete}
        onFetchPhase={onFetchPhase}
      />,
    );
  });
  const native = () => renderer.root.findByType('RoomPetView' as never);
  expect(native().props.careMotion).toBe('fetchRun');
  await ReactTestRenderer.act(() => completions[0]({ finished: true }));
  expect(native().props.careMotion).toBe('fetchPick');
  await ReactTestRenderer.act(() => jest.advanceTimersByTime(799));
  expect(onFetchPhase).not.toHaveBeenCalled();
  await ReactTestRenderer.act(() => jest.advanceTimersByTime(1));
  expect(native().props.careMotion).toBe('fetchCarry');
  expect(onFetchPhase).toHaveBeenLastCalledWith('carry');
  expect(timing).toHaveBeenLastCalledWith(
    expect.anything(),
    expect.objectContaining({ toValue: { x: 0, y: 0 } }),
  );
  await ReactTestRenderer.act(() => completions[1]({ finished: true }));
  expect(native().props.careMotion).toBe('fetchDrop');
  expect(onFetchPhase).toHaveBeenLastCalledWith('drop');
  await ReactTestRenderer.act(() => jest.advanceTimersByTime(700));
  expect(onCareComplete).toHaveBeenCalledTimes(1);
  expect(native().props.careMotion).toBe('');
  await ReactTestRenderer.act(() => renderer.unmount());
  listener.mockRestore();
  timing.mockRestore();
  AppState.currentState = previous;
  jest.useRealTimers();
});
