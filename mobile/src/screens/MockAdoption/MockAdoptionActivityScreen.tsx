import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { type ElementRef, useCallback, useEffect, useRef, useState } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import {
  Alert,
  Animated,
  BackHandler,
  Image,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { RootStackParamList } from '../../app/navigation/types';
import BackIcon from '../../assets/icons/common/left-arrow.svg';
import { colors, typography } from '../../theme';
import { ApiClientError } from '../../services/api';
import {
  abandonSimulation,
  getSimulationAbandonErrorMessage,
  isSimulationAlreadyClosed,
} from '../../services/simulation/abandon';
import { SimulationCareAction } from '../../services/simulation/actions';
import {
  careRequest,
  getCareActionErrorMessage,
  performCareAction,
} from '../../services/simulation/care';
import { readCurrentActiveSimulation } from '../../services/simulation/activeSession';
import { invalidateSimulationReports } from '../../services/simulation/reports';
import { endMockAdoption, getActiveMockAdoption } from '../../services/storage/mockAdoptionStorage';
import { mockAdoptionPets } from './data';
import { RoomPet } from './RoomPet';
import { useToilet } from './useToilet';
import { TreatLauncher } from './TreatLauncher';
import { PetStatsDialog } from './PetStatsDialog';
import { useShopSession } from '../shop/useShopSession';
import { useShopStore } from '../../stores/shopStore';
import { consumeCareSupplies, findCareSupply } from '../../services/shop/consumption';
import { availableProducts } from '../../services/shop/catalog';
import { SuddenEventDialog } from './SuddenEventDialog';
import { VetVisitDialog } from './VetVisitDialog';
import {
  getVetVisit,
  SimulationEventStatus,
  SimulationTriggerContext,
  SimulationTriggerType,
  type SimulationEvent,
} from '../../services/simulation/events';

const BOWL_VISUAL_OFFSET = 70;
const PREVIOUS_ACTION_HEIGHT = 72;

type Props = NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;

export function MockAdoptionActivityScreen({ navigation, route }: Props) {
  const { status: shopStatus, session } = useShopSession(route.params.petId);
  const shop = useShopStore();
  const owns = (id: string) => shopStatus === 'ready' && (shop.inventory[id] ?? 0) > 0;
  const purchased = (id: string) =>
    shopStatus === 'ready' && shop.ownedItems.some(item => item.productId === id);
  const pet = mockAdoptionPets.find(item => item.id === route.params.petId);
  const activeSimulation = readCurrentActiveSimulation();
  const synced =
    activeSimulation && session?.sessionSeq === activeSimulation.sessionSeq
      ? activeSimulation
      : null;
  const toilet = useToilet(route.params.petId, pet?.category ?? 'dog');
  const petProducts = availableProducts(pet?.category ?? 'dog');
  const ownsServer = (category: string) =>
    petProducts.some(product => product.serverCategory === category && owns(product.id));
  const careItemSeq = (action: 'food' | 'treat' | 'clean' | 'bath' | 'play') =>
    pet ? findCareSupply(pet.category, action)?.itemSeq : undefined;
  const hasTreat = petProducts.some(p => p.category === '간식' && owns(p.id));
  const hasToy = petProducts.some(p => p.category === '놀이' && p.kind === 'durable' && owns(p.id));
  const hasFoodBowl = synced ? synced.equipment.foodBowl : owns('bowl') || ownsServer('FOOD_BOWL');
  const hasWaterBowl = synced
    ? synced.equipment.waterBowl
    : owns('waterBowl') || ownsServer('WATER_BOWL');
  const showLitter = synced
    ? synced.equipment.litterBox
    : pet?.category === 'dog'
    ? purchased('pads') || ownsServer('LITTER')
    : owns('litterBox') || ownsServer('LITTER_BOX');
  const showToy = synced ? synced.equipment.toy : hasToy;
  const [statsVisible, setStatsVisible] = useState(false);
  const [hospitalVisible, setHospitalVisible] = useState(false);
  const hospitalEvents = (shop.hospitalEvents ?? []).filter(
    event => getVetVisit(event) && Date.now() < Date.parse(event.expiresDttm),
  );
  const pendingEvent = hospitalEvents[0] ?? null;
  const [suddenEvent, setSuddenEvent] = useState<SimulationEvent | null>(null);
  useEffect(() => {
    const event = synced?.pendingEvent;
    if (
      shopStatus !== 'ready' ||
      !event ||
      event.status !== SimulationEventStatus.Pending ||
      !event.vetVisit
    ) {
      return;
    }
    useShopStore.getState().enqueueHospitalEvent(event);
  }, [shopStatus, synced]);
  const vetVisit = getVetVisit(pendingEvent);
  const walkPending = useRef(false);
  const [care, setCare] = useState<{
    kind: 'food' | 'water' | 'treat' | 'fetch';
    offset: { x: number; y: number };
  } | null>(null);
  const careBusy = useRef(false);
  const [toyGround, setToyGround] = useState<{ x: number; y: number } | null>(null);
  const [bathing, setBathing] = useState(false);
  const [bathReturning, setBathReturning] = useState(false);
  const bathingRef = useRef(false);
  const bathTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const bathScreenOpacity = useRef(new Animated.Value(1)).current;
  useEffect(() => {
    if (!bathReturning) {
      return;
    }
    const animation = Animated.timing(bathScreenOpacity, {
      toValue: 1,
      duration: 2000,
      useNativeDriver: true,
    });
    animation.start(({ finished }) => {
      if (!finished || !bathingRef.current) {
        return;
      }
      bathingRef.current = false;
      setBathReturning(false);
    });
    return () => animation.stop();
  }, [bathReturning, bathScreenOpacity]);
  useEffect(
    () => () => {
      clearTimeout(bathTimer.current);
      bathScreenOpacity.stopAnimation();
    },
    [bathScreenOpacity],
  );
  const [treatActive, setTreatActive] = useState(false);
  const [fullBowl, setFullBowl] = useState({ food: false, water: false });
  const streak = useRef({ kind: '', count: 0 });
  const [toast, setToast] = useState('');
  const [toastPosition, setToastPosition] = useState<'pet' | 'bottom'>('pet');
  const toastTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const fillScale = useRef(new Animated.Value(1)).current;
  const bowlViews = useRef<Partial<Record<'food' | 'water', ElementRef<typeof View> | null>>>({});
  const screenView = useRef<ElementRef<typeof View>>(null);
  useEffect(() => () => clearTimeout(toastTimer.current), []);
  const [returnSequence, setReturnSequence] = useState(0);
  const [roomVisible, setRoomVisible] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const leavingRef = useRef(false);
  const pendingNavigation = useRef<(() => void) | null>(null);
  const hideThenNavigate = useCallback((action: () => void) => {
    if (leavingRef.current || careBusy.current || bathingRef.current) {
      return;
    }
    leavingRef.current = true;
    pendingNavigation.current = action;
    setStatsVisible(false);
    setLeaving(true);
  }, []);
  const onPetHidden = useCallback(() => {
    const action = pendingNavigation.current;
    pendingNavigation.current = null;
    if (action) {
      setRoomVisible(false);
      action();
    }
  }, []);
  useFocusEffect(
    useCallback(() => {
      leavingRef.current = false;
      setLeaving(false);
      if (walkPending.current) {
        walkPending.current = false;
        setReturnSequence(value => value + 1);
      }
      setRoomVisible(true);
      const back = BackHandler.addEventListener('hardwareBackPress', () => {
        hideThenNavigate(() => navigation.goBack());
        return true;
      });
      return () => {
        back.remove();
        clearTimeout(bathTimer.current);
        bathScreenOpacity.stopAnimation();
        bathScreenOpacity.setValue(1);
        bathingRef.current = false;
        setBathing(false);
        setBathReturning(false);
        careBusy.current = false;
        setTreatActive(false);
        setCare(null);
        setToyGround(null);
        setFullBowl({ food: false, water: false });
        setRoomVisible(false);
        setStatsVisible(false);
        setHospitalVisible(false);
      };
    }, [hideThenNavigate, navigation, bathScreenOpacity]),
  );
  const [roomSize, setRoomSize] = useState({ width: 0, height: 0 });
  useEffect(() => {
    if (leaving && (!roomVisible || !pet || roomSize.width <= 0)) {
      onPetHidden();
    }
  }, [leaving, roomVisible, pet, roomSize.width, onPetHidden]);
  // Same cover transform as the background: anchor the model to the painted bed.
  const backgroundScale = Math.max(roomSize.width / 937, roomSize.height / 1678);
  // Expand the transparent viewport above the pet without moving its feet.
  const petWidth = 900 * backgroundScale;
  const petHeight = 900 * backgroundScale;
  const petFrame = {
    width: petWidth,
    height: petHeight,
    left: (roomSize.width - 937 * backgroundScale) / 2 + 535 * backgroundScale - petWidth / 2,
    top:
      (roomSize.height - 1678 * backgroundScale) / 2 +
      (1140 - 554 * 0.68 - (900 - 554)) * backgroundScale,
  };
  // Use the background's cover transform so the landing point stays beside the pot.
  const toyLanding = {
    x: (roomSize.width - 937 * backgroundScale) / 2 + 510 * backgroundScale,
    y: (roomSize.height - 1678 * backgroundScale) / 2 + 900 * backgroundScale,
  };
  const ending = useRef(false);
  const [busy, setBusy] = useState(false);
  const quit = async () => {
    if (ending.current) {
      return;
    }
    ending.current = true;
    setBusy(true);
    try {
      const active = await getActiveMockAdoption();
      if (active?.sessionSeq) {
        try {
          await abandonSimulation(active.sessionSeq);
          invalidateSimulationReports();
        } catch (error) {
          if (!isSimulationAlreadyClosed(error)) {
            throw error;
          }
          invalidateSimulationReports();
        }
      }
      await endMockAdoption();
      hideThenNavigate(() => navigation.navigate('Home', { tab: 'mockAdoption' }));
    } catch (caught) {
      Alert.alert(
        '모의입양',
        caught instanceof ApiClientError
          ? getSimulationAbandonErrorMessage(caught)
          : '진행 상태를 초기화하지 못했어요. 다시 시도해주세요.',
      );
    } finally {
      ending.current = false;
      setBusy(false);
    }
  };
  const showToast = (message: string, position: 'pet' | 'bottom' = 'pet') => {
    setToastPosition(position);
    setToast(message);
    clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(''), 2500);
  };
  const confirmTreatment = () => {
    setHospitalVisible(true);
  };
  const recordCare = async (actionCode: SimulationCareAction, itemSeq?: number) => {
    const request = careRequest(actionCode, itemSeq);
    const active = request ? await getActiveMockAdoption() : null;
    if (!request || !active?.sessionSeq) {
      return true as const;
    }
    try {
      await performCareAction(active.sessionSeq, request);
      return true as const;
    } catch (error) {
      return error instanceof ApiClientError
        ? getCareActionErrorMessage(error)
        : '돌봄을 진행하지 못했어요. 다시 시도해주세요.';
    }
  };
  const resolveTreatment = async (pay: boolean) => {
    if (!pendingEvent) {
      return;
    }
    if (pay) {
      const recorded = await recordCare(SimulationCareAction.VetVisit);
      if (recorded !== true) {
        Alert.alert('동물병원', recorded);
        return;
      }
    }
    if (!shop.resolveHospitalEvent(pendingEvent.occurrenceSeq, pay)) {
      Alert.alert(
        '동물병원',
        useShopStore.getState().error ?? '진료를 처리하지 못했어요. 다시 시도해주세요.',
      );
      return;
    }
    if (pay) {
      showToast(
        `예산이 ${(pendingEvent.vetVisit?.cost ?? 0).toLocaleString('ko-KR')}원 쓰였습니다.`,
        'bottom',
      );
    }
    if (hospitalEvents.length <= 1) {
      setHospitalVisible(false);
    }
  };
  const startBath = async () => {
    if (careBusy.current || bathingRef.current || leaving || busy || !roomVisible) {
      return;
    }
    const recorded = await recordCare(SimulationCareAction.Bathe, careItemSeq('bath'));
    if (recorded !== true) {
      showToast(recorded);
      return;
    }
    bathingRef.current = true;
    setBathing(true);
    setStatsVisible(false);
    setToast('');
    clearTimeout(toastTimer.current);
    bathTimer.current = setTimeout(() => {
      Animated.timing(bathScreenOpacity, {
        toValue: 0,
        duration: 2000,
        useNativeDriver: true,
      }).start(({ finished }) => {
        if (!finished || !bathingRef.current) {
          return;
        }
        setBathing(false);
        setBathReturning(true);
      });
    }, 3000);
  };
  const provide = async (kind: 'food' | 'water') => {
    if (careBusy.current || bathingRef.current || leaving || busy || !pet) {
      return;
    }
    const nextStreak = { kind, count: streak.current.kind === kind ? streak.current.count + 1 : 1 };
    if (nextStreak.count >= 5) {
      showToast(kind === 'food' ? '지금은 배불러요' : '지금은 목마르지 않아요');
      return;
    }
    const recorded = await recordCare(
      kind === 'food' ? SimulationCareAction.Feed : SimulationCareAction.Water,
      kind === 'food' ? careItemSeq('food') : undefined,
    );
    if (recorded !== true) {
      showToast(recorded);
      return;
    }
    if (kind === 'food' && !consumeCareSupplies(pet.id, pet.category, 'food')) {
      showToast(useShopStore.getState().error || '사료를 사용할 수 없어요.');
      return;
    }
    streak.current = nextStreak;
    setFullBowl(value => ({ ...value, [kind]: true }));
    fillScale.setValue(0.8);
    Animated.spring(fillScale, { toValue: 1, useNativeDriver: true }).start();
    careBusy.current = true;
    bowlViews.current[kind]?.measureInWindow((x, y, width, height) => {
      screenView.current?.measureInWindow((screenX, screenY) => {
        setCare({
          kind,
          offset: {
            // At the front diagonal eating pose the muzzle is left and above
            // the feet. Align the muzzle, rather than the feet, to the bowl.
            x: x - screenX + width / 2 + petWidth * 0.06 + 22 - (petFrame.left + petWidth / 2),
            y:
              y -
              screenY -
              BOWL_VISUAL_OFFSET +
              height * 0.55 +
              petHeight * 0.06 -
              (petFrame.top + petHeight * (1 - 177.28 / 900)),
          },
        });
      });
    });
  };
  return (
    <View
      ref={screenView}
      style={styles.container}
      onLayout={({ nativeEvent: { layout } }) => {
        setRoomSize({ width: layout.width, height: layout.height });
      }}
    >
      <Image
        source={
          bathing
            ? require('../../assets/images/mock-adoption/img_dog_showerbooth.png')
            : require('../../assets/images/mock-adoption/img_home_background.png')
        }
        resizeMode="cover"
        style={styles.background}
      />
      {toyGround && !bathing ? (
        <Image
          pointerEvents="none"
          accessibilityLabel="바닥에 놓인 강아지 장난감"
          source={require('../../assets/images/mock-adoption/img_dog_toy.png')}
          resizeMode="contain"
          style={[styles.groundToy, { left: toyGround.x - 20, top: toyGround.y - 20 }]}
        />
      ) : null}
      {roomVisible && pet && roomSize.width > 0 ? (
        <RoomPet
          key={`${pet.id}-${returnSequence}`}
          petId={pet.id}
          style={[styles.pet, petFrame, bathing && { top: petFrame.top + 30 }]}
          returnSequence={returnSequence}
          entryOffset={{ x: roomSize.width * 0.55, y: roomSize.height * 0.3 }}
          bathing={bathing}
          sceneOpacity={bathScreenOpacity}
          care={care}
          onConsumed={() => {
            if (care && (care.kind === 'food' || care.kind === 'water')) {
              setFullBowl(value => ({ ...value, [care.kind]: false }));
            }
          }}
          onFetchPhase={phase => {
            if (phase === 'carry' || phase === 'cancel') {
              setToyGround(null);
            }
            if (phase === 'drop') {
              setToyGround({ x: petFrame.left + petWidth / 2, y: petFrame.top + petHeight * 0.79 });
              showToast('♥');
            }
          }}
          onCareComplete={() => {
            careBusy.current = false;
            setCare(null);
          }}
          onPress={() => {
            if (!careBusy.current && !bathingRef.current) {
              setStatsVisible(true);
            }
          }}
          leaving={leaving}
          onHidden={onPetHidden}
        />
      ) : null}
      {pet && !bathing && showLitter ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={
            pet.category === 'dog'
              ? toilet.full
                ? '가득 찬 배변 패드'
                : '빈 배변 패드'
              : toilet.full
              ? '가득 찬 고양이 화장실'
              : '빈 고양이 화장실'
          }
          disabled={!toilet.full || !!care || leaving || busy || bathReturning}
          onPress={async () => {
            if (careBusy.current || bathingRef.current) {
              return;
            }
            try {
              const recorded = await recordCare(
                SimulationCareAction.CleanLitter,
                careItemSeq('clean'),
              );
              if (recorded !== true) {
                showToast(recorded);
                return;
              }
              if (await toilet.clean(() => consumeCareSupplies(pet.id, pet.category, 'clean'))) {
                showToast('깨끗해져서 좋아요!');
              } else if (useShopStore.getState().error) {
                showToast(useShopStore.getState().error!);
              }
            } catch {
              showToast('청소 상태를 저장하지 못했어요. 다시 시도해주세요.');
            }
          }}
          style={styles.toilet}
        >
          <Image
            source={
              pet.category === 'dog'
                ? toilet.full
                  ? require('../../assets/images/mock-adoption/img_dog_full_toilet.png')
                  : require('../../assets/images/mock-adoption/img_dog_empty_toilet.png')
                : toilet.full
                ? require('../../assets/images/mock-adoption/img_cat_full_toilet.png')
                : require('../../assets/images/mock-adoption/img_cat_empty_toilet.png')
            }
            resizeMode="contain"
            style={styles.toiletImage}
          />
        </Pressable>
      ) : null}
      <SafeAreaView
        accessibilityElementsHidden={bathing || bathReturning}
        importantForAccessibility={bathing || bathReturning ? 'no-hide-descendants' : 'auto'}
        style={[styles.safeArea, bathing && styles.bathHidden]}
        pointerEvents={bathing || bathReturning ? 'none' : 'box-none'}
      >
        <View style={styles.header}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="뒤로가기"
            disabled={leaving}
            onPress={() => hideThenNavigate(() => navigation.goBack())}
            style={({ pressed }) => [styles.iconButton, pressed && styles.pressed]}
          >
            <BackIcon width={24} height={24} />
          </Pressable>
          <View style={styles.actionColumns}>
            <View style={styles.action}>
              {__DEV__ && pet ? (
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel="돌발 이벤트 팝업 미리보기"
                  disabled={busy || leaving || !!care || treatActive}
                  onPress={() => {
                    setStatsVisible(false);
                    const preview: SimulationEvent = {
                      occurrenceSeq: Date.now(),
                      scenario: {
                        scenarioSeq: 5,
                        title: '축 처져 있어요',
                        description:
                          '밥을 잘 안 먹고 구석에서 움직이지 않아요. 어떻게 해줘야 할까요?',
                        triggerContext: SimulationTriggerContext.Home,
                        triggerType: SimulationTriggerType.Condition,
                      },
                      vetVisit: { cost: 120000 },
                      walkSeq: null,
                      status: SimulationEventStatus.Pending,
                      occurredDttm: new Date().toISOString(),
                      expiresDttm: new Date(Date.now() + 21600000).toISOString(),
                      resolvedDttm: null,
                      resolvedActionCode: null,
                      resultText: null,
                    };
                    shop.enqueueHospitalEvent(preview);
                    setSuddenEvent(preview);
                  }}
                  style={({ pressed }) => [styles.previewButton, pressed && styles.pressed]}
                >
                  <Text style={styles.previewLabel}>돌발 팝업 미리보기</Text>
                </Pressable>
              ) : null}
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="AR 산책하기"
                style={({ pressed }) => [styles.actionButton, pressed && styles.pressed]}
                disabled={busy || leaving}
                onPress={() => {
                  hideThenNavigate(() => {
                    walkPending.current = true;
                    navigation.navigate('ArWalk', { petName: session?.petName ?? '' });
                  });
                }}
              >
                <Image
                  source={require('../../assets/images/mock-adoption/img_dog_paw.png')}
                  resizeMode="contain"
                  style={styles.actionImage}
                />
                <Text style={styles.actionCaption}>산책가기</Text>
              </Pressable>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="목욕하기"
                disabled={busy || leaving || !!care || treatActive}
                onPress={startBath}
                style={({ pressed }) => [styles.actionButton, pressed && styles.pressed]}
              >
                <Image
                  source={require('../../assets/images/mock-adoption/img_shower.png')}
                  resizeMode="contain"
                  style={styles.actionImage}
                />
                <Text style={styles.actionCaption}>목욕하기</Text>
              </Pressable>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="모의입양 그만두기"
                accessibilityState={{ disabled: busy || leaving }}
                disabled={busy || leaving}
                onPress={quit}
                style={({ pressed }) => [styles.quit, pressed && styles.pressed]}
              >
                <Text style={styles.quitLabel}>모의입양 그만두기</Text>
              </Pressable>
            </View>
            <View style={styles.rightActions}>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="상점 열기"
                disabled={busy || leaving}
                onPress={() =>
                  hideThenNavigate(() =>
                    navigation.navigate('PetShop', { petId: route.params.petId }),
                  )
                }
                style={({ pressed }) => [styles.actionButton, pressed && styles.pressed]}
              >
                <Image
                  source={require('../../assets/images/mock-adoption/img_shop.png')}
                  resizeMode="contain"
                  style={styles.actionImage}
                />
                <Text style={styles.actionCaption}>쇼핑하기</Text>
              </Pressable>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="동물병원"
                accessibilityValue={{
                  text: vetVisit !== null ? '치료 필요' : '치료 대기 없음',
                }}
                disabled={busy || leaving || !!care || treatActive || !vetVisit}
                onPress={confirmTreatment}
                style={({ pressed }) => [styles.actionButton, pressed && styles.pressed]}
              >
                <Image
                  source={
                    vetVisit !== null
                      ? require('../../assets/images/mock-adoption/img_veterinary_hospital_with_red_dot.png')
                      : require('../../assets/images/mock-adoption/img_veterinary_hospital.png')
                  }
                  resizeMode="contain"
                  style={styles.actionImage}
                />
                <Text style={styles.actionCaption}>동물병원</Text>
              </Pressable>
            </View>
          </View>
        </View>
        <View style={styles.room} pointerEvents="box-none">
          <View style={styles.bowls}>
            {(['food', 'water'] as const).map(kind =>
              (kind === 'food' ? hasFoodBowl : hasWaterBowl) ? (
                <Pressable
                  key={kind}
                  accessibilityRole="button"
                  accessibilityLabel={kind === 'food' ? '사료 그릇' : '물그릇'}
                  accessibilityValue={{ text: fullBowl[kind] ? '가득 찬 그릇' : '빈 그릇' }}
                  disabled={!!care || leaving || busy}
                  onPress={() => provide(kind)}
                  style={styles.bowl}
                >
                  <View
                    collapsable={false}
                    ref={view => {
                      bowlViews.current[kind] = view;
                    }}
                    style={styles.bowlContent}
                  >
                    <Animated.Image
                      source={
                        kind === 'food'
                          ? fullBowl[kind]
                            ? require('../../assets/images/mock-adoption/img_full_feetstuff_bowl.png')
                            : require('../../assets/images/mock-adoption/img_empty_feetstuff_bowl.png')
                          : fullBowl[kind]
                          ? require('../../assets/images/mock-adoption/img_full_water_bowl.png')
                          : require('../../assets/images/mock-adoption/img_empty_water_bowl.png')
                      }
                      resizeMode="contain"
                      style={{
                        ...styles.bowlImage,
                        transform: [{ scale: fullBowl[kind] ? fillScale : 1 }],
                      }}
                    />
                  </View>
                </Pressable>
              ) : (
                <View key={kind} pointerEvents="none" style={styles.bowl} />
              ),
            )}
          </View>
        </View>
      </SafeAreaView>
      {care || treatActive || bathing || bathReturning ? (
        <View style={StyleSheet.absoluteFill} onStartShouldSetResponder={() => true} />
      ) : null}
      {roomVisible &&
      pet &&
      !leaving &&
      !bathing &&
      (hasTreat || treatActive || care?.kind === 'treat') ? (
        <TreatLauncher
          dockHidden={!!care || treatActive || bathReturning}
          category={pet.category}
          width={roomSize.width}
          height={roomSize.height}
          target={{ x: petFrame.left + petWidth / 2, y: petFrame.top + petHeight * 0.62 }}
          disabled={
            !!care || busy || statsVisible || bathReturning || treatActive || roomSize.width <= 0
          }
          onBusyChange={active => {
            careBusy.current = active;
            setTreatActive(active);
          }}
          onLanded={async () => {
            setTreatActive(false);
            const recorded = await recordCare(SimulationCareAction.Snack, careItemSeq('treat'));
            if (recorded !== true) {
              careBusy.current = false;
              showToast(recorded);
              return;
            }
            if (!consumeCareSupplies(pet.id, pet.category, 'treat')) {
              careBusy.current = false;
              showToast(useShopStore.getState().error || '간식을 사용할 수 없어요.');
              return;
            }
            setCare({ kind: 'treat', offset: { x: 0, y: 0 } });
            showToast('♥');
          }}
        />
      ) : null}
      {roomVisible && pet?.category === 'dog' && showToy && !leaving && !bathing ? (
        <TreatLauncher
          dockHidden={!!care || treatActive || bathReturning}
          item="toy"
          category="dog"
          bottomOffset={92}
          width={roomSize.width}
          height={roomSize.height}
          target={toyLanding}
          disabled={
            !!care || busy || statsVisible || bathReturning || treatActive || roomSize.width <= 0
          }
          onBusyChange={active => {
            careBusy.current = active;
            setTreatActive(active);
          }}
          onLanded={async point => {
            const recorded = await recordCare(SimulationCareAction.Play, careItemSeq('play'));
            if (recorded !== true) {
              careBusy.current = false;
              setTreatActive(false);
              showToast(recorded);
              return;
            }
            setTreatActive(false);
            setToyGround(point);
            setCare({
              kind: 'fetch',
              offset: {
                x: point.x - (petFrame.left + petWidth / 2),
                y: point.y - (petFrame.top + petHeight * 0.79) + petHeight * 0.05,
              },
            });
          }}
        />
      ) : null}
      {toast && !bathing ? (
        <View
          pointerEvents="none"
          style={[
            styles.toastAnchor,
            {
              ...(toastPosition === 'bottom'
                ? { bottom: 24, left: 24, right: 24, alignItems: 'center' as const }
                : {
                    top: Math.max(16, petFrame.top + petHeight * 0.38 - 48),
                    left: Math.max(
                      16,
                      Math.min(
                        roomSize.width * 0.4 - 16,
                        petFrame.left + petWidth / 2 - roomSize.width * 0.3,
                      ),
                    ),
                    width: roomSize.width * 0.6,
                  }),
            },
          ]}
        >
          <View style={[styles.toast, toast === '♥' && styles.heartToastBackground]}>
            <Text
              accessibilityLiveRegion="polite"
              style={[styles.toastText, toast === '♥' && styles.heartToast]}
            >
              {toast}
            </Text>
          </View>
        </View>
      ) : null}
      {bathing || bathReturning ? (
        <Animated.View
          pointerEvents="none"
          style={[
            styles.bathFade,
            {
              opacity: bathScreenOpacity.interpolate({ inputRange: [0, 1], outputRange: [1, 0] }),
            },
          ]}
        />
      ) : null}
      {pet && pendingEvent && hospitalVisible ? (
        <VetVisitDialog
          key={pendingEvent.occurrenceSeq}
          event={pendingEvent}
          remaining={hospitalEvents.length}
          toast={toastPosition === 'bottom' ? toast : undefined}
          onClose={() => setHospitalVisible(false)}
          onPay={() => resolveTreatment(true)}
          onIgnore={() => resolveTreatment(false)}
        />
      ) : null}
      {pet && suddenEvent ? (
        <SuddenEventDialog
          event={suddenEvent}
          image={pet.image}
          petName={session?.petName ?? pet.name}
          onClose={() => setSuddenEvent(null)}
        />
      ) : null}
      {pet ? (
        <PetStatsDialog
          visible={statsVisible}
          petName={session?.petName ?? pet.name}
          stats={synced?.stats}
          onClose={() => setStatsVisible(false)}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  previewButton: {
    backgroundColor: '#FFFFFF',
    borderColor: '#68BF65',
    borderWidth: 1,
    borderRadius: 12,
    padding: 10,
    marginBottom: 8,
  },
  previewLabel: { ...typography.captionSemiBold12, color: '#327A30' },
  container: { flex: 1, backgroundColor: colors.white },
  background: { position: 'absolute', width: '100%', height: '100%' },
  groundToy: { position: 'absolute', width: 40, height: 40 },
  pet: { position: 'absolute' },
  toilet: { position: 'absolute', right: '3%', top: '47%', width: '36%', height: '13%' },
  toiletImage: { width: '100%', height: '100%' },
  safeArea: { flex: 1 },
  bathHidden: { opacity: 0 },
  bathFade: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: '#000000',
  },
  header: {
    gap: 12,
    paddingHorizontal: 16,
    paddingTop: 8,
  },
  iconButton: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center' },
  room: { flex: 1 },
  bowls: {
    position: 'absolute',
    left: 16,
    bottom: 24 + PREVIOUS_ACTION_HEIGHT,
    transform: [{ translateY: BOWL_VISUAL_OFFSET }],
    flexDirection: 'row',
    width: '44%',
    maxWidth: 220,
    gap: 4,
  },
  bowl: { flex: 1, aspectRatio: 1 },
  bowlContent: { flex: 1 },
  bowlImage: { width: '100%', height: '100%' },
  quit: { justifyContent: 'center', minHeight: 48, paddingHorizontal: 4 },
  quitLabel: { ...typography.bodyRegular14, color: colors.gray700 },
  actionColumns: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  actionButton: { width: 60, alignItems: 'center', gap: 4 },
  actionImage: { width: 60, height: 60 },
  actionCaption: { ...typography.captionRegular10, color: colors.gray700, textAlign: 'center' },
  rightActions: { width: 60, alignItems: 'center', gap: 16 },
  action: {
    width: 180,
    alignItems: 'flex-start',
    gap: 16,
  },
  toastAnchor: {
    position: 'absolute',
    alignItems: 'center',
  },
  toast: {
    maxWidth: '100%',
    alignItems: 'center',
    backgroundColor: 'rgba(51, 51, 51, 0.5)',
    borderRadius: 20,
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  toastText: { color: colors.white, ...typography.bodyRegular14, textAlign: 'center' },
  heartToast: { color: '#FF6B8A', fontSize: 32, lineHeight: 40 },
  heartToastBackground: { backgroundColor: 'transparent' },
  pressed: { opacity: 0.7 },
});
