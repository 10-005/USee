import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { Alert, Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import PetChevronLeftIcon from '../../assets/icons/mypage/pet-chevron-left.svg';
import PetChevronRightIcon from '../../assets/icons/mypage/pet-chevron-right.svg';
import { usePetStore, type Pet } from '../../stores/petStore';
import { colors, radius, typography } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'PetList'>;

export function PetListScreen({ navigation }: Props) {
  const pets = usePetStore(state => state.pets);

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <View style={styles.header}>
        <Pressable
          accessibilityLabel="뒤로 가기"
          accessibilityRole="button"
          hitSlop={10}
          onPress={navigation.goBack}
          style={({ pressed }) => [styles.backButton, pressed && styles.pressed]}
        >
          <PetChevronLeftIcon />
        </Pressable>
        <Text style={styles.headerTitle}>반려동물 등록</Text>
      </View>
      <View style={styles.content}>
        <Text accessibilityLabel={`등록된 반려동물 ${pets.length}마리`} style={styles.count}>
          등록된 반려동물 {pets.length}마리
        </Text>
        <View style={styles.petList}>
          {pets.map(pet => (
            <PetListItem
              key={pet.id}
              onPress={() => Alert.alert('반려동물 상세', '상세 화면을 준비하고 있습니다.')}
              pet={pet}
            />
          ))}
        </View>
        <Pressable
          accessibilityLabel="반려동물 추가 등록"
          accessibilityRole="button"
          onPress={() =>
            Alert.alert('반려동물 추가 등록', '등록 화면은 S15P31D106-76 작업에서 연결됩니다.')
          }
          style={({ pressed }) => [styles.addCard, pressed && styles.pressed]}
        >
          <Text style={styles.plus}>+</Text>
          <Text style={styles.addLabel}>반려동물 추가 등록</Text>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}

function PetListItem({ onPress, pet }: { onPress: () => void; pet: Pet }) {
  return (
    <Pressable
      accessibilityLabel={`${pet.name} 상세보기`}
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.petRow, pressed && styles.pressed]}
    >
      <Image resizeMode="cover" source={pet.image} style={styles.petImage} />
      <View style={styles.petSummary}>
        <Text style={styles.petName}>{pet.name}</Text>
        <Text style={styles.petMeta}>
          {pet.breed} | {pet.age}살 | {pet.gender}
        </Text>
      </View>
      <PetChevronRightIcon />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  header: { alignItems: 'center', height: 56, justifyContent: 'center' },
  backButton: {
    alignItems: 'center',
    height: 40,
    justifyContent: 'center',
    left: 10,
    position: 'absolute',
    width: 40,
  },
  headerTitle: { ...typography.titleSemiBold18, color: colors.black },
  content: { paddingHorizontal: 26, paddingTop: 16 },
  count: {
    ...typography.captionSemiBold12,
    color: colors.gray500,
    fontFamily: 'Pretendard-Regular',
    lineHeight: 22,
  },
  petList: { marginTop: 8 },
  petRow: { alignItems: 'center', flexDirection: 'row', height: 83 },
  petImage: { borderRadius: 10, height: 67, width: 67 },
  petSummary: { flex: 1, marginLeft: 20 },
  petName: {
    ...typography.bodySemiBold16,
    color: colors.gray500,
    fontFamily: 'Pretendard-Bold',
    lineHeight: 22,
  },
  petMeta: {
    ...typography.captionSemiBold12,
    color: colors.gray500,
    fontFamily: 'Pretendard-Regular',
    lineHeight: 22,
  },
  addCard: {
    alignItems: 'center',
    borderColor: colors.gray500,
    borderRadius: radius.sm,
    borderStyle: 'dashed',
    borderWidth: 1,
    height: 57,
    justifyContent: 'center',
    marginTop: 12,
  },
  plus: { ...typography.bodyRegular14, color: colors.gray500, height: 22, lineHeight: 22 },
  addLabel: {
    ...typography.captionSemiBold12,
    color: colors.gray500,
    fontFamily: 'Pretendard-Regular',
    lineHeight: 22,
    marginTop: -5,
  },
  pressed: { opacity: 0.65 },
});
