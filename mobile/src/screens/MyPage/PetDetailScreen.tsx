import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMemo, useState } from 'react';
import {
  Alert,
  FlatList,
  Image,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import PetChevronLeftIcon from '../../assets/icons/mypage/pet-chevron-left.svg';
import PetEditIcon from '../../assets/icons/mypage/pet-edit.svg';
import PetMoreIcon from '../../assets/icons/mypage/pet-more.svg';
import PetTrashIcon from '../../assets/icons/mypage/pet-trash.svg';
import { usePetStore } from '../../stores/petStore';
import { colors, radius, typography } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'PetDetail'>;

export function PetDetailScreen({ navigation, route }: Props) {
  const insets = useSafeAreaInsets();
  const { width: screenWidth } = useWindowDimensions();
  const imageWidth = screenWidth - 52;
  const pet = usePetStore(state => state.pets.find(item => item.id === route.params.petId));
  const deletePet = usePetStore(state => state.deletePet);
  const [menuVisible, setMenuVisible] = useState(false);
  const [deleteVisible, setDeleteVisible] = useState(false);
  const [imageIndex, setImageIndex] = useState(0);
  const details = useMemo(
    () =>
      pet
        ? [
            ['성별', `${pet.gender} (중성화${pet.neutered ? 'O' : 'X'})`],
            ['나이', `${pet.age}살`],
            ['몸무게', `${pet.weight}kg`],
            ['색상', pet.color],
            ['특징', pet.description],
            ['RFID 번호', pet.microchipNumber],
          ]
        : [],
    [pet],
  );

  if (!pet) {
    return (
      <SafeAreaView edges={['top']} style={styles.safeArea}>
        <PetHeader onBack={navigation.goBack} onMenu={() => undefined} />
        <View style={styles.notFound}>
          <Text style={styles.notFoundText}>삭제되었거나 존재하지 않는 반려동물입니다.</Text>
        </View>
      </SafeAreaView>
    );
  }

  const confirmDelete = () => {
    deletePet(pet.id);
    setDeleteVisible(false);
    navigation.goBack();
  };

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <PetHeader onBack={navigation.goBack} onMenu={() => setMenuVisible(value => !value)} />
      <View style={styles.content}>
        <FlatList
          data={pet.images}
          decelerationRate="fast"
          horizontal
          keyExtractor={(_, index) => `${pet.id}-${index}`}
          onMomentumScrollEnd={event =>
            setImageIndex(Math.round(event.nativeEvent.contentOffset.x / imageWidth))
          }
          pagingEnabled
          renderItem={({ item }) => (
            <Image
              resizeMode="cover"
              source={item}
              style={[styles.heroImage, { width: imageWidth }]}
            />
          )}
          showsHorizontalScrollIndicator={false}
          style={{ width: imageWidth }}
        />
        <View accessibilityLabel={`${imageIndex + 1}번째 사진`} style={styles.indicator}>
          {pet.images.map((_, index) => (
            <View
              key={`${pet.id}-indicator-${index}`}
              style={[styles.indicatorDot, index === imageIndex && styles.indicatorDotActive]}
            />
          ))}
        </View>
        <Text style={styles.petName}>{pet.name}</Text>
        <Text style={styles.petKind}>
          {pet.species} | {pet.breed}
        </Text>
        <View style={styles.divider} />
        <View style={styles.details}>
          {details.map(([label, value]) => (
            <View key={label} style={styles.detailRow}>
              <Text style={styles.detailLabel}>{label}</Text>
              <Text style={styles.detailValue}>{value}</Text>
            </View>
          ))}
        </View>
      </View>
      <Pressable
        accessibilityRole="button"
        onPress={() => Alert.alert('실종 신고하기', '실종 신고 화면은 관련 작업에서 연결됩니다.')}
        style={({ pressed }) => [
          styles.reportButton,
          { bottom: insets.bottom + 22 },
          pressed && styles.pressed,
        ]}
      >
        <Text style={styles.reportText}>실종 신고하기</Text>
      </Pressable>
      {menuVisible ? (
        <View style={[styles.menu, { top: insets.top + 44 }]}>
          <Pressable
            accessibilityLabel="반려동물 수정"
            accessibilityRole="button"
            onPress={() => Alert.alert('수정하기', '수정 폼은 S15P31D106-76 작업 후 연결됩니다.')}
            style={({ pressed }) => [styles.menuItem, pressed && styles.pressed]}
          >
            <PetEditIcon />
            <Text style={styles.menuText}>수정하기</Text>
          </Pressable>
          <Pressable
            accessibilityLabel="반려동물 삭제"
            accessibilityRole="button"
            onPress={() => {
              setMenuVisible(false);
              setDeleteVisible(true);
            }}
            style={({ pressed }) => [styles.menuItem, pressed && styles.pressed]}
          >
            <PetTrashIcon />
            <Text style={styles.deleteMenuText}>삭제하기</Text>
          </Pressable>
        </View>
      ) : null}
      <Modal
        animationType="fade"
        onRequestClose={() => setDeleteVisible(false)}
        transparent
        visible={deleteVisible}
      >
        <View style={styles.modalBackdrop}>
          <View style={styles.deleteModal}>
            <Text style={styles.modalTitle}>반려동물 정보를 삭제하시겠습니까?</Text>
            <Text style={styles.modalDescription}>삭제 후에는 해당 정보를 복구할 수 없습니다.</Text>
            <View style={styles.modalActions}>
              <Pressable
                onPress={() => setDeleteVisible(false)}
                style={[styles.modalButton, styles.cancelButton]}
              >
                <Text style={styles.modalButtonText}>취소</Text>
              </Pressable>
              <Pressable
                accessibilityLabel="반려동물 삭제 확인"
                onPress={confirmDelete}
                style={[styles.modalButton, styles.deleteButton]}
              >
                <Text style={styles.modalButtonText}>삭제하기</Text>
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

function PetHeader({ onBack, onMenu }: { onBack: () => void; onMenu: () => void }) {
  return (
    <View style={styles.header}>
      <Pressable
        accessibilityLabel="뒤로 가기"
        accessibilityRole="button"
        hitSlop={10}
        onPress={onBack}
        style={styles.headerButton}
      >
        <PetChevronLeftIcon />
      </Pressable>
      <Text style={styles.headerTitle}>반려동물 상세</Text>
      <Pressable
        accessibilityLabel="반려동물 메뉴"
        accessibilityRole="button"
        hitSlop={10}
        onPress={onMenu}
        style={[styles.headerButton, styles.moreButton]}
      >
        <PetMoreIcon />
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  header: { alignItems: 'center', flexDirection: 'row', height: 56, justifyContent: 'center' },
  headerButton: {
    alignItems: 'center',
    height: 40,
    justifyContent: 'center',
    left: 10,
    position: 'absolute',
    width: 40,
  },
  moreButton: { left: undefined, right: 10 },
  headerTitle: { ...typography.titleSemiBold18, color: colors.black },
  content: { paddingHorizontal: 26, paddingTop: 12 },
  heroImage: { borderRadius: 10, height: 360 },
  indicator: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 5,
    height: 34,
    justifyContent: 'center',
  },
  indicatorDot: { backgroundColor: colors.gray200, borderRadius: 4, height: 8, width: 8 },
  indicatorDotActive: { backgroundColor: colors.green200, width: 29 },
  petName: { color: '#242424', fontFamily: 'Pretendard-Bold', fontSize: 20, lineHeight: 22 },
  petKind: {
    ...typography.captionSemiBold12,
    color: colors.gray500,
    fontFamily: 'Pretendard-Regular',
    lineHeight: 22,
    marginTop: 4,
  },
  divider: { backgroundColor: colors.gray200, height: 1, marginTop: 10 },
  details: { marginTop: 8 },
  detailRow: { alignItems: 'center', flexDirection: 'row', minHeight: 30 },
  detailLabel: { ...typography.bodyRegular16, color: colors.gray800, width: 92 },
  detailValue: { ...typography.bodySemiBold16, color: '#0A0A0A', flex: 1 },
  reportButton: {
    alignItems: 'center',
    backgroundColor: '#FAB45B',
    borderRadius: radius.sm,
    height: 48,
    justifyContent: 'center',
    left: 26,
    position: 'absolute',
    right: 26,
  },
  reportText: { ...typography.bodySemiBold16, color: colors.white },
  menu: {
    backgroundColor: colors.white,
    borderColor: '#D9DBE3',
    borderRadius: 10,
    borderWidth: 1,
    elevation: 6,
    overflow: 'hidden',
    position: 'absolute',
    right: 24,
    width: 160,
    zIndex: 2,
  },
  menuItem: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 10,
    height: 48,
    paddingHorizontal: 16,
  },
  menuText: { ...typography.bodyRegular16, color: '#262B33', fontFamily: 'Pretendard-Medium' },
  deleteMenuText: {
    ...typography.bodyRegular16,
    color: '#FA705B',
    fontFamily: 'Pretendard-Medium',
  },
  modalBackdrop: {
    alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,0.3)',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  deleteModal: { backgroundColor: colors.white, borderRadius: 18, padding: 24, width: '100%' },
  modalTitle: { color: '#222222', fontFamily: 'Pretendard-Bold', fontSize: 22, lineHeight: 28 },
  modalDescription: { ...typography.bodyRegular16, color: colors.gray500, marginTop: 16 },
  modalActions: { flexDirection: 'row', gap: 14, marginTop: 16 },
  modalButton: {
    alignItems: 'center',
    borderRadius: 10,
    flex: 1,
    height: 40,
    justifyContent: 'center',
  },
  cancelButton: { backgroundColor: colors.gray200 },
  deleteButton: { backgroundColor: '#FA705B' },
  modalButtonText: {
    color: colors.white,
    fontFamily: 'Pretendard-Bold',
    fontSize: 15,
    lineHeight: 22.5,
  },
  notFound: { alignItems: 'center', flex: 1, justifyContent: 'center' },
  notFoundText: { ...typography.bodyRegular16, color: colors.gray500 },
  pressed: { opacity: 0.65 },
});
