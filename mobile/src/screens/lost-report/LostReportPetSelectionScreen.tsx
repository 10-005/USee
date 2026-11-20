import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import PawInputActiveIcon from '../../assets/icons/common/paw-input-active.svg';
import PawInputIcon from '../../assets/icons/common/paw-input.svg';
import { AppHeader, SkeletonBlock, SkeletonGroup, StepIndicator } from '../../components/common';
import { useLostReportDraftStore } from '../../stores/lostReportDraftStore';
import { usePetStore, type Pet } from '../../stores/petStore';
import { colors, radius, typography } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'LostReportPetSelection'>;

export function LostReportPetSelectionScreen({ navigation }: Props) {
  const insets = useSafeAreaInsets();
  const pets = usePetStore(state => state.pets);
  const fetchPets = usePetStore(state => state.fetchPets);
  const fetchPet = usePetStore(state => state.fetchPet);
  const petError = usePetStore(state => state.error);
  const petsLoading = usePetStore(state => state.isLoading);
  const entryMode = useLostReportDraftStore(state => state.entryMode);
  const selectedPetId = useLostReportDraftStore(state => state.selectedPetId);
  const selectManual = useLostReportDraftStore(state => state.selectManual);
  const selectPet = useLostReportDraftStore(state => state.selectPet);
  const resetSelection = useLostReportDraftStore(state => state.resetSelection);
  const [selectingPetId, setSelectingPetId] = useState<string | null>(null);
  const hasSelection = entryMode !== null;
  const nextButtonInsetStyle = useMemo(() => ({ bottom: insets.bottom + 16 }), [insets.bottom]);
  const scrollInsetStyle = useMemo(() => ({ paddingBottom: insets.bottom + 88 }), [insets.bottom]);

  useEffect(() => {
    resetSelection();
  }, [resetSelection]);

  useFocusEffect(
    useCallback(() => {
      fetchPets().catch(() => undefined);
    }, [fetchPets]),
  );

  const choosePet = async (pet: Pet) => {
    if (selectingPetId) {
      return;
    }
    setSelectingPetId(pet.id);
    try {
      const detail = await fetchPet(pet.id);
      if (detail) {
        selectPet(detail);
      } else {
        Alert.alert('반려동물 조회 실패', '잠시 후 다시 선택해 주세요.');
      }
    } finally {
      setSelectingPetId(null);
    }
  };

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <AppHeader onBackPress={navigation.goBack} title="실종 동물 등록" />
      <View style={styles.indicator}>
        <StepIndicator currentStep={1} totalSteps={4} />
      </View>
      <Text style={styles.heading}>누구를 찾고 있나요?</Text>

      <ScrollView
        contentContainerStyle={[styles.scrollContent, scrollInsetStyle]}
        decelerationRate="fast"
        showsVerticalScrollIndicator={false}
      >
        <Pressable
          accessibilityRole="button"
          accessibilityState={{ selected: entryMode === 'manual' }}
          onPress={selectManual}
          style={({ pressed }) => [
            styles.manualButton,
            entryMode === 'manual' && styles.selectedOption,
            pressed && styles.pressed,
          ]}
        >
          {entryMode === 'manual' ? (
            <PawInputActiveIcon height={20} width={20} />
          ) : (
            <PawInputIcon height={20} width={20} />
          )}
          <Text style={[styles.manualLabel, entryMode === 'manual' && styles.selectedLabel]}>
            직접 입력하기
          </Text>
        </Pressable>

        <View style={styles.petList}>
          {petsLoading && pets.length === 0 ? (
            <PetSelectionSkeleton />
          ) : petError && pets.length === 0 ? (
            <View style={styles.stateView}>
              <Text style={styles.stateText}>{petError}</Text>
              <Pressable accessibilityRole="button" onPress={() => fetchPets()}>
                <Text style={styles.retryText}>다시 시도</Text>
              </Pressable>
            </View>
          ) : null}
          {pets.map(pet => (
            <PetSelectionCard
              key={pet.id}
              loading={selectingPetId === pet.id}
              onPress={() => choosePet(pet)}
              pet={pet}
              selected={entryMode === 'pet' && selectedPetId === pet.id}
            />
          ))}
        </View>
      </ScrollView>

      <Pressable
        accessibilityRole="button"
        accessibilityState={{ disabled: !hasSelection }}
        disabled={!hasSelection}
        onPress={() => navigation.navigate('PetRegistration', { flow: 'lost-report' })}
        style={({ pressed }) => [
          styles.nextButton,
          nextButtonInsetStyle,
          !hasSelection && styles.nextButtonDisabled,
          pressed && hasSelection && styles.pressed,
        ]}
      >
        <Text style={styles.nextLabel}>다음</Text>
      </Pressable>
    </SafeAreaView>
  );
}

function PetSelectionSkeleton() {
  return (
    <SkeletonGroup
      accessibilityLabel="등록한 반려동물 불러오는 중"
      style={styles.selectionSkeleton}
    >
      {[0, 1, 2].map(index => (
        <View key={index} style={styles.petCard}>
          <SkeletonBlock style={styles.petImage} />
          <View style={styles.skeletonText}>
            <SkeletonBlock style={styles.skeletonName} />
            <SkeletonBlock style={styles.skeletonMeta} />
          </View>
        </View>
      ))}
    </SkeletonGroup>
  );
}

function PetSelectionCard({
  onPress,
  pet,
  selected,
  loading,
}: {
  loading: boolean;
  onPress: () => void;
  pet: Pet;
  selected: boolean;
}) {
  return (
    <Pressable
      accessibilityLabel={`${pet.name}, ${pet.species}, ${pet.breed}`}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={({ pressed }) => [
        styles.petCard,
        selected && styles.selectedPetCard,
        pressed && styles.pressed,
      ]}
    >
      <Image resizeMode="cover" source={pet.image} style={styles.petImage} />
      <View style={styles.petText}>
        <Text style={styles.petName}>{pet.name}</Text>
        <Text style={styles.petMeta}>
          {pet.species} | {pet.breed}
        </Text>
      </View>
      {loading ? <ActivityIndicator color={colors.green400} style={styles.cardLoader} /> : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  indicator: { alignItems: 'center', height: 34, paddingTop: 6 },
  heading: {
    color: colors.black,
    fontFamily: 'Pretendard-Bold',
    fontSize: 20,
    lineHeight: 26,
    marginBottom: 26,
    marginTop: 6,
    textAlign: 'center',
  },
  scrollContent: { paddingHorizontal: 26 },
  manualButton: {
    alignItems: 'center',
    borderColor: colors.gray500,
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 8,
    height: 48,
    justifyContent: 'center',
  },
  manualLabel: { ...typography.bodySemiBold16, color: colors.gray500 },
  selectedOption: { backgroundColor: '#ECFDF3', borderColor: colors.green400 },
  selectedLabel: { color: colors.green400 },
  petList: { gap: 16, marginTop: 16 },
  stateView: { alignItems: 'center', gap: 10, justifyContent: 'center', minHeight: 120 },
  stateText: { ...typography.bodyRegular14, color: colors.gray500, textAlign: 'center' },
  retryText: { ...typography.bodySemiBold16, color: colors.green400 },
  selectionSkeleton: { gap: 16 },
  skeletonText: { flex: 1, gap: 10, marginLeft: 20 },
  skeletonName: { height: 16, width: '42%' },
  skeletonMeta: { height: 12, width: '64%' },
  petCard: {
    alignItems: 'center',
    borderColor: colors.gray500,
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    height: 83,
    paddingHorizontal: 12,
  },
  selectedPetCard: {
    backgroundColor: '#ECFDF3',
    borderColor: colors.green400,
    borderWidth: 1,
    paddingHorizontal: 12,
  },
  petImage: { borderRadius: 10, height: 67, width: 67 },
  petText: { marginLeft: 20 },
  cardLoader: { marginLeft: 'auto' },
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
  nextButton: {
    alignItems: 'center',
    backgroundColor: colors.green400,
    borderRadius: radius.md,
    height: 48,
    justifyContent: 'center',
    left: 26,
    position: 'absolute',
    right: 26,
  },
  nextButtonDisabled: { backgroundColor: colors.gray200 },
  nextLabel: { ...typography.bodySemiBold16, color: colors.white },
  pressed: { opacity: 0.7 },
});
