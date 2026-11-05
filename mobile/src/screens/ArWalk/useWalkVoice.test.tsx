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
function Harness({ active, name = '' }: { active: boolean; name?: string }) {
  voice = useWalkVoice(active, name);
  return null;
}

beforeEach(() => {
  jest.clearAllMocks();
  Platform.OS = 'android';
  AppState.currentState = 'active';
});

test('saved-name call requests attention while name plus command requests only the command', async () => {
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<Harness active name="초코" />);
  });
  const emitter = jest.mocked(NativeEventEmitter).mock.results[0].value;
  const receive = emitter.addListener.mock.calls[0][1];
  await ReactTestRenderer.act(() => receive({ status: 'recognized', transcript: '초코야' }));
  expect(voice.actionRequest).toBe('1:look');
  await ReactTestRenderer.act(() => receive({ status: 'recognized', transcript: '초코야 앉아' }));
  expect(voice.actionRequest).toBe('2:sit');
  await ReactTestRenderer.act(() => receive({ status: 'recognized', transcript: '보리야' }));
  expect(voice.actionRequest).toBe('2:sit');
  await ReactTestRenderer.act(() => receive({ status: 'recognized', transcript: '초코야 돌아' }));
  expect(voice.actionRequest).toBe('3:spin');
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('a full utterance requests sit once, ignores unknown text, and stops on screen exit', async () => {
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<Harness active />);
  });
  expect(PermissionsAndroid.request).toHaveBeenCalled();
  expect(NativeModules.WalkSpeech.start).toHaveBeenCalledTimes(1);
  const emitter = jest.mocked(NativeEventEmitter).mock.results[0].value;
  const receive = emitter.addListener.mock.calls[0][1];
  await ReactTestRenderer.act(() => receive({ status: 'recognized', transcript: '초코야 앉아' }));
  expect(voice.actionRequest).toBe('1:sit');
  await ReactTestRenderer.act(() => receive({ status: 'recognized', transcript: '초코야' }));
  expect(voice.actionRequest).toBe('1:sit');
  await ReactTestRenderer.act(() => receive({ status: 'recognized', transcript: '초코야 멈춰' }));
  expect(voice.actionRequest).toBe('2:stop');
  expect(voice.message).toBe('“초코야 멈춰”');
  expect(voice.isRecognizing).toBe(false);
  await ReactTestRenderer.act(() => receive({ status: 'recognized', transcript: '초코야 가자' }));
  expect(voice.actionRequest).toBe('3:go');
  expect(voice.message).toBe('“초코야 가자”');
  await ReactTestRenderer.act(() => receive({ status: 'listening', transcript: '' }));
  expect(voice.message).toBe('“초코야 가자”');
  expect(voice.isRecognizing).toBe(false);
  await ReactTestRenderer.act(() => receive({ status: 'hearing', transcript: '' }));
  expect(voice.isRecognizing).toBe(true);
  await ReactTestRenderer.act(() => renderer.update(<Harness active={false} />));
  expect(NativeModules.WalkSpeech.stop).toHaveBeenCalled();
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('denied microphone permission leaves walking available without starting recognition', async () => {
  jest.mocked(PermissionsAndroid.request).mockResolvedValueOnce('denied');
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<Harness active />);
  });
  expect(NativeModules.WalkSpeech.start).not.toHaveBeenCalled();
  expect(voice.message).toContain('마이크 권한');
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('backgrounding stops recognition, ignores late speech and resumes only in foreground', async () => {
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<Harness active />);
  });
  const emitter = jest.mocked(NativeEventEmitter).mock.results[0].value;
  const receive = emitter.addListener.mock.calls[0][1];
  const onState = jest.mocked(AppState.addEventListener).mock.calls[0][1];
  AppState.currentState = 'background';
  await ReactTestRenderer.act(() => {
    onState('background');
    receive({ status: 'recognized', transcript: '초코야 앉아' });
  });
  expect(NativeModules.WalkSpeech.stop).toHaveBeenCalled();
  expect(voice.actionRequest).toBe('');
  AppState.currentState = 'active';
  await ReactTestRenderer.act(async () => {
    onState('active');
  });
  expect(NativeModules.WalkSpeech.start).toHaveBeenCalledTimes(2);
  await ReactTestRenderer.act(() => voice.setEnabled(false));
  expect(NativeModules.WalkSpeech.stop).toHaveBeenCalledTimes(2);
  await ReactTestRenderer.act(() => renderer.unmount());
});
