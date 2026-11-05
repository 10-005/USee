import { useHeaderHeight } from '@react-navigation/elements';
import { useIsFocused } from '@react-navigation/native';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, Text, View, type NativeSyntheticEvent } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useCameraPermission } from 'react-native-vision-camera';

import { styles } from './ArWalkScreen.styles';
import {
  ArFloorView,
  type FloorTracking,
  type FloorTrackingStatus,
} from './components/ArFloorView';

const STARTUP_ERRORS: Partial<Record<FloorTrackingStatus, string>> = {
  recognitionError: '지형 인식을 시작하지 못했습니다. 산책 화면을 다시 열어 주세요.',
  needsInstall: 'Google Play Services for AR을 설치해 주세요.',
  unsupported: '이 기기는 AR 산책을 지원하지 않습니다.',
  error: 'AR 산책을 시작하지 못했습니다. 카메라 권한을 확인해 주세요.',
};

export function ArWalkScreen() {
  const isFocused = useIsFocused();
  const insets = useSafeAreaInsets();
  const headerHeight = useHeaderHeight();
  const { hasPermission, requestPermission, canRequestPermission } = useCameraPermission();
  const [status, setStatus] = useState<FloorTrackingStatus>('searching');
  const [placementRequest, setPlacementRequest] = useState(0);
  const [canReposition, setCanReposition] = useState(true);
  const [showTraversalDebug, setShowTraversalDebug] = useState(false);
  const [hasSeenDog, setHasSeenDog] = useState(false);
  const [loadingDots, setLoadingDots] = useState(2);
  const [walkStartedAt, setWalkStartedAt] = useState<number | null>(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  useEffect(() => {
    if (walkStartedAt === null) {
      return;
    }
    const updateElapsed = () => {
      setElapsedSeconds(Math.floor((Date.now() - walkStartedAt) / 1000));
    };
    const interval = setInterval(updateElapsed, 1000);
    return () => clearInterval(interval);
  }, [walkStartedAt]);

  useEffect(() => {
    if (!hasPermission && canRequestPermission) {
      requestPermission().catch(() => undefined);
    }
  }, [canRequestPermission, hasPermission, requestPermission]);

  const startupError = STARTUP_ERRORS[status];
  const isRecognizing = hasPermission && isFocused && !hasSeenDog && !startupError;

  useEffect(() => {
    if (!isRecognizing) {
      return;
    }
    const interval = setInterval(() => setLoadingDots(value => (value % 3) + 1), 400);
    return () => clearInterval(interval);
  }, [isRecognizing]);

  const onTracking = (event: NativeSyntheticEvent<FloorTracking>) => {
    const next = event.nativeEvent;
    if (next.visible && next.onScreen) {
      setHasSeenDog(true);
    }
    if (isFocused && next.status === 'anchored' && next.userMoving) {
      setWalkStartedAt(current => current ?? Date.now());
    }
    setCanReposition(
      ['analyzing', 'searching', 'ready', 'anchored'].includes(next.status) &&
        !next.onScreen &&
        !next.userMoving,
    );
    setStatus(current => (current === next.status ? current : next.status));
  };

  const walkTime = `${Math.floor(elapsedSeconds / 60)
    .toString()
    .padStart(2, '0')}:${(elapsedSeconds % 60).toString().padStart(2, '0')}`;

  return (
    <View style={styles.screen}>
      {hasPermission ? (
        <ArFloorView
          active={isFocused}
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
      <View pointerEvents="none" style={[styles.walkTimer, { top: insets.top + 16 }]}>
        <Text accessibilityLabel={`산책 시간 ${walkTime}`} style={styles.walkTimerText}>
          {walkTime}
        </Text>
      </View>
      {isRecognizing && (
        <View pointerEvents="none" style={styles.loadingOverlay}>
          <ActivityIndicator color="#FFFFFF" size="large" />
          <Text accessibilityLabel="지형 인식 중" style={styles.loadingText}>
            지형 인식 중{'.'.repeat(loadingDots)}
          </Text>
        </View>
      )}
      {hasPermission && !hasSeenDog && startupError && (
        <View pointerEvents="none" style={styles.loadingOverlay}>
          <Text style={styles.messageText}>{startupError}</Text>
        </View>
      )}
      {hasPermission && (
        <View pointerEvents="box-none" style={[styles.displayControls, { top: headerHeight + 8 }]}>
          <Pressable
            accessibilityRole="switch"
            accessibilityLabel="이동 가능 영역 표시"
            accessibilityState={{ checked: showTraversalDebug }}
            onPress={() => setShowTraversalDebug(value => !value)}
            style={styles.areaToggle}
          >
            <Text style={styles.areaToggleLabel}>영역 표시</Text>
            <View style={[styles.toggleTrack, showTraversalDebug && styles.toggleTrackOn]}>
              <View style={[styles.toggleThumb, showTraversalDebug && styles.toggleThumbOn]} />
            </View>
          </Pressable>
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
