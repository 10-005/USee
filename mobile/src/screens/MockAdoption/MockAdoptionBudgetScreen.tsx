import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useRef, useState } from 'react';
import {
  Image,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { RootStackParamList } from '../../app/navigation/types';
import DownArrowIcon from '../../assets/icons/common/filter-arrow.svg';
import { AppHeader, Button, TextInput } from '../../components/common';
import { startMockAdoption } from '../../services/storage/mockAdoptionStorage';
import { colors, radius, typography } from '../../theme';
import { getBudgetError } from './budget';
import { mockAdoptionPets } from './data';
import { costGroupLabels, getInitialCostTotal, initialCostItems } from './initialCosts';

type Props = NativeStackScreenProps<RootStackParamList, 'MockAdoptionBudget'>;
const budgetIncrements = [
  { label: '+1만', amount: 10_000 },
  { label: '+5만', amount: 50_000 },
  { label: '+10만', amount: 100_000 },
  { label: '+100만', amount: 1_000_000 },
];

export function MockAdoptionBudgetScreen({ navigation, route }: Props) {
  const pet = mockAdoptionPets.find(item => item.id === route.params.petId);
  const [value, setValue] = useState('');
  const [touched, setTouched] = useState(false);
  const [guideExpanded, setGuideExpanded] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string>();
  const submitting = useRef(false);
  const error = getBudgetError(value);

  const start = async () => {
    setTouched(true);
    if (error || !pet || submitting.current) {
      return;
    }
    submitting.current = true;
    setSaving(true);
    setSaveError(undefined);
    try {
      const session = await startMockAdoption(pet.id, Number(value));
      navigation.replace('MockAdoptionActivity', { petId: session.petId });
    } catch {
      setSaveError('예산을 저장하지 못했어요. 다시 시도해주세요.');
    } finally {
      submitting.current = false;
      setSaving(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <AppHeader title="모의 입양" onBackPress={() => navigation.goBack()} />
      <KeyboardAvoidingView
        style={styles.container}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
          <Text style={styles.step}>예산 설정</Text>
          <View style={styles.titleGroup}>
            <Text style={[styles.title, styles.titleAccent]}>첫 주 예산을</Text>
            <Text style={styles.title}>입력해주세요!</Text>
          </View>
          <Text style={styles.subtitle}>
            초기 준비 비용을 포함해, 함께하는 일주일의 예산을 세워보세요.
          </Text>
          {pet ? (
            <View style={styles.pet}>
              <Image source={pet.image} style={styles.petImage} resizeMode="contain" />
              <Text style={styles.petName}>{pet.name}</Text>
            </View>
          ) : (
            <Text style={styles.error}>
              선택한 동물을 찾을 수 없어요. 이전 화면에서 다시 선택해주세요.
            </Text>
          )}
          <Text style={styles.inputLabel}>첫 주 예산 (초기 준비 비용 포함)</Text>
          <View style={styles.amountField}>
            <TextInput
              accessibilityLabel="첫 주 예산 (초기 준비 비용 포함)"
              placeholder="0"
              style={styles.amountInput}
              keyboardType="number-pad"
              inputMode="numeric"
              editable={!saving}
              value={value.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
              onChangeText={text => {
                const digits = text.replace(/,/g, '');
                if (/^[0-9]*$/.test(digits)) {
                  setValue(digits);
                  setSaveError(undefined);
                }
              }}
              onBlur={() => setTouched(true)}
              aria-invalid={Boolean((touched || value) && error)}
            />
            <View pointerEvents="none" style={styles.currencyWrap}>
              <Text style={styles.currency}>원</Text>
            </View>
          </View>
          {(touched || value) && error ? (
            <Text accessibilityLiveRegion="polite" style={styles.error}>
              {error}
            </Text>
          ) : null}
          <View style={styles.incrementRow}>
            {budgetIncrements.map(increment => (
              <Pressable
                key={increment.amount}
                accessibilityRole="button"
                accessibilityLabel={`${increment.amount.toLocaleString('ko-KR')}원 추가`}
                accessibilityState={{ disabled: saving }}
                disabled={saving}
                onPress={() => {
                  setTouched(true);
                  setSaveError(undefined);
                  setValue(current => {
                    const next = Number(current || '0') + increment.amount;
                    return Number.isSafeInteger(next) ? String(next) : current;
                  });
                }}
                style={({ pressed }) => [
                  styles.incrementButton,
                  pressed && styles.pressed,
                  saving && styles.disabled,
                ]}
              >
                <Text style={styles.incrementLabel}>{increment.label}</Text>
              </Pressable>
            ))}
          </View>
          <Text style={styles.helper}>최소 10,000원부터 입력할 수 있어요.</Text>
          {pet ? (
            <View style={styles.guide}>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="초기 준비 비용 참고하기"
                accessibilityState={{ expanded: guideExpanded }}
                onPress={() => setGuideExpanded(current => !current)}
                style={({ pressed }) => [styles.guideToggle, pressed && styles.pressed]}
              >
                <View style={styles.guideHeading}>
                  <Text style={styles.guideTitle}>초기 준비 비용 참고하기</Text>
                  <Text style={styles.description}>
                    {costGroupLabels[pet.costGroup]} 기준 · 참고용 추정치
                  </Text>
                </View>
                <View style={[styles.toggleIcon, guideExpanded && styles.toggleIconExpanded]}>
                  <DownArrowIcon width={20} height={20} />
                </View>
              </Pressable>
              {guideExpanded ? (
                <View style={styles.guideDetails}>
                  <Text style={styles.guideTitle}>
                    {costGroupLabels[pet.costGroup]} 초기 준비 비용
                  </Text>
                  <Text style={styles.amount}>
                    약 {getInitialCostTotal(pet.costGroup).toLocaleString('ko-KR')}원
                  </Text>
                  <Text style={styles.description}>
                    처음 반려동물을 맞이할 때 준비하는 항목별 예상 금액이에요.
                  </Text>
                  {initialCostItems.map(item => (
                    <View key={item.label} style={styles.costRow}>
                      <Text style={styles.description}>{item.label}</Text>
                      <Text style={styles.description}>
                        {item.amounts[pet.costGroup].toLocaleString('ko-KR')}원
                      </Text>
                    </View>
                  ))}
                  <View style={[styles.costRow, styles.totalRow]}>
                    <Text style={styles.guideTitle}>합계</Text>
                    <Text style={styles.guideTitle}>
                      {getInitialCostTotal(pet.costGroup).toLocaleString('ko-KR')}원
                    </Text>
                  </View>
                  <Text style={styles.description}>
                    서비스에서 설정한 참고용 추정치로, 매주 반복되는 지출이나 최소 필수 금액은
                    아니에요. 건강 상태와 이미 갖춘 용품, 진료 여부에 따라 실제 비용은 달라져요.
                  </Text>
                  {pet.id === 'dog-3' ? (
                    <Text style={styles.description}>
                      푸들은 토이·미니어처 기준의 소형견 예산을 적용했어요.
                    </Text>
                  ) : null}
                </View>
              ) : null}
            </View>
          ) : null}
          <Text style={styles.helper}>시작하면 선택한 동물과 예산이 7일간 저장돼요.</Text>
        </ScrollView>
        <View style={styles.footer}>
          {saveError ? (
            <Text accessibilityLiveRegion="polite" style={styles.error}>
              {saveError}
            </Text>
          ) : null}
          <Button disabled={Boolean(error) || !pet} loading={saving} onPress={start}>
            모의입양 시작하기
          </Button>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.white },
  content: { padding: 20, gap: 12 },
  step: { ...typography.bodySemiBold16, color: colors.green400 },
  titleGroup: { gap: 2, paddingVertical: 4 },
  title: {
    ...typography.titleBold18,
    fontSize: 22,
    lineHeight: 34,
    color: colors.black,
    flexShrink: 1,
  },
  titleAccent: { color: colors.green400 },
  subtitle: { ...typography.bodyRegular14, lineHeight: 22, color: colors.gray500 },
  pet: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.gray10,
    borderRadius: radius.lg,
    padding: 12,
    gap: 12,
    marginVertical: 8,
  },
  petImage: { width: 60, height: 60 },
  petName: { ...typography.bodySemiBold16, color: colors.gray700 },
  inputLabel: { ...typography.bodyRegular14, color: colors.gray600 },
  amountField: { position: 'relative' },
  amountInput: { textAlign: 'right', paddingRight: 36 },
  currencyWrap: {
    position: 'absolute',
    top: 0,
    right: 16,
    height: '100%',
    justifyContent: 'center',
  },
  currency: { ...typography.bodyRegular16, color: colors.gray700 },
  incrementRow: { flexDirection: 'row', gap: 8 },
  incrementButton: {
    flex: 1,
    minHeight: 30,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.md,
    backgroundColor: colors.gray10,
    borderColor: colors.gray200,
    borderWidth: 1,
  },
  incrementLabel: { ...typography.bodyRegular14, color: colors.gray600 },
  pressed: { opacity: 0.7 },
  disabled: { opacity: 0.45 },
  helper: { ...typography.bodyRegular14, color: colors.gray500 },
  guide: {
    borderRadius: radius.lg,
    backgroundColor: colors.gray10,
    borderWidth: 1,
    borderColor: colors.gray200,
    marginVertical: 8,
  },
  guideToggle: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
    gap: 12,
  },
  guideHeading: { flex: 1, gap: 4 },
  toggleIcon: {
    width: 24,
    height: 24,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  toggleIconExpanded: { transform: [{ rotate: '180deg' }] },
  guideDetails: { padding: 16, paddingTop: 0, gap: 12 },
  guideTitle: { ...typography.bodySemiBold16, color: colors.gray700 },
  amount: { ...typography.titleBold18, color: colors.gray700 },
  description: { ...typography.bodyRegular14, lineHeight: 22, color: colors.gray600 },
  costRow: { flexDirection: 'row', justifyContent: 'space-between', gap: 12 },
  totalRow: { borderTopWidth: 1, borderTopColor: colors.gray200, paddingTop: 12 },
  footer: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 16, gap: 8 },
  error: { ...typography.bodyRegular14, color: colors.danger },
});
