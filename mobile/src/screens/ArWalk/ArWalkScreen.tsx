import { useIsFocused } from '@react-navigation/native';
import { useEffect, useState } from 'react';
import { Pressable, Switch, Text, View, type NativeSyntheticEvent } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useCameraPermission } from 'react-native-vision-camera';

import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../app/navigation/types';

import { styles } from './ArWalkScreen.styles';
import {
  ArFloorView,
  type FloorTracking,
  type FloorTrackingStatus,
} from './components/ArFloorView';

const STATUS_COPY: Record<FloorTrackingStatus | 'leading', { label: string; hint: string }> = {
  analyzing: {
    label: '걸을 수 있는 바닥을 확인하는 중',
    hint: '바닥이 화면에 보이도록 휴대폰을 천천히 움직여 주세요.',
  },
  recognitionError: {
    label: '바닥 인식을 시작하지 못했습니다',
    hint: '산책 화면을 닫았다가 다시 열어 주세요.',
  },
  searching: {
    label: '바닥을 찾는 중',
    hint: '바닥이 보이도록 휴대폰을 천천히 움직여 주세요.',
  },
  ready: {
    label: '강아지를 놓을 수 있어요',
    hint: '바닥이 확인되면 내 앞 약 1m에 강아지가 자동으로 배치됩니다.',
  },
  anchored: {
    label: '앞에서 기다리는 중',
    hint: '걸음을 옮기면 강아지가 방향을 맞춰 함께 움직입니다.',
  },
  leading: {
    label: '함께 걷는 중',
    hint: '방향을 바꾸거나 뒤로 걸어도 강아지가 따라옵니다.',
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

export function ArWalkScreen({
  route,
}: Partial<NativeStackScreenProps<RootStackParamList, 'ArWalk'>>) {
  const isFocused = useIsFocused();
  const insets = useSafeAreaInsets();
  const { hasPermission, requestPermission, canRequestPermission } = useCameraPermission();
  const [status, setStatus] = useState<FloorTrackingStatus | 'leading'>('searching');
  const [placementRequest, setPlacementRequest] = useState(0);
  const [canReposition, setCanReposition] = useState(true);
  const [showTraversalDebug, setShowTraversalDebug] = useState(true);

  useEffect(() => {
    if (!hasPermission && canRequestPermission) {
      requestPermission().catch(() => undefined);
    }
  }, [canRequestPermission, hasPermission, requestPermission]);

  const onTracking = (event: NativeSyntheticEvent<FloorTracking>) => {
    const next = event.nativeEvent;
    setCanReposition(
      ['analyzing', 'searching', 'ready', 'anchored'].includes(next.status) &&
        !next.onScreen &&
        !next.userMoving,
    );
    const shown = next.status === 'anchored' && next.moving ? 'leading' : next.status;
    setStatus(current => (current === shown ? current : shown));
  };

  const copy = STATUS_COPY[status];

  return (
    <View style={styles.screen}>
      {hasPermission ? (
        <ArFloorView
          active={isFocused}
          petName={route?.params?.petName ?? '강아지'}
          placementRequest={placementRequest}
          showTraversalDebug={showTraversalDebug}
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
      {hasPermission && (
        <View style={[styles.areaToggle, { top: insets.top + 8 }]}>
          <Text style={styles.areaToggleLabel}>영역 표시</Text>
          <Switch
            accessibilityLabel="이동 가능 영역 표시"
            value={showTraversalDebug}
            onValueChange={setShowTraversalDebug}
            trackColor={{ false: '#666666', true: '#36875C' }}
            thumbColor="#FFFFFF"
          />
        </View>
      )}
      {hasPermission && canReposition && (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="강아지 배치하기"
          accessibilityState={{
            disabled: !isFocused || status === 'searching' || status === 'analyzing',
          }}
          disabled={!isFocused || status === 'searching' || status === 'analyzing'}
          onPress={() => setPlacementRequest(value => value + 1)}
          style={[
            styles.placeButton,
            { bottom: insets.bottom + 24 },
            (status === 'searching' || status === 'analyzing') && styles.placeButtonDisabled,
          ]}
        >
          <Text style={styles.placeButtonText}>강아지 배치하기</Text>
        </Pressable>
      )}
    </View>
  );
}
