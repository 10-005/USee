import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useRef, useState } from 'react';
import { Alert, Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { RootStackParamList } from '../../app/navigation/types';
import { Button } from '../../components/common';
import BackIcon from '../../assets/icons/common/left-arrow.svg';
import { colors, typography } from '../../theme';
import { endMockAdoption } from '../../services/storage/mockAdoptionStorage';
import { mockAdoptionPets } from './data';

type Props = NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;

export function MockAdoptionActivityScreen({ navigation, route }: Props) {
  const pet = mockAdoptionPets.find(item => item.id === route.params.petId);
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
    <View style={styles.container}>
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
            <Button disabled={busy} onPress={() => navigation.navigate('ArWalk')}>
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
