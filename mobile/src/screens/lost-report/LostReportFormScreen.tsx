import { DateTimePickerAndroid } from '@react-native-community/datetimepicker';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useCallback, useMemo, useState } from 'react';
import {
  BackHandler,
  Keyboard,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import CalendarIcon from '../../assets/icons/common/calendar.svg';
import LocationIcon from '../../assets/icons/common/location.svg';
import { AppHeader, DatePickerModal, StepIndicator, Switch } from '../../components/common';
import { useLostReportDraftStore } from '../../stores/lostReportDraftStore';
import { useLostReportEditStore } from '../../stores/lostReportEditStore';
import { colors, radius, typography } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'LostReportForm'>;
type Step = 3 | 4;

const PHONE_PATTERN = /^01[016789]-\d{3,4}-\d{4}$/;

export function formatLostDateTime(value: string) {
  if (!value) {
    return '선택';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '선택';
  }
  const period = date.getHours() < 12 ? '오전' : '오후';
  const hour = date.getHours() % 12 || 12;
  const minute = String(date.getMinutes()).padStart(2, '0');
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day} ${period} ${hour}:${minute}`;
}

function formatPhone(value: string) {
  const digits = value.replace(/\D/g, '').slice(0, 11);
  if (digits.length <= 3) {
    return digits;
  }
  if (digits.length <= 7) {
    return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  }
  return `${digits.slice(0, 3)}-${digits.slice(3, digits.length - 4)}-${digits.slice(-4)}`;
}

export function LostReportFormScreen({ navigation, route }: Props) {
  const insets = useSafeAreaInsets();
  const details = useLostReportDraftStore(state => state.details);
  const resetDraft = useLostReportDraftStore(state => state.resetDraft);
  const updateDetails = useLostReportDraftStore(state => state.updateDetails);
  const updateReport = useLostReportEditStore(state => state.updateReport);
  const isEditing = Boolean(route.params?.reportId);
  const [step, setStep] = useState<Step>(3);
  const [error, setError] = useState('');
  const [datePickerOpen, setDatePickerOpen] = useState(false);

  const buttonInsetStyle = useMemo(() => ({ bottom: insets.bottom + 16 }), [insets.bottom]);
  const contentInsetStyle = useMemo(() => ({ paddingBottom: insets.bottom + 92 }), [insets.bottom]);
  const canContinue =
    step === 3
      ? Boolean(details.lostAt && details.location.trim())
      : !details.exposeContact || PHONE_PATTERN.test(details.contact);

  const goBack = useCallback(() => {
    Keyboard.dismiss();
    if (step === 4) {
      setStep(3);
      setError('');
      return;
    }
    navigation.goBack();
  }, [navigation, step]);

  useFocusEffect(
    useCallback(() => {
      const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
        goBack();
        return true;
      });
      return () => subscription.remove();
    }, [goBack]),
  );

  const openDateTimePicker = () => {
    Keyboard.dismiss();
    setDatePickerOpen(true);
  };

  const selectLostDate = (date: Date) => {
    const current = details.lostAt ? new Date(details.lostAt) : new Date();
    const selected = new Date(date);
    selected.setHours(current.getHours(), current.getMinutes(), 0, 0);
    setDatePickerOpen(false);
    setTimeout(() => {
      DateTimePickerAndroid.open({
        is24Hour: false,
        mode: 'time',
        value: selected,
        onChange: (timeEvent, selectedTime) => {
          if (timeEvent.type !== 'set' || !selectedTime) {
            return;
          }
          selected.setHours(selectedTime.getHours(), selectedTime.getMinutes(), 0, 0);
          updateDetails({ lostAt: selected.toISOString() });
          setError('');
        },
      });
    }, 200);
  };

  const openLocationPicker = () => {
    Keyboard.dismiss();
    setError('');
    navigation.navigate('LostReportLocationPicker');
  };

  const handleSubmit = () => {
    Keyboard.dismiss();
    if (!canContinue) {
      setError(step === 3 ? '실종일과 실종 장소를 입력해 주세요.' : '전화번호를 확인해 주세요.');
      return;
    }
    if (step === 3) {
      setStep(4);
      setError('');
      return;
    }
    if (isEditing && route.params?.reportId) {
      const animal = useLostReportDraftStore.getState().animal;
      updateReport(route.params.reportId, {
        age: animal.age === null ? '모름' : `${animal.age}살`,
        color: animal.color,
        contact: details.exposeContact ? details.contact : undefined,
        date: details.lostAt.slice(0, 10),
        description: animal.description,
        gender: animal.gender ?? '모름',
        images: animal.images,
        location: details.detailAddress
          ? `${details.location} ${details.detailAddress}`
          : details.location,
        name: animal.name || animal.breed,
        neutered: animal.neutered === true,
        species: animal.species === '강아지' ? '개' : (animal.species as '고양이' | '기타'),
        weight: animal.weight === null ? '모름' : `${animal.weight}kg`,
      });
      resetDraft();
      navigation.navigate('LostReportDetail', { reportId: route.params.reportId });
      return;
    }
    resetDraft();
    navigation.navigate('Home');
  };

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <AppHeader onBackPress={goBack} title={isEditing ? '실종 신고 수정' : '실종 동물 등록'} />
      <View style={styles.indicator}>
        <StepIndicator currentStep={step} totalSteps={4} />
      </View>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={styles.flex}
      >
        <ScrollView
          contentContainerStyle={[styles.content, contentInsetStyle]}
          decelerationRate="fast"
          keyboardDismissMode="on-drag"
          keyboardShouldPersistTaps="handled"
          onTouchStart={Keyboard.dismiss}
          showsVerticalScrollIndicator={false}
        >
          {step === 3 ? (
            <LostInformationStep
              details={details}
              error={error}
              onFindLocation={openLocationPicker}
              onOpenDateTime={openDateTimePicker}
              onUpdate={updateDetails}
            />
          ) : (
            <ContactStep details={details} error={error} onUpdate={updateDetails} />
          )}
        </ScrollView>
      </KeyboardAvoidingView>
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ disabled: !canContinue }}
        disabled={!canContinue}
        onPress={handleSubmit}
        style={({ pressed }) => [
          styles.submitButton,
          buttonInsetStyle,
          !canContinue && styles.submitButtonDisabled,
          pressed && canContinue && styles.pressed,
        ]}
      >
        <Text style={styles.submitLabel}>
          {step === 3 ? '다음' : isEditing ? '게시글 수정' : '게시글 등록'}
        </Text>
      </Pressable>
      <DatePickerModal
        maximumDate={new Date()}
        onClose={() => setDatePickerOpen(false)}
        onConfirm={selectLostDate}
        title="실종일 선택"
        value={details.lostAt ? new Date(details.lostAt) : new Date()}
        visible={datePickerOpen}
      />
    </SafeAreaView>
  );
}

function LostInformationStep({
  details,
  error,
  onFindLocation,
  onOpenDateTime,
  onUpdate,
}: {
  details: ReturnType<typeof useLostReportDraftStore.getState>['details'];
  error: string;
  onFindLocation: () => void;
  onOpenDateTime: () => void;
  onUpdate: ReturnType<typeof useLostReportDraftStore.getState>['updateDetails'];
}) {
  return (
    <>
      <Text style={styles.sectionTitle}>실종 정보</Text>
      <Text style={styles.label}>실종일*</Text>
      <Pressable onPress={onOpenDateTime} style={styles.dateField}>
        <Text style={[styles.fieldText, !details.lostAt && styles.placeholder]}>
          {formatLostDateTime(details.lostAt)}
        </Text>
        <CalendarIcon height={20} width={20} />
      </Pressable>

      <Text style={styles.label}>실종 장소*</Text>
      {details.location ? (
        <View style={styles.selectedAddress}>
          <Text style={styles.selectedAddressText}>{details.location}</Text>
        </View>
      ) : null}
      <Pressable onPress={onFindLocation} style={styles.locationButton}>
        <LocationIcon height={20} width={20} />
        <Text style={styles.locationLabel}>지도에서 위치 선택</Text>
      </Pressable>

      <Text style={[styles.label, styles.detailAddressLabel]}>상세주소</Text>
      <TextInput
        maxLength={100}
        onChangeText={detailAddress => onUpdate({ detailAddress })}
        placeholder="예: 인동도서관 앞"
        placeholderTextColor={colors.gray500}
        style={styles.textInput}
        value={details.detailAddress}
      />
      {error ? <Text style={styles.error}>{error}</Text> : null}
    </>
  );
}

function ContactStep({
  details,
  error,
  onUpdate,
}: {
  details: ReturnType<typeof useLostReportDraftStore.getState>['details'];
  error: string;
  onUpdate: ReturnType<typeof useLostReportDraftStore.getState>['updateDetails'];
}) {
  return (
    <>
      <Text style={styles.sectionTitle}>연락처</Text>
      <View style={styles.switchRow}>
        <View style={styles.switchText}>
          <Text style={styles.switchTitle}>전화번호 노출 여부</Text>
          <Text style={styles.switchDescription}>긴급 시 빠른 연락 수단이 될 수 있습니다.</Text>
        </View>
        <Switch
          label="전화번호 공개"
          onValueChange={exposeContact => onUpdate({ exposeContact })}
          value={details.exposeContact}
        />
      </View>
      <Text style={[styles.label, !details.exposeContact && styles.disabledLabel]}>전화번호</Text>
      <TextInput
        editable={details.exposeContact}
        keyboardType="phone-pad"
        onChangeText={contact => onUpdate({ contact: formatPhone(contact) })}
        placeholder="010-0000-0000"
        placeholderTextColor={colors.gray300}
        style={[styles.textInput, !details.exposeContact && styles.disabledInput]}
        value={details.contact}
      />
      {error ? <Text style={styles.error}>{error}</Text> : null}
    </>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  flex: { flex: 1 },
  indicator: { alignItems: 'center', height: 24, paddingTop: 2 },
  content: { paddingHorizontal: 26, paddingTop: 0 },
  sectionTitle: {
    color: colors.black,
    fontFamily: 'Pretendard-Bold',
    fontSize: 20,
    lineHeight: 26,
    marginBottom: 22,
  },
  label: { ...typography.bodySemiBold16, color: colors.black, marginBottom: 10, marginTop: 0 },
  dateField: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    height: 46,
    justifyContent: 'space-between',
    marginBottom: 22,
    paddingHorizontal: 14,
  },
  fieldText: { ...typography.bodyRegular16, color: colors.gray600 },
  placeholder: { color: colors.gray500 },
  locationButton: {
    alignItems: 'center',
    borderColor: colors.green400,
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 8,
    height: 48,
    justifyContent: 'center',
    marginBottom: 12,
  },
  locationLabel: { ...typography.bodySemiBold16, color: colors.green400 },
  selectedAddress: {
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    justifyContent: 'center',
    marginBottom: 12,
    minHeight: 46,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  selectedAddressText: { ...typography.bodyRegular16, color: colors.gray500 },
  detailAddressLabel: { marginTop: 18 },
  textInput: {
    ...typography.bodyRegular16,
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    color: colors.gray700,
    height: 46,
    paddingHorizontal: 14,
    paddingVertical: 0,
  },
  error: { ...typography.bodyRegular14, color: colors.danger, marginTop: 10 },
  switchRow: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 22,
  },
  switchText: { gap: 2 },
  switchTitle: { ...typography.bodySemiBold16, color: colors.black },
  switchDescription: { ...typography.bodyRegular14, color: colors.gray500 },
  disabledLabel: { color: colors.gray300 },
  disabledInput: { backgroundColor: colors.gray10, color: colors.gray300 },
  submitButton: {
    alignItems: 'center',
    backgroundColor: colors.green400,
    borderRadius: radius.md,
    height: 48,
    justifyContent: 'center',
    left: 26,
    position: 'absolute',
    right: 26,
  },
  submitButtonDisabled: { backgroundColor: colors.gray200 },
  submitLabel: { ...typography.bodySemiBold16, color: colors.white },
  pressed: { opacity: 0.72 },
});
