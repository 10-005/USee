import { useIsFocused } from '@react-navigation/native';
import { useEffect, useState } from 'react';
import { Text, View, type NativeSyntheticEvent } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useCameraPermission } from 'react-native-vision-camera';

import { styles } from './ArWalkScreen.styles';
import {
  ArFloorView,
  type FloorTracking,
  type FloorTrackingStatus,
} from './components/ArFloorView';

const STATUS_COPY: Record<FloorTrackingStatus | 'leading', { label: string; hint: string }> = {
  searching: {
    label: '바닥을 찾는 중',
    hint: '바닥이 보이도록 휴대폰을 천천히 움직여 주세요.',
  },
  anchored: {
    label: '앞에서 기다리는 중',
    hint: '휴대폰을 돌리면 강아지가 그 방향으로 걸어갑니다.',
  },
  leading: {
    label: '함께 걷는 중',
    hint: '강아지가 앞에 서서 걸어갑니다.',
  },
  lost: {
    label: '추적을 놓쳤습니다',
    hint: '바닥이 다시 보이도록 휴대폰을 움직여 주세요.',
  },
  needsInstall: {
    label: 'ARCore 설치가 필요합니다',
    hint: '화면에 나온 안내에서 Google Play Services for AR을 설치해 주세요.',
  },
  unsupported: {
    label: '이 폰은 바닥 인식을 지원하지 않습니다',
    hint: 'ARCore를 사용할 수 있는 기기에서 다시 열어 주세요.',
  },
  error: {
    label: '바닥 인식을 시작하지 못했습니다',
    hint: '카메라 권한을 확인한 뒤 화면을 다시 열어 주세요.',
  },
};

export function ArWalkScreen() {
  const isFocused = useIsFocused();
  const insets = useSafeAreaInsets();
  const { hasPermission, requestPermission, canRequestPermission } = useCameraPermission();
  const [status, setStatus] = useState<FloorTrackingStatus | 'leading'>('searching');

  useEffect(() => {
    if (!hasPermission && canRequestPermission) {
      requestPermission().catch(() => undefined);
    }
  }, [canRequestPermission, hasPermission, requestPermission]);

  const onTracking = (event: NativeSyntheticEvent<FloorTracking>) => {
    const next = event.nativeEvent;
    const shown =
      next.status === 'anchored' && next.clip && next.clip !== 'Idle' ? 'leading' : next.status;
    setStatus(current => (current === shown ? current : shown));
  };

  const copy = STATUS_COPY[status];

  return (
    <View style={styles.screen}>
      {hasPermission ? (
        <ArFloorView
          active={isFocused}
          collapsable={false}
          style={styles.camera}
          onTracking={onTracking}
        />
      ) : (
        <View style={styles.message}>
          <Text style={styles.messageText}>강아지와 산책하려면 카메라 권한이 필요합니다.</Text>
        </View>
      )}
      <View pointerEvents="none" style={styles.overlay}>
        <View style={[styles.status, { top: insets.top + 56 }]}>
          <Text style={styles.statusLabel}>{copy.label}</Text>
          <Text style={styles.statusHint}>{copy.hint}</Text>
        </View>
      </View>
    </View>
  );
}
