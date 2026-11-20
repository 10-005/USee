import ReactTestRenderer from 'react-test-renderer';
import {
  AppState,
  NativeEventEmitter,
  NativeModules,
  PermissionsAndroid,
  Platform,
} from 'react-native';
import { useWalkVoice } from './useWalkVoice';

jest.mock('react-native', () => ({
  Platform: { OS: 'android' },
  AppState: { currentState: 'active', addEventListener: jest.fn(() => ({ remove: jest.fn() })) },
  NativeModules: { WalkSpeech: { start: jest.fn(), stop: jest.fn() } },
  NativeEventEmitter: jest.fn(() => ({ addListener: jest.fn(() => ({ remove: jest.fn() })) })),
  PermissionsAndroid: {
    request: jest.fn(async () => 'granted'),
    PERMISSIONS: { RECORD_AUDIO: 'record_audio' },
    RESULTS: { GRANTED: 'granted' },
  },
}));

let voice: ReturnType<typeof useWalkVoice>;
let renderer: ReactTestRenderer.ReactTestRenderer;
function Harness({ active = true }: { active?: boolean }) {
  voice = useWalkVoice(active, '초코');
  return null;
}
const start = () =>
  ReactTestRenderer.act(async () => {
    await voice.startListening();
  });
const receive = (
  status: string,
  transcript = '',
  requestId = NativeModules.WalkSpeech.start.mock.calls.at(-1)?.[0],
) => {
  const emitter = jest.mocked(NativeEventEmitter).mock.results[0].value;
  return ReactTestRenderer.act(() =>
    emitter.addListener.mock.calls[0][1]({ status, transcript, requestId }),
  );
};

beforeEach(async () => {
  jest.clearAllMocks();
  Platform.OS = 'android';
  AppState.currentState = 'active';
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<Harness />);
  });
});
afterEach(async () => {
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('only a button press requests permission and starts listening', async () => {
  expect(PermissionsAndroid.request).not.toHaveBeenCalled();
  expect(NativeModules.WalkSpeech.start).not.toHaveBeenCalled();
  expect(voice.isListening).toBe(false);
  await start();
  expect(NativeModules.WalkSpeech.start).toHaveBeenCalledTimes(1);
  expect(voice.isListening).toBe(true);
  await start();
  expect(NativeModules.WalkSpeech.start).toHaveBeenCalledTimes(1);
});

test('partial text appears only during recognition; one final sentence stops and acts once', async () => {
  await start();
  await receive('partial', '초코야');
  expect(voice.message).toBe('“초코야”');
  expect(voice.actionRequest).toBe('');
  await receive('recognized', '초코야 앉아');
  expect(voice.actionRequest).toBe('1:sit');
  expect(voice.isListening).toBe(false);
  expect(voice.message).toBe('');
  expect(NativeModules.WalkSpeech.stop).toHaveBeenCalled();
  await receive('recognized', '초코야 가자');
  expect(voice.actionRequest).toBe('1:sit');
  expect(NativeModules.WalkSpeech.start).toHaveBeenCalledTimes(1);
});

test.each([
  ['초코야', 'look'],
  ['초코야 돌아', 'spin'],
  ['초코야 멈춰', 'stop'],
  ['초코야 가자', 'go'],
])('preserves command %s', async (text, command) => {
  await start();
  await receive('recognized', text);
  expect(voice.actionRequest).toBe(`1:${command}`);
  expect(voice.isListening).toBe(false);
});

test('unknown sentence also stops and late results cannot affect a new attempt', async () => {
  await start();
  const oldId = NativeModules.WalkSpeech.start.mock.calls[0][0];
  await receive('recognized', '오늘 날씨 좋다');
  expect(voice.isListening).toBe(false);
  expect(voice.actionRequest).toBe('');
  await start();
  await receive('recognized', '앉아', oldId);
  expect(voice.isListening).toBe(true);
  expect(voice.actionRequest).toBe('');
  await receive('recognized', '앉아');
  expect(voice.actionRequest).toBe('1:sit');
});

test('permission denial stops without starting and allows another press', async () => {
  jest.mocked(PermissionsAndroid.request).mockResolvedValueOnce('denied');
  await start();
  expect(NativeModules.WalkSpeech.start).not.toHaveBeenCalled();
  expect(voice.isListening).toBe(false);
  expect(voice.error).toContain('마이크 권한');
  await start();
  expect(voice.isListening).toBe(true);
});

test('backgrounding cancels capture and foregrounding never starts automatically', async () => {
  await start();
  const onState = jest.mocked(AppState.addEventListener).mock.calls[0][1];
  AppState.currentState = 'background';
  await ReactTestRenderer.act(() => onState('background'));
  await receive('recognized', '앉아');
  expect(voice.isListening).toBe(false);
  expect(voice.actionRequest).toBe('');
  AppState.currentState = 'active';
  await ReactTestRenderer.act(() => onState('active'));
  expect(NativeModules.WalkSpeech.start).toHaveBeenCalledTimes(1);
  await start();
  expect(NativeModules.WalkSpeech.start).toHaveBeenCalledTimes(2);
});

test('timeout or error stops and screen exit ignores late results', async () => {
  await start();
  await receive('error');
  expect(voice.isListening).toBe(false);
  expect(voice.error).toContain('다시 눌러주세요');
  await start();
  await ReactTestRenderer.act(() => renderer.update(<Harness active={false} />));
  await receive('recognized', '앉아');
  expect(voice.isListening).toBe(false);
  expect(voice.actionRequest).toBe('');
});

test('leaving while permission is pending prevents a delayed start', async () => {
  let grant!: (value: 'granted') => void;
  jest.mocked(PermissionsAndroid.request).mockImplementationOnce(
    () =>
      new Promise(resolve => {
        grant = resolve;
      }),
  );
  let pending!: Promise<void>;
  await ReactTestRenderer.act(() => {
    pending = voice.startListening();
  });
  await ReactTestRenderer.act(() => renderer.update(<Harness active={false} />));
  await ReactTestRenderer.act(async () => {
    grant('granted');
    await pending;
  });
  expect(NativeModules.WalkSpeech.start).not.toHaveBeenCalled();
});
