import { useEffect, useRef, useState } from 'react';
import {
  AppState,
  NativeEventEmitter,
  NativeModules,
  PermissionsAndroid,
  Platform,
} from 'react-native';
import { parseWalkVoiceCommand } from './voiceCommand';
import { getWalkSpeechErrorMessage } from './speechFeedback';

type SpeechResult = { status: string; transcript: string; reason?: string; requestId: number };
const speech = NativeModules.WalkSpeech;
let nextAttemptId = 0;

export function useWalkVoice(active: boolean, petName = '') {
  const [isListening, setIsListening] = useState(false);
  const [isStarting, setIsStarting] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [actionRequest, setActionRequest] = useState('');
  const commandId = useRef(0);
  const startRef = useRef<() => Promise<void>>(async () => {});

  useEffect(() => {
    let disposed = false;
    let attempt = 0;
    let busy = false;
    let listening = false;
    const stop = () => {
      attempt = 0;
      busy = false;
      listening = false;
      setIsStarting(false);
      setIsListening(false);
      setMessage('');
      speech?.stop();
    };
    const fail = (text: string) => {
      stop();
      setError(text);
    };
    setIsListening(false);
    setIsStarting(false);
    setMessage('');
    if (!active) {
      startRef.current = async () => {};
      return;
    }
    const subscription =
      speech && Platform.OS === 'android'
        ? new NativeEventEmitter(speech).addListener('WalkSpeechResult', value => {
            const event = value as SpeechResult;
            if (
              disposed ||
              !listening ||
              event.requestId !== attempt ||
              AppState.currentState !== 'active'
            ) {
              return;
            }
            const { status, transcript, reason } = event;
            if (status === 'recognized') {
              const command = parseWalkVoiceCommand(transcript, petName);
              stop();
              if (command) {
                setActionRequest(`${++commandId.current}:${command}`);
              }
            } else if (status === 'partial') {
              setMessage(transcript ? `“${transcript}”` : '');
            } else if (status === 'permissionDenied') {
              fail('설정에서 마이크 권한을 허용해주세요');
            } else if (status === 'unavailable') {
              fail('음성 인식 서비스 설정을 확인해주세요');
            } else if (status === 'error' || status === 'waiting') {
              fail(getWalkSpeechErrorMessage(reason));
            }
          })
        : undefined;
    startRef.current = async () => {
      if (disposed || busy || AppState.currentState !== 'active') {
        return;
      }
      setError('');
      if (Platform.OS !== 'android' || !speech) {
        setError('이 기기에서는 음성 명령을 지원하지 않습니다.');
        return;
      }
      busy = true;
      const current = ++nextAttemptId;
      attempt = current;
      setIsStarting(true);
      try {
        const granted = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
          {
            title: '산책 음성 명령',
            message:
              '강아지에게 “앉아”, “멈춰”, “가자”, “돌아”라고 말하려면 마이크 권한이 필요합니다.',
            buttonPositive: '확인',
          },
        );
        if (disposed || current !== attempt || AppState.currentState !== 'active') {
          return;
        }
        if (granted !== PermissionsAndroid.RESULTS.GRANTED) {
          fail('설정에서 마이크 권한을 허용해주세요');
          return;
        }
        listening = true;
        setIsStarting(false);
        setIsListening(true);
        setMessage('');
        speech.start(current);
      } catch {
        if (!disposed && current === attempt) {
          fail('음성 인식을 시작하지 못했어요. 버튼을 다시 눌러주세요.');
        }
      }
    };
    const appState = AppState.addEventListener('change', state => {
      if (state !== 'active') {
        stop();
      }
    });
    return () => {
      disposed = true;
      subscription?.remove();
      appState.remove();
      stop();
    };
  }, [active, petName]);

  return {
    startListening: () => startRef.current(),
    isListening: active && isListening,
    isStarting: active && isStarting,
    message,
    error,
    actionRequest,
  };
}
