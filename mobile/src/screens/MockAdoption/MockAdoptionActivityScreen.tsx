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
import { endMockAdoption } from '../../services/storage/mockAdoptionStorage';
import { mockAdoptionPets } from './data';
import { RoomPet } from './RoomPet';
import { useToilet } from './useToilet';
import { PetStatsDialog, type PetStats } from './PetStatsDialog';

// Temporary preview values until the server provides the selected pet's stats.
const mockPetStats: PetStats = {
  hunger: 75,
  hydration: 18,
  clean: 65,
  friendship: 82,
  stress: 8,
  health: 92,
  activity: 45,
};

const BOWL_VISUAL_OFFSET = 70;
const PREVIOUS_ACTION_HEIGHT = 72;

type Props = NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;

export function MockAdoptionActivityScreen({ navigation, route }: Props) {
  const pet = mockAdoptionPets.find(item => item.id === route.params.petId);
  const toilet = useToilet(route.params.petId, pet?.category ?? 'dog');
  const [statsVisible, setStatsVisible] = useState(false);
  const walkPending = useRef(false);
  const [care, setCare] = useState<{
    kind: 'food' | 'water';
    offset: { x: number; y: number };
  } | null>(null);
  const careBusy = useRef(false);
  const [fullBowl, setFullBowl] = useState({ food: false, water: false });
  const streak = useRef({ kind: '', count: 0 });
  const [toast, setToast] = useState('');
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
    if (leavingRef.current || careBusy.current) {
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
        careBusy.current = false;
        setCare(null);
        setFullBowl({ food: false, water: false });
        setRoomVisible(false);
        setStatsVisible(false);
      };
    }, [hideThenNavigate, navigation]),
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
  const ending = useRef(false);
  const [busy, setBusy] = useState(false);
  const quit = async () => {
    if (ending.current) {
      return;
    }
    ending.current = true;
    setBusy(true);
    try {
      await endMockAdoption();
      hideThenNavigate(() => navigation.replace('MockAdoptionSelection'));
    } catch {
      Alert.alert('모의입양', '진행 상태를 초기화하지 못했어요. 다시 시도해주세요.');
    } finally {
      ending.current = false;
      setBusy(false);
    }
  };
  const showToast = (message: string) => {
    setToast(message);
    clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(''), 2500);
  };
  const provide = (kind: 'food' | 'water') => {
    if (careBusy.current || leaving || busy || !pet) {
      return;
    }
    streak.current = { kind, count: streak.current.kind === kind ? streak.current.count + 1 : 1 };
    setFullBowl(value => ({ ...value, [kind]: true }));
    fillScale.setValue(0.8);
    Animated.spring(fillScale, { toValue: 1, useNativeDriver: true }).start();
    if (streak.current.count >= 5) {
      showToast(kind === 'food' ? '지금은 배불러요' : '지금은 목마르지 않아요');
      return;
    }
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
        source={require('../../assets/images/mock-adoption/img_home_background.png')}
        resizeMode="cover"
        style={styles.background}
      />
      {roomVisible && pet && roomSize.width > 0 ? (
        <RoomPet
          key={`${pet.id}-${returnSequence}`}
          petId={pet.id}
          style={[styles.pet, petFrame]}
          returnSequence={returnSequence}
          entryOffset={{ x: roomSize.width * 0.55, y: roomSize.height * 0.3 }}
          care={care}
          onConsumed={() => {
            if (care) {
              setFullBowl(value => ({ ...value, [care.kind]: false }));
            }
          }}
          onCareComplete={() => {
            careBusy.current = false;
            setCare(null);
          }}
          onPress={() => {
            if (!careBusy.current) {
              setStatsVisible(true);
            }
          }}
          leaving={leaving}
          onHidden={onPetHidden}
        />
      ) : null}
      {pet ? (
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
          disabled={!toilet.full || !!care || leaving || busy}
          onPress={async () => {
            if (careBusy.current) {
              return;
            }
            try {
              if (await toilet.clean()) {
                showToast('깨끗해져서 좋아요!');
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
      <SafeAreaView style={styles.safeArea} pointerEvents="box-none">
        <View style={styles.header}>
          <View style={styles.topActions}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="뒤로가기"
              disabled={leaving}
              onPress={() => hideThenNavigate(() => navigation.goBack())}
              style={({ pressed }) => [styles.iconButton, pressed && styles.pressed]}
            >
              <BackIcon width={24} height={24} />
            </Pressable>
            <View style={styles.action}>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="AR 산책하기"
                style={({ pressed }) => [styles.walk, pressed && styles.pressed]}
                disabled={busy || leaving}
                onPress={() => {
                  hideThenNavigate(() => {
                    walkPending.current = true;
                    navigation.navigate('ArWalk');
                  });
                }}
              >
                <Image
                  source={require('../../assets/images/mock-adoption/img_dog_walking.png')}
                  resizeMode="contain"
                  style={styles.walkImage}
                />
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
          </View>
          <Pressable accessibilityRole="button" accessibilityLabel="알림" style={styles.iconButton}>
            <Image
              source={require('../../assets/images/mock-adoption/img_alarm.png')}
              resizeMode="contain"
              style={styles.alarm}
            />
          </Pressable>
        </View>
        <View style={styles.room} pointerEvents="box-none">
          <View style={styles.bowls}>
            {(['food', 'water'] as const).map(kind => (
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
            ))}
          </View>
        </View>
      </SafeAreaView>
      {care ? (
        <View style={StyleSheet.absoluteFill} onStartShouldSetResponder={() => true} />
      ) : null}
      {toast ? (
        <View
          pointerEvents="none"
          style={[
            styles.toast,
            {
              top: Math.max(16, petFrame.top + petHeight * 0.38 - 48),
              left: Math.max(
                16,
                Math.min(roomSize.width - 256, petFrame.left + petWidth / 2 - 120),
              ),
              width: Math.min(240, Math.max(0, roomSize.width - 32)),
            },
          ]}
        >
          <Text accessibilityLiveRegion="polite" style={styles.toastText}>
            {toast}
          </Text>
        </View>
      ) : null}
      {pet ? (
        <PetStatsDialog
          visible={statsVisible}
          petName={pet.name}
          stats={mockPetStats}
          onClose={() => setStatsVisible(false)}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.white },
  background: { position: 'absolute', width: '100%', height: '100%' },
  pet: { position: 'absolute' },
  toilet: { position: 'absolute', right: '3%', top: '47%', width: '36%', height: '13%' },
  toiletImage: { width: '100%', height: '100%' },
  safeArea: { flex: 1 },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingTop: 8,
  },
  iconButton: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center' },
  alarm: { width: 36, height: 36 },
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
  topActions: { alignItems: 'flex-start', gap: 4 },
  walk: { width: 60, height: 60, alignItems: 'center', justifyContent: 'center' },
  walkImage: { width: '100%', height: '100%' },
  action: {
    width: 180,
    marginTop: 8,
    alignItems: 'flex-start',
    gap: 4,
  },
  toast: {
    position: 'absolute',
    alignItems: 'center',
    backgroundColor: 'rgba(51, 51, 51, 0.5)',
    borderRadius: 20,
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  toastText: { color: colors.white, ...typography.bodyRegular14 },
  pressed: { opacity: 0.7 },
});
