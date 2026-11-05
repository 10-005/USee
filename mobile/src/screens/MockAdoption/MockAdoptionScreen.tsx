import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { AppHeader, Button } from '../../components/common';
import { colors, radius, typography } from '../../theme';
import { mockAdoptionPets, type PetCategory } from './data';

import type { RootStackParamList } from '../../app/navigation/types';

type MockAdoptionNavigation = NativeStackNavigationProp<
  RootStackParamList,
  'MockAdoptionSelection'
>;

export function MockAdoptionScreen() {
  const navigation = useNavigation<MockAdoptionNavigation>();
  const insets = useSafeAreaInsets();
  const [category, setCategory] = useState<PetCategory>('dog');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selectedPet = mockAdoptionPets.find(pet => pet.id === selectedId);

  return (
    <SafeAreaView edges={['top']} style={styles.container}>
      <AppHeader title="모의 입양" onBackPress={() => navigation.goBack()} />
      <View style={styles.heading}>
        <Text style={styles.eyebrow}>모의 입양</Text>
        <Text style={styles.title}>모의 입양할 동물을 선택해주세요!</Text>
        <Text style={styles.subtitle}>함께 일상을 경험하고 싶은 반려동물을 골라주세요.</Text>
      </View>
      <View style={styles.tabs}>
        {(
          [
            { key: 'dog', label: '개' },
            { key: 'cat', label: '고양이' },
          ] as const
        ).map(tab => (
          <Pressable
            accessibilityRole="tab"
            accessibilityState={{ selected: category === tab.key }}
            key={tab.key}
            onPress={() => setCategory(tab.key)}
            style={[styles.tab, category === tab.key && styles.activeTab]}
          >
            <Text style={[styles.tabLabel, category === tab.key && styles.activeLabel]}>
              {tab.label}
            </Text>
          </Pressable>
        ))}
      </View>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.grid}>
        {mockAdoptionPets
          .filter(pet => pet.category === category)
          .map(pet => {
            const selected = selectedId === pet.id;
            return (
              <Pressable
                accessibilityLabel={pet.name}
                accessibilityRole="radio"
                accessibilityState={{ checked: selected }}
                key={pet.id}
                onPress={() => setSelectedId(pet.id)}
                style={({ pressed }) => [
                  styles.card,
                  selected && styles.selectedCard,
                  pressed && styles.pressed,
                ]}
              >
                <View style={[styles.photo, selected && styles.selectedPhoto]}>
                  <Image
                    source={pet.image}
                    resizeMode="contain"
                    style={styles.petImage}
                    accessible={false}
                  />
                  <View style={[styles.mark, selected && styles.selectedMark]}>
                    {selected ? <Text style={styles.check}>✓</Text> : null}
                  </View>
                </View>
                <Text style={[styles.petName, selected && styles.activeLabel]}>{pet.name}</Text>
              </Pressable>
            );
          })}
      </ScrollView>
      <View style={[styles.footer, { paddingBottom: 16 + insets.bottom }]}>
        <Text accessibilityLiveRegion="polite" style={styles.selectionLabel}>
          {selectedPet
            ? `${selectedPet.name} · 함께할 준비가 되었어요!`
            : '함께할 반려동물 한 마리를 선택해주세요.'}
        </Text>
        <Button
          disabled={!selectedPet}
          onPress={() => {
            if (selectedPet) {
              navigation.navigate('MockAdoptionName', { petId: selectedPet.id });
            }
          }}
        >
          다음
        </Button>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { backgroundColor: colors.white, flex: 1 },
  heading: { paddingHorizontal: 20, paddingTop: 24, paddingBottom: 24, gap: 10 },
  eyebrow: { ...typography.bodySemiBold16, color: colors.green400 },
  title: { ...typography.titleBold18, fontSize: 24, lineHeight: 34, color: colors.black },
  subtitle: { ...typography.bodyRegular14, color: colors.gray500 },
  tabs: {
    flexDirection: 'row',
    marginHorizontal: 20,
    marginBottom: 20,
    borderBottomWidth: 1,
    borderBottomColor: colors.gray200,
  },
  tab: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 14,
    borderBottomWidth: 2,
    borderBottomColor: colors.transparent,
  },
  activeTab: { borderBottomColor: colors.green400 },
  tabLabel: { ...typography.bodySemiBold16, color: colors.gray400 },
  activeLabel: { color: colors.green400 },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
    paddingHorizontal: 20,
    paddingBottom: 20,
  },
  card: {
    width: '45%',
    flexGrow: 1,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.gray200,
    overflow: 'hidden',
  },
  selectedCard: { borderColor: colors.green400 },
  photo: { aspectRatio: 1.35, backgroundColor: colors.gray10 },
  petImage: { width: '100%', height: '100%' },
  selectedPhoto: { backgroundColor: '#ECFDF3' },
  mark: {
    position: 'absolute',
    top: 10,
    right: 10,
    width: 22,
    height: 22,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.gray200,
    backgroundColor: colors.white,
    alignItems: 'center',
    justifyContent: 'center',
  },
  selectedMark: { backgroundColor: colors.green400, borderColor: colors.green400 },
  check: { color: colors.white, fontSize: 14, lineHeight: 18 },
  petName: {
    ...typography.bodySemiBold16,
    color: colors.gray700,
    textAlign: 'center',
    paddingVertical: 14,
    paddingHorizontal: 4,
  },
  footer: { paddingHorizontal: 20, paddingTop: 12, backgroundColor: colors.white, gap: 12 },
  selectionLabel: { ...typography.bodyRegular14, color: colors.gray500, textAlign: 'center' },
  pressed: { opacity: 0.75 },
});
