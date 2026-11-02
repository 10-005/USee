import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useRef, useState } from 'react';
import {
  Image,
  KeyboardAvoidingView,
  Linking,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { RootStackParamList } from '../../app/navigation/types';
import { AppHeader, Button, TextInput } from '../../components/common';
import { startMockAdoption } from '../../services/storage/mockAdoptionStorage';
import { colors, radius, typography } from '../../theme';
import { getBudgetError } from './budget';
import { mockAdoptionPets } from './data';

type Props = NativeStackScreenProps<RootStackParamList, 'MockAdoptionBudget'>;
const SOURCE_URL = 'https://www.mafra.go.kr/bbs/home/792/594753/download.do';
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
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string>();
  const submitting = useRef(false);
  const error = getBudgetError(value);
  const isCat = pet?.category === 'cat';

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
          <Text style={styles.title}>일주일 예상 양육비를{'\n'}입력해주세요!</Text>
          <Text style={styles.subtitle}>함께하는 일주일, 반려동물을 위한 예산을 세워보세요.</Text>
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
          <Text style={styles.inputLabel}>일주일 예상 양육비</Text>
          <View style={styles.amountField}>
            <TextInput
              accessibilityLabel="일주일 예상 양육비"
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
          <View style={styles.guide}>
            <Text style={styles.guideTitle}>일주일 예산, 이 정도부터 계획해보세요</Text>
            <Text style={styles.amount}>{isCat ? '25,000원' : '35,000원'}</Text>
            <Text style={styles.description}>
              {isCat ? '반려묘 월평균 양육비 92,000원' : '반려견 월평균 양육비 135,000원'}을 주
              단위로 환산하고 여유를 더한 권장 시작 예산이에요.
            </Text>
            <Text style={styles.description}>
              사료·간식, 위생용품, 병원비 등을 고려해주세요. 조사 평균을 참고한 금액으로, 실제 최소
              양육비는 동물의 크기와 건강 상태에 따라 달라져요. 초기 용품과 갑작스러운 치료비는
              별도로 준비해주세요.
            </Text>
            <Pressable
              accessibilityRole="link"
              onPress={() => {
                Linking.openURL(SOURCE_URL).catch(() =>
                  setSaveError('출처를 열지 못했어요. 잠시 후 다시 시도해주세요.'),
                );
              }}
            >
              <Text style={styles.source}>출처: 농림축산식품부 2025년 반려동물 양육현황조사 ↗</Text>
            </Pressable>
          </View>
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
  title: { ...typography.titleBold18, fontSize: 24, lineHeight: 34, color: colors.black },
  subtitle: { ...typography.bodyRegular14, color: colors.gray500 },
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
    backgroundColor: '#ECFDF3',
    padding: 18,
    gap: 12,
    marginVertical: 8,
  },
  guideTitle: { ...typography.bodySemiBold16, color: colors.gray700 },
  amount: { ...typography.titleBold18, fontSize: 26, lineHeight: 34, color: colors.green400 },
  description: { ...typography.bodyRegular14, lineHeight: 22, color: colors.gray600 },
  source: {
    ...typography.captionSemiBold12,
    lineHeight: 18,
    color: colors.green400,
    textDecorationLine: 'underline',
  },
  footer: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 16, gap: 8 },
  error: { ...typography.bodyRegular14, color: colors.danger },
});
