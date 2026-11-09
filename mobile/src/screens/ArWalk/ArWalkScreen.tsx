import { useHeaderHeight } from '@react-navigation/elements';
import { useIsFocused } from '@react-navigation/native';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Animated,
  Image,
  PanResponder,
  Pressable,
  Text,
  View,
  type NativeSyntheticEvent,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useCameraPermission } from 'react-native-vision-camera';
import { useShopStore } from '../../stores/shopStore';
import { AppDialog } from '../../components/common/AppDialog';
import { findProduct, money } from '../../services/shop/catalog';
import type { RootStackParamList } from '../../app/navigation/types';

import { styles } from './ArWalkScreen.styles';
import { useWalkVoice } from './useWalkVoice';
import { useWalkRoute } from './useWalkRoute';
import { WalkRouteMap } from './components/WalkRouteMap';
import { VoiceListeningDots } from './components/VoiceListeningDots';
import { VoiceMicrophone } from './components/VoiceMicrophone';
import { VoiceGlowText } from './components/VoiceGlowText';
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

const waterBowl = require('../../assets/images/mock-adoption/img_full_water_bowl.png');
const dogTreat = require('../../assets/images/mock-adoption/img_dog_treats.png');

function WalkTreat({ onThrow }: { onThrow: (dx: number, dy: number) => void }) {
  const offset = useRef(new Animated.ValueXY()).current;
  const latestThrow = useRef(onThrow);
  latestThrow.current = onThrow;
  const responder = useMemo(() => {
    const reset = () => offset.setValue({ x: 0, y: 0 });
    return PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onPanResponderMove: (_, { dx, dy }) => offset.setValue({ x: dx, y: dy }),
      onPanResponderRelease: (_, { dx, dy }) => {
        if (dy <= -40) latestThrow.current(dx, dy);
        else reset();
      },
      onPanResponderTerminate: reset,
    });
  }, [offset]);

  return (
    <View
      {...responder.panHandlers}
      accessible
      accessibilityLabel="간식 잡아서 위로 던지기"
      accessibilityActions={[{ name: 'activate', label: '간식 던지기' }]}
      onAccessibilityAction={({ nativeEvent }) => {
        if (nativeEvent.actionName === 'activate') onThrow(0, -60);
      }}
      style={styles.careButton}
    >
      <Animated.Image
        pointerEvents="none"
        source={dogTreat}
        style={[styles.careIcon, { transform: offset.getTranslateTransform() }]}
      />
    </View>
  );
}

export function ArWalkScreen({
  route,
}: { route?: { params?: RootStackParamList['ArWalk'] } } = {}) {
  const shop = useShopStore();
  const [bagPurchaseVisible, setBagPurchaseVisible] = useState(false);
  const [bagPurchaseError, setBagPurchaseError] = useState('');
  const purchasingBags = useRef(false);
  const bagProduct = findProduct('bags');
  const openBagPurchase = () => {
    purchasingBags.current = false;
    setBagPurchaseError('');
    setBagPurchaseVisible(true);
  };
  const purchaseBags = () => {
    if (purchasingBags.current) {
      return;
    }
    purchasingBags.current = true;
    const error = useShopStore.getState().buyWalkBags();
    if (error) {
      purchasingBags.current = false;
      setBagPurchaseError(error);
    } else {
      setBagPurchaseVisible(false);
    }
  };
  const [cleanupResponse, setCleanupResponse] = useState('');
  const lastCleanupRequest = useRef(0);
  const isFocused = useIsFocused();
  const insets = useSafeAreaInsets();
  const headerHeight = useHeaderHeight();
  const { hasPermission, requestPermission, canRequestPermission } = useCameraPermission();
  const [status, setStatus] = useState<FloorTrackingStatus>('searching');
  const [placementRequest, setPlacementRequest] = useState(0);
  const [canReposition, setCanReposition] = useState(true);
  const [canGiveCare, setCanGiveCare] = useState(false);
  const [careRequest, setCareRequest] = useState('');
  const careRequestId = useRef(0);
  const viewport = useRef({ width: 0, height: 0 });
  const careBottom = insets.bottom + 24;
  const sendCare = (action: 'drink' | 'eat', dx = 0, dy = 0) => {
    const { width, height } = viewport.current;
    if (width <= 0 || height <= 0) return;
    const dockOffset = (styles.careButton.width + styles.careActions.gap) / 2;
    const x = (width / 2 + (action === 'drink' ? -dockOffset : dockOffset) + dx) / width;
    const y = (height - careBottom - styles.careButton.height / 2 + dy) / height;
    const clamp = (value: number) => Math.max(0, Math.min(1, value));
    setCareRequest(`${++careRequestId.current}:${action}:${clamp(x)}:${clamp(y)}`);
  };
  const [showTraversalDebug, setShowTraversalDebug] = useState(false);
  const [hasSeenDog, setHasSeenDog] = useState(false);
  const [loadingDots, setLoadingDots] = useState(2);
  const [walkStartedAt, setWalkStartedAt] = useState<number | null>(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const walkRoute = useWalkRoute(isFocused && walkStartedAt !== null);
  const voice = useWalkVoice(
    isFocused && hasPermission && status === 'anchored' && !bagPurchaseVisible,
    route?.params?.petName ?? '',
  );

  useEffect(() => {
    if (voice.error) {
      Alert.alert('산책 음성 명령', voice.error);
    }
  }, [voice.error]);

  useEffect(() => {
    if (!isFocused || !hasPermission) {
      setCanGiveCare(false);
      setCareRequest('');
    }
  }, [isFocused, hasPermission]);

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
    setCanGiveCare(isFocused && next.status === 'anchored' && next.canGiveCare === true);
    if (next.cleanupRequestId && next.cleanupRequestId !== lastCleanupRequest.current) {
      lastCleanupRequest.current = next.cleanupRequestId;
      const consumed = useShopStore.getState().consume({ bags: 1 });
      setCleanupResponse(`${next.cleanupRequestId}:${consumed ? 1 : 0}`);
    }
    if (next.visible && next.onScreen) {
      setHasSeenDog(true);
    }
    if (isFocused && next.status === 'anchored' && next.visible && next.onScreen) {
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
    <View
      style={styles.screen}
      onLayout={({ nativeEvent }) => {
        viewport.current = nativeEvent.layout;
      }}
    >
      {hasPermission ? (
        <ArFloorView
          active={isFocused}
          placementRequest={placementRequest}
          showTraversalDebug={showTraversalDebug}
          collapsable={false}
          style={styles.camera}
          onTracking={onTracking}
          onWalkPose={event => walkRoute.onPose(event.nativeEvent)}
          compassRegion={walkRoute.mapPosition ? `${walkRoute.mapPosition.latitude.toFixed(1)},${walkRoute.mapPosition.longitude.toFixed(1)}` : undefined}
          bagAvailable={shop.ready && Date.now() < shop.expiresAt && (shop.inventory.bags ?? 0) > 0}
          cleanupResponse={cleanupResponse}
          onBagPurchase={openBagPurchase}
          actionRequest={voice.actionRequest}
          careRequest={careRequest}
        />
      ) : (
        <View style={styles.message}>
          <Text style={styles.messageText}>강아지와 산책하려면 카메라 권한이 필요합니다.</Text>
        </View>
      )}
      <View style={[styles.walkTimer, { top: headerHeight + 8 }]}>
        <Text accessibilityLabel={`산책 시간 ${walkTime}`} style={styles.walkTimerText}>
          {walkTime}
        </Text>
        <Text style={styles.walkTimerText}>
          {walkRoute.meters < 1000
            ? `${Math.round(walkRoute.meters)} m`
            : `${(walkRoute.meters / 1000).toFixed(2)} km`}
        </Text>
        <WalkRouteMap route={walkRoute} />
      </View>
      {isRecognizing && (
        <View pointerEvents="none" style={styles.loadingOverlay}>
          <ActivityIndicator color="#FFFFFF" size="small" />
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
        <View
          pointerEvents="box-none"
          style={[styles.displayControls, { top: headerHeight + 192 }]}
        >
          <Pressable
            accessibilityRole="switch"
            accessibilityLabel="이동 가능 영역 표시"
            accessibilityState={{ checked: showTraversalDebug }}
            onPress={() => setShowTraversalDebug(value => !value)}
            style={styles.areaToggle}
            hitSlop={4}
          >
            <Text style={styles.areaToggleLabel}>영역 표시</Text>
            <View style={[styles.toggleTrack, showTraversalDebug && styles.toggleTrackOn]}>
              <View style={[styles.toggleThumb, showTraversalDebug && styles.toggleThumbOn]} />
            </View>
          </Pressable>
        </View>
      )}
      {hasPermission && (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={voice.isListening ? '산책 음성 듣는 중' : '산책 음성 말하기'}
          accessibilityHint="한 번 누르면 한 문장을 듣고 자동으로 종료합니다"
          accessibilityState={{
            disabled:
              !isFocused ||
              status !== 'anchored' ||
              bagPurchaseVisible ||
              voice.isStarting ||
              voice.isListening,
            busy: voice.isStarting || voice.isListening,
          }}
          disabled={
            !isFocused ||
            status !== 'anchored' ||
            bagPurchaseVisible ||
            voice.isStarting ||
            voice.isListening
          }
          onPress={voice.startListening}
          style={[styles.voiceButton, { bottom: insets.bottom + 24 }]}
          hitSlop={4}
        >
          <VoiceMicrophone listening={voice.isListening} />
        </Pressable>
      )}
      {hasPermission && isFocused && canGiveCare && !bagPurchaseVisible && (
        <View style={[styles.careActions, { bottom: careBottom }]}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="물 주기"
            onPress={() => sendCare('drink')}
            style={styles.careButton}
          >
            <Image source={waterBowl} style={styles.careIcon} />
          </Pressable>
          <WalkTreat onThrow={(dx, dy) => sendCare('eat', dx, dy)} />
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
      {hasSeenDog && voice.isListening && (
        <View pointerEvents="none" style={[styles.voiceStatus, { bottom: insets.bottom + 90 }]}>
          {voice.message ? <VoiceGlowText text={voice.message} /> : <VoiceListeningDots />}
        </View>
      )}
      {bagPurchaseVisible && (
        <AppDialog
          title="배변봉투 구매"
          message={[
            bagProduct
              ? `${bagProduct.name} 1묶음 (${bagProduct.qty}${bagProduct.unit}) · ${money(
                  bagProduct.price,
                )}`
              : '배변봉투 상품 정보를 불러오지 못했어요.',
            `남은 예산 ${money(shop.budget - shop.spent)}`,
            '구매 금액은 모의 입양 예산에서 차감되고 보유 목록에 저장돼요.',
            bagPurchaseError,
          ]
            .filter(Boolean)
            .join('\n\n')}
          confirmLabel="구매하기"
          cancelLabel="취소"
          onConfirm={purchaseBags}
          onClose={() => setBagPurchaseVisible(false)}
        />
      )}
    </View>
  );
}
