import { useEffect, useRef, useState } from 'react';
import {
  AppState,
  NativeEventEmitter,
  NativeModules,
  PermissionsAndroid,
  Platform,
} from 'react-native';
import { parseWalkVoiceCommand } from './voiceCommand';

type SpeechResult = { status: string; transcript: string };
const speech = NativeModules.WalkSpeech;

export function useWalkVoice(active: boolean) {
  const [enabled, setEnabled] = useState(true);
  const [message, setMessage] = useState('음성 명령 준비 중');
  const [isRecognizing, setIsRecognizing] = useState(false);
  const [actionRequest, setActionRequest] = useState('');
  const requestId = useRef(0);
  useEffect(() => {
    setIsRecognizing(false);
    if (!active || !enabled) {
      return;
    }
    if (Platform.OS !== 'android' || !speech) {
      setMessage('이 기기에서는 음성 명령을 지원하지 않습니다.');
      return;
    }
    let disposed = false;
    let lifecycleVersion = 0;
    const subscription = new NativeEventEmitter(speech).addListener('WalkSpeechResult', event => {
      const { status, transcript } = event as SpeechResult;
      if (disposed || AppState.currentState !== 'active') {
        return;
      }
      if (status === 'recognized') {
        setIsRecognizing(false);
        setMessage(transcript ? `“${transcript}”` : '앉아 / 멈춰 / 가자라고 말해 주세요');
        const command = parseWalkVoiceCommand(transcript);
        if (command) {
          setActionRequest(`${++requestId.current}:${command}`);
        }
      } else if (status === 'hearing') {
        setIsRecognizing(true);
      } else if (status === 'permissionDenied') {
        setIsRecognizing(false);
        setMessage('음성 명령을 사용하려면 설정에서 마이크 권한을 허용해 주세요.');
      } else if (status === 'unavailable') {
        setIsRecognizing(false);
        setMessage('사용 가능한 음성 인식 서비스가 없습니다.');
      } else if (status === 'error') {
        setIsRecognizing(false);
        setMessage('음성 인식이 중단되었습니다. 음성을 껐다 켜서 재시도해 주세요.');
      } else if (status === 'retrying') {
        setIsRecognizing(false);
        setMessage('음성 인식에 다시 연결 중');
      }
    });
    const start = async () => {
      const version = ++lifecycleVersion;
      try {
        const granted = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
          {
            title: '산책 음성 명령',
            message: '강아지에게 “앉아”, “멈춰”, “가자”라고 말하려면 마이크 권한이 필요합니다.',
            buttonPositive: '확인',
          },
        );
        if (disposed || version !== lifecycleVersion || AppState.currentState !== 'active') {
          return;
        }
        if (granted === PermissionsAndroid.RESULTS.GRANTED) {
          setIsRecognizing(true);
          setMessage('듣는 중 · 앉아 / 멈춰 / 가자');
          speech.start();
        } else {
          setIsRecognizing(false);
          setMessage('마이크 권한이 없어 음성 명령을 사용할 수 없습니다.');
        }
      } catch {
        if (!disposed) {
          setIsRecognizing(false);
          setMessage('마이크 권한을 확인하지 못했습니다. 음성을 껐다 켜 주세요.');
        }
      }
    };
    if (AppState.currentState === 'active') {
      start();
    }
    const appState = AppState.addEventListener('change', state => {
      if (state === 'active') {
        start();
      } else {
        setIsRecognizing(false);
        lifecycleVersion++;
        speech.stop();
      }
    });
    return () => {
      disposed = true;
      lifecycleVersion++;
      subscription.remove();
      appState.remove();
      speech.stop();
    };
  }, [active, enabled]);
  return {
    enabled,
    setEnabled,
    message,
    actionRequest,
    isRecognizing: active && enabled && isRecognizing,
  };
}
