import { useHeaderHeight } from '@react-navigation/elements';
import { useIsFocused, useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
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
import { availableProducts, findProduct, money } from '../../services/shop/catalog';
import { findCareSupply } from '../../services/shop/consumption';
import { ApiClientError } from '../../services/api';
import { SimulationWalkAction } from '../../services/simulation/actions';
import {
  getPurchaseErrorMessage,
  purchaseSimulationItems,
} from '../../services/simulation/purchase';
import { invalidateSimulationTransactions } from '../../services/simulation/transactions';
import type { SimulationEvent } from '../../services/simulation/events';
import {
  getSimulationWalkActionErrorMessage,
  getSimulationWalkEndErrorMessage,
  getSimulationWalkStartErrorMessage,
  isFinishedWalkError,
  isWalkPoopEvent,
  performSimulationWalkAction,
  sendSimulationWalkHeartbeat,
  startSimulationWalk,
} from '../../services/simulation/walk';
import { endActiveSimulationWalk } from '../../services/simulation/walkLifecycle';
import {
  getActiveMockAdoption,
  saveMockAdoptionWalk,
} from '../../services/storage/mockAdoptionStorage';
import { mockAdoptionPets } from '../MockAdoption/data';
import { SuddenEventDialog } from '../MockAdoption/SuddenEventDialog';
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

function walkBagProduct() {
  return (
    availableProducts('dog').find(product => product.serverCategory === 'POOP_BAG') ??
    findProduct('bags')
  );
}

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
        if (dy <= -40) {
          latestThrow.current(dx, dy);
        } else {
          reset();
        }
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
        if (nativeEvent.actionName === 'activate') {
          onThrow(0, -60);
        }
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
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList, 'ArWalk'>>();
  const shop = useShopStore();
  const endingWalk = useRef(false);
  const [ending, setEnding] = useState(false);
  const walkStart = useRef<Promise<void>>(Promise.resolve());
  const [bagPurchaseVisible, setBagPurchaseVisible] = useState(false);
  const [bagPurchaseError, setBagPurchaseError] = useState('');
  const purchasingBags = useRef(false);
  const bagProduct = walkBagProduct();
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
    const finish = (error: string | null) => {
      if (error) {
        purchasingBags.current = false;
        setBagPurchaseError(error);
        return;
      }
      setBagPurchaseVisible(false);
    };
    const product = walkBagProduct();
    const state = useShopStore.getState();
    if (!state.ready) {
      finish('쇼핑 정보를 불러오는 중이에요.');
      return;
    }
    if (Date.now() >= state.expiresAt) {
      finish('모의 입양 기간이 종료되었어요.');
      return;
    }
    if (!product) {
      finish('배변봉투 상품 정보를 불러오지 못했어요.');
      return;
    }
    if (state.budget - state.spent < product.price) {
      finish('남은 예산이 부족해요.');
      return;
    }
    getActiveMockAdoption()
      .then(active => {
        if (!active?.sessionSeq || !product.itemSeq) {
          finish(useShopStore.getState().buyWalkBags());
          return undefined;
        }
        return purchaseSimulationItems(active.sessionSeq, [
          { itemSeq: product.itemSeq, quantity: 1 },
        ]).then(result => {
          invalidateSimulationTransactions(active.sessionSeq);
          const line = result.purchases[0];
          finish(
            line
              ? useShopStore.getState().applyServerPurchase(product.id, line)
              : '구매하지 못했어요. 다시 시도해주세요.',
          );
        });
      })
      .catch(error => {
        finish(
          error instanceof ApiClientError
            ? getPurchaseErrorMessage(error)
            : '구매하지 못했어요. 다시 시도해주세요.',
        );
      });
  };
  const consumeWalkBag = () => {
    const product = walkBagProduct();
    return product ? useShopStore.getState().consume({ [product.id]: 1 }) : false;
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
  const [walkEvent, setWalkEvent] = useState<SimulationEvent | null>(null);
  const serverWalk = useRef<{ sessionSeq: number; walkSeq: number } | null>(null);
  const dismissedWalkEvents = useRef(new Set<number>());
  const poopEventRef = useRef<SimulationEvent | null>(null);
  const closeResolvedWalkEvent = (occurrenceSeq?: number) => {
    if (!occurrenceSeq) {
      return;
    }
    dismissedWalkEvents.current.add(occurrenceSeq);
    if (poopEventRef.current?.occurrenceSeq === occurrenceSeq) {
      poopEventRef.current = null;
    }
    setWalkEvent(current => (current?.occurrenceSeq === occurrenceSeq ? null : current));
  };
  const sendCare = (action: 'drink' | 'eat', dx = 0, dy = 0) => {
    const { width, height } = viewport.current;
    if (width <= 0 || height <= 0) {
      return;
    }
    const dockOffset = (styles.careButton.width + styles.careActions.gap) / 2;
    const x = (width / 2 + (action === 'drink' ? -dockOffset : dockOffset) + dx) / width;
    const y = (height - careBottom - styles.careButton.height / 2 + dy) / height;
    const clamp = (value: number) => Math.max(0, Math.min(1, value));
    const place = () => {
      setCareRequest(`${++careRequestId.current}:${action}:${clamp(x)}:${clamp(y)}`);
    };
    const remote = serverWalk.current;
    if (!remote) {
      place();
      return;
    }
    if (action === 'drink') {
      performSimulationWalkAction(remote.sessionSeq, remote.walkSeq, {
        actionCode: SimulationWalkAction.GiveWater,
      })
        .then(result => {
          closeResolvedWalkEvent(result.resolvedEvent?.occurrenceSeq);
          place();
        })
        .catch(error => {
          Alert.alert('산책', getSimulationWalkActionErrorMessage(error));
        });
      return;
    }
    const snack = findCareSupply('dog', 'treat');
    if (!snack?.itemSeq) {
      Alert.alert('산책', '간식이 필요해요!');
      return;
    }
    performSimulationWalkAction(remote.sessionSeq, remote.walkSeq, {
      actionCode: SimulationWalkAction.GiveSnack,
      itemSeq: snack.itemSeq,
    })
      .then(result => {
        useShopStore.getState().consume({ [snack.id]: 1 });
        closeResolvedWalkEvent(result.resolvedEvent?.occurrenceSeq);
        place();
      })
      .catch(error => {
        Alert.alert('산책', getSimulationWalkActionErrorMessage(error));
      });
  };
  const [showTraversalDebug, setShowTraversalDebug] = useState(false);
  const [hasSeenDog, setHasSeenDog] = useState(false);
  const [loadingDots, setLoadingDots] = useState(2);
  const [walkStartedAt, setWalkStartedAt] = useState<number | null>(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [poopRequest, setPoopRequest] = useState(0);
  const [walkPetId, setWalkPetId] = useState<string | null>(null);
  const walkPet = mockAdoptionPets.find(pet => pet.id === walkPetId);
  const serverWalkRequested = useRef(false);
  const armedPoop = useRef<number | null>(null);
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
    if (walkStartedAt === null || serverWalkRequested.current) {
      return;
    }
    serverWalkRequested.current = true;
    let mounted = true;
    let timer: ReturnType<typeof setInterval> | undefined;
    const starting = (async () => {
      const active = await getActiveMockAdoption();
      if (!mounted || !active?.sessionSeq) {
        return;
      }
      setWalkPetId(active.petId);
      let walkSeq = active.walkSeq;
      let intervalSeconds = 60;
      if (!walkSeq) {
        const started = await startSimulationWalk(active.sessionSeq);
        if (!mounted) {
          return;
        }
        await saveMockAdoptionWalk(started.walkSeq);
        walkSeq = started.walkSeq;
        intervalSeconds = started.heartbeatIntervalSeconds;
      }
      const sessionSeq = active.sessionSeq;
      serverWalk.current = { sessionSeq, walkSeq };
      const beat = async () => {
        try {
          const result = await sendSimulationWalkHeartbeat(sessionSeq, walkSeq);
          if (!mounted) {
            return;
          }
          const event = result.newEvent ?? result.pendingEvent;
          if (!event || dismissedWalkEvents.current.has(event.occurrenceSeq)) {
            return;
          }
          setWalkEvent(event);
          if (isWalkPoopEvent(event) && armedPoop.current !== event.occurrenceSeq) {
            armedPoop.current = event.occurrenceSeq;
            poopEventRef.current = event;
            setPoopRequest(value => value + 1);
          }
        } catch (error) {
          if (mounted && isFinishedWalkError(error)) {
            if (timer) {
              clearInterval(timer);
            }
            Alert.alert('산책', getSimulationWalkEndErrorMessage(error));
          }
        }
      };
      if (!mounted) {
        return;
      }
      timer = setInterval(() => {
        beat().catch(() => undefined);
      }, intervalSeconds * 1000);
    })().catch(error => {
      if (mounted) {
        Alert.alert('산책', getSimulationWalkStartErrorMessage(error));
      }
    });
    walkStart.current = starting;
    return () => {
      mounted = false;
      if (timer) {
        clearInterval(timer);
      }
    };
  }, [walkStartedAt]);

  useEffect(() => {
    return navigation.addListener('beforeRemove', event => {
      event.preventDefault();
      if (endingWalk.current) {
        return;
      }
      endingWalk.current = true;
      setEnding(true);
      walkStart.current
        .then(() => endActiveSimulationWalk())
        .then(() => {
          navigation.dispatch(event.data.action);
        })
        .catch(error => {
          endingWalk.current = false;
          setEnding(false);
          Alert.alert('산책', getSimulationWalkEndErrorMessage(error));
        });
    });
  }, [navigation]);

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
      const requestId = next.cleanupRequestId;
      const respond = (consumed: boolean) => {
        setCleanupResponse(`${requestId}:${consumed ? 1 : 0}`);
      };
      const poopEvent = poopEventRef.current;
      if (!poopEvent) {
        respond(consumeWalkBag());
      } else {
        getActiveMockAdoption()
          .then(active => {
            if (!active?.sessionSeq || !active.walkSeq) {
              respond(consumeWalkBag());
              return undefined;
            }
            return performSimulationWalkAction(active.sessionSeq, active.walkSeq, {
              actionCode: SimulationWalkAction.CleanUpPoop,
            }).then(result => {
              closeResolvedWalkEvent(
                result.resolvedEvent?.occurrenceSeq ?? poopEvent.occurrenceSeq,
              );
              respond(consumeWalkBag());
            });
          })
          .catch(error => {
            respond(false);
            Alert.alert('산책', getSimulationWalkActionErrorMessage(error));
          });
      }
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
          compassRegion={
            walkRoute.mapPosition
              ? `${walkRoute.mapPosition.latitude.toFixed(
                  1,
                )},${walkRoute.mapPosition.longitude.toFixed(1)}`
              : undefined
          }
          bagAvailable={
            shop.ready &&
            Date.now() < shop.expiresAt &&
            !!bagProduct &&
            (shop.inventory[bagProduct.id] ?? 0) > 0
          }
          cleanupResponse={cleanupResponse}
          poopRequest={poopRequest}
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
      {walkPet && walkEvent ? (
        <SuddenEventDialog
          event={walkEvent}
          image={walkPet.image}
          petName={route?.params?.petName || walkPet.name}
          onClose={() => {
            dismissedWalkEvents.current.add(walkEvent.occurrenceSeq);
            setWalkEvent(null);
          }}
        />
      ) : null}
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
      {ending ? (
        <View accessibilityLabel="산책 종료 중" style={styles.endingOverlay}>
          <ActivityIndicator color="#FFFFFF" size="large" />
          <Text style={styles.loadingText}>산책을 종료하는 중이에요.</Text>
        </View>
      ) : null}
    </View>
  );
}
