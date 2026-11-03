import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useCallback, useRef, useState } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import { Alert, Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { RootStackParamList } from '../../app/navigation/types';
import { Button } from '../../components/common';
import BackIcon from '../../assets/icons/common/left-arrow.svg';
import { colors, typography } from '../../theme';
import { endMockAdoption } from '../../services/storage/mockAdoptionStorage';
import { mockAdoptionPets } from './data';
import { RoomPet } from './RoomPet';

type Props = NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;

export function MockAdoptionActivityScreen({ navigation, route }: Props) {
  const pet = mockAdoptionPets.find(item => item.id === route.params.petId);
  const walkPending = useRef(false);
  const [returnSequence, setReturnSequence] = useState(0);
  const [roomVisible, setRoomVisible] = useState(false);
  useFocusEffect(
    useCallback(() => {
      if (walkPending.current) {
        walkPending.current = false;
        setReturnSequence(value => value + 1);
      }
      setRoomVisible(true);
      return () => setRoomVisible(false);
    }, []),
  );
  const [roomSize, setRoomSize] = useState({ width: 0, height: 0 });
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
      navigation.replace('MockAdoptionSelection');
    } catch {
      Alert.alert('모의입양', '진행 상태를 초기화하지 못했어요. 다시 시도해주세요.');
    } finally {
      ending.current = false;
      setBusy(false);
    }
  };
  return (
    <View
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
      {pet ? (
        <Image
          accessibilityLabel={pet.category === 'dog' ? '빈 배변 패드' : '빈 고양이 화장실'}
          source={
            pet.category === 'dog'
              ? require('../../assets/images/mock-adoption/img_dog_empty_toilet.png')
              : require('../../assets/images/mock-adoption/img_cat_empty_toilet.png')
          }
          resizeMode="contain"
          style={styles.toilet}
        />
      ) : null}
      {roomVisible && pet && roomSize.width > 0 ? (
        <RoomPet
          key={`${pet.id}-${returnSequence}`}
          petId={pet.id}
          style={[styles.pet, petFrame]}
          returnSequence={returnSequence}
          entryOffset={{ x: roomSize.width * 0.55, y: roomSize.height * 0.3 }}
        />
      ) : null}
      <SafeAreaView style={styles.safeArea}>
        <View style={styles.header}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="뒤로가기"
            onPress={() => navigation.goBack()}
            style={({ pressed }) => [styles.iconButton, pressed && styles.pressed]}
          >
            <BackIcon width={24} height={24} />
          </Pressable>
          <Pressable accessibilityRole="button" accessibilityLabel="알림" style={styles.iconButton}>
            <Image
              source={require('../../assets/images/mock-adoption/img_alarm.png')}
              resizeMode="contain"
              style={styles.alarm}
            />
          </Pressable>
        </View>
        <View style={styles.room}>
          <View style={styles.bowls}>
            <Image
              accessibilityLabel="사료 그릇"
              source={require('../../assets/images/mock-adoption/img_full_feetstuff_bowl.png')}
              resizeMode="contain"
              style={styles.bowl}
            />
            <Image
              accessibilityLabel="물그릇"
              source={require('../../assets/images/mock-adoption/img_full_water_bowl.png')}
              resizeMode="contain"
              style={styles.bowl}
            />
          </View>
        </View>
        <View style={styles.action}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="모의입양 그만두기"
            accessibilityState={{ disabled: busy }}
            disabled={busy}
            onPress={quit}
            style={({ pressed }) => [styles.quit, pressed && styles.pressed]}
          >
            <Text style={styles.quitLabel}>모의입양 그만두기</Text>
          </Pressable>
          <View style={styles.walk}>
            <Button
              disabled={busy}
              onPress={() => {
                walkPending.current = true;
                navigation.navigate('ArWalk');
              }}
            >
              AR 산책하기
            </Button>
          </View>
        </View>
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.white },
  background: { position: 'absolute', width: '100%', height: '100%' },
  pet: { position: 'absolute' },
  toilet: { position: 'absolute', right: '3%', top: '47%', width: '36%', height: '13%' },
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
    bottom: 24,
    flexDirection: 'row',
    width: '44%',
    maxWidth: 220,
    gap: 4,
  },
  bowl: { flex: 1, aspectRatio: 1 },
  quit: { justifyContent: 'center', minHeight: 48, paddingHorizontal: 4 },
  quitLabel: { ...typography.bodyRegular14, color: colors.gray700 },
  walk: { flex: 1 },
  action: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 20,
    paddingTop: 8,
    paddingBottom: 16,
  },
  pressed: { opacity: 0.7 },
});
