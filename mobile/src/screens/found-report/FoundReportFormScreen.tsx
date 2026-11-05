import { DateTimePickerAndroid } from '@react-native-community/datetimepicker';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useCallback, useMemo, useState, type ReactNode } from 'react';
import {
  Image,
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
import FilterArrowIcon from '../../assets/icons/common/filter-arrow.svg';
import LocationIcon from '../../assets/icons/common/location.svg';
import PlusIcon from '../../assets/icons/common/plus.svg';
import XCircleIcon from '../../assets/icons/common/x-circle.svg';
import { AppHeader, DatePickerModal, StepIndicator, Switch } from '../../components/common';
import { MAX_IMAGE_SIZE_MB } from '../../constants/upload';
import {
  useFoundReportDraftStore,
  type FoundReportDraft,
} from '../../stores/foundReportDraftStore';
import { useFoundReportEditStore } from '../../stores/foundReportEditStore';
import { colors, radius, typography } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'FoundReportForm'>;
type Step = 2 | 3;
const PHONE_PATTERN = /^01[016789]-\d{3,4}-\d{4}$/;

const formatPhone = (value: string) => {
  const digits = value.replace(/\D/g, '').slice(0, 11);
  if (digits.length <= 3) {
    return digits;
  }
  if (digits.length <= 7) {
    return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  }
  return `${digits.slice(0, 3)}-${digits.slice(3, -4)}-${digits.slice(-4)}`;
};

export const formatFoundDateTime = (value: string) => {
  if (!value) {
    return '선택';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '선택';
  }
  const period = date.getHours() < 12 ? '오전' : '오후';
  const hour = date.getHours() % 12 || 12;
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate(),
  ).padStart(2, '0')} ${period} ${hour}:${String(date.getMinutes()).padStart(2, '0')}`;
};

export function FoundReportFormScreen({ navigation, route }: Props) {
  const insets = useSafeAreaInsets();
  const draft = useFoundReportDraftStore(state => state.draft);
  const resetDraft = useFoundReportDraftStore(state => state.resetDraft);
  const updateDraft = useFoundReportDraftStore(state => state.updateDraft);
  const updateReport = useFoundReportEditStore(state => state.updateReport);
  const [step, setStep] = useState<Step>(2);
  const [error, setError] = useState('');
  const [datePickerOpen, setDatePickerOpen] = useState(false);
  const isEditing = Boolean(route.params?.reportId);

  const canContinue = useMemo(() => {
    if (step === 2) {
      return Boolean(draft.foundAt && draft.location && draft.foundSituation.trim());
    }
    return !draft.exposeContact || PHONE_PATTERN.test(draft.contact);
  }, [draft, step]);

  const goBack = useCallback(() => {
    Keyboard.dismiss();
    if (step > 2) {
      setStep(current => (current - 1) as Step);
      setError('');
      return;
    }
    navigation.goBack();
  }, [navigation, step]);

  const openDateTime = () => {
    Keyboard.dismiss();
    setDatePickerOpen(true);
  };

  const selectFoundDate = (date: Date) => {
    const current = draft.foundAt ? new Date(draft.foundAt) : new Date();
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
          updateDraft({ foundAt: selected.toISOString() });
        },
      });
    }, 200);
  };

  const submit = () => {
    Keyboard.dismiss();
    if (!canContinue) {
      setError('필수 항목을 확인해 주세요.');
      return;
    }
    if (step < 3) {
      setStep(current => (current + 1) as Step);
      setError('');
      return;
    }
    if (isEditing && route.params?.reportId) {
      updateReport(route.params.reportId, {
        age: draft.age,
        color: draft.color,
        contact: draft.exposeContact ? draft.contact : undefined,
        date: draft.foundAt.slice(0, 10),
        description: draft.foundSituation,
        gender: draft.gender === 'male' ? '수컷' : draft.gender === 'female' ? '암컷' : '모름',
        images: draft.images.map(image => ({ uri: image.uri })),
        location: draft.location,
        name: draft.breed,
        neutered: draft.neutered === 'done',
        species: draft.species === '강아지' ? '개' : (draft.species as '고양이' | '기타'),
        weight: draft.weight,
      });
      resetDraft();
      navigation.navigate('FoundReportDetail', { reportId: route.params.reportId });
      return;
    }
    resetDraft();
    navigation.navigate('Home');
  };

  const bottomStyle = { bottom: insets.bottom + 16 };
  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <AppHeader onBackPress={goBack} title={isEditing ? '제보글 수정' : '제보 등록'} />
      <View style={styles.indicator}>
        <StepIndicator currentStep={step} totalSteps={3} />
      </View>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={styles.flex}
      >
        <ScrollView
          contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 96 }]}
          decelerationRate="fast"
          keyboardDismissMode="on-drag"
          keyboardShouldPersistTaps="handled"
          onTouchStart={Keyboard.dismiss}
          showsVerticalScrollIndicator={false}
        >
          {step === 2 ? (
            <FoundInformationStep
              draft={draft}
              onOpenDateTime={openDateTime}
              onOpenLocation={() =>
                navigation.navigate('LostReportLocationPicker', { target: 'found-report' })
              }
              onUpdate={updateDraft}
            />
          ) : null}
          {step === 3 ? <ContactStep draft={draft} onUpdate={updateDraft} /> : null}
          {error ? <Text style={styles.error}>{error}</Text> : null}
        </ScrollView>
      </KeyboardAvoidingView>
      <Pressable
        disabled={!canContinue}
        onPress={submit}
        style={({ pressed }) => [
          styles.submit,
          bottomStyle,
          !canContinue && styles.submitDisabled,
          pressed && canContinue && styles.pressed,
        ]}
      >
        <Text style={styles.submitText}>
          {step === 3 ? (isEditing ? '게시글 수정' : '게시글 등록') : '다음'}
        </Text>
      </Pressable>
      <DatePickerModal
        maximumDate={new Date()}
        onClose={() => setDatePickerOpen(false)}
        onConfirm={selectFoundDate}
        title="발견일 선택"
        value={draft.foundAt ? new Date(draft.foundAt) : new Date()}
        visible={datePickerOpen}
      />
    </SafeAreaView>
  );
}

function _AnimalStep({
  draft,
  onAddPhoto,
  onOpenSpecies,
  onUpdate,
}: {
  draft: FoundReportDraft;
  onAddPhoto: () => void;
  onOpenSpecies: () => void;
  onUpdate: (values: Partial<FoundReportDraft>) => void;
}) {
  return (
    <>
      <Text style={styles.sectionTitle}>동물 정보</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.photoRow}>
        {draft.images.map((asset, index) => (
          <View key={`${asset.uri}-${index}`} style={styles.photoWrap}>
            <Image source={{ uri: asset.uri }} style={styles.photo} />
            <Pressable
              onPress={() =>
                onUpdate({ images: draft.images.filter((_, imageIndex) => imageIndex !== index) })
              }
              style={styles.removePhoto}
            >
              <XCircleIcon height={24} width={24} />
            </Pressable>
            {index === 0 ? <Text style={styles.representative}>대표</Text> : null}
          </View>
        ))}
        {draft.images.length < 3 ? (
          <Pressable onPress={onAddPhoto} style={styles.addPhoto}>
            <PlusIcon height={28} width={28} />
          </Pressable>
        ) : null}
      </ScrollView>
      <Text style={styles.photoGuide}>
        대표 사진 1장 이상 · 최대 3장 · 장당 {MAX_IMAGE_SIZE_MB}MB 이하
      </Text>
      <FieldLabel required>동물 종류</FieldLabel>
      <Pressable onPress={onOpenSpecies} style={styles.selectField}>
        <Text style={[styles.inputText, !draft.species && styles.placeholder]}>
          {draft.species || '선택'}
        </Text>
        <FilterArrowIcon height={20} width={20} />
      </Pressable>
      <FieldLabel required>품종</FieldLabel>
      <FormInput
        editable={Boolean(draft.species)}
        onChangeText={breed => onUpdate({ breed })}
        placeholder="품종을 입력해 주세요"
        value={draft.breed}
      />
      <FieldLabel required>성별</FieldLabel>
      <OptionRow
        onSelect={gender => onUpdate({ gender: gender as FoundReportDraft['gender'] })}
        options={[
          ['male', '수컷'],
          ['female', '암컷'],
          ['unknown', '모름'],
        ]}
        value={draft.gender}
      />
      <FieldLabel>중성화 여부</FieldLabel>
      <OptionRow
        onSelect={neutered => onUpdate({ neutered: neutered as FoundReportDraft['neutered'] })}
        options={[
          ['done', '완료'],
          ['not-done', '미완료'],
          ['unknown', '모름'],
        ]}
        value={draft.neutered}
      />
      <View style={styles.twoColumn}>
        <View style={styles.half}>
          <FieldLabel>나이</FieldLabel>
          <FormInput
            keyboardType="numeric"
            onChangeText={age => onUpdate({ age })}
            placeholder="예: 12살"
            value={draft.age}
          />
        </View>
        <View style={styles.half}>
          <FieldLabel>몸무게</FieldLabel>
          <FormInput
            keyboardType="decimal-pad"
            onChangeText={weight => onUpdate({ weight })}
            placeholder="예: 5kg"
            value={draft.weight}
          />
        </View>
      </View>
      <FieldLabel required>털색</FieldLabel>
      <FormInput
        onChangeText={color => onUpdate({ color })}
        placeholder="예: 연한갈색, 크림색"
        value={draft.color}
      />
      <FieldLabel>특징</FieldLabel>
      <FormInput
        multiline
        onChangeText={description => onUpdate({ description })}
        placeholder="예: 꼬리 끝이 뭉툭함"
        value={draft.description}
      />
      <FieldLabel required>마이크로칩(RFID) 등록 여부</FieldLabel>
      <OptionRow
        onSelect={microchipStatus =>
          onUpdate({ microchipStatus: microchipStatus as FoundReportDraft['microchipStatus'] })
        }
        options={[
          ['done', '완료'],
          ['not-done', '미완료'],
          ['unknown', '모름'],
        ]}
        value={draft.microchipStatus}
      />
      {draft.microchipStatus === 'done' ? (
        <>
          <FieldLabel>마이크로칩 번호</FieldLabel>
          <FormInput
            keyboardType="numeric"
            onChangeText={microchipNumber => onUpdate({ microchipNumber })}
            placeholder="번호를 입력해 주세요"
            value={draft.microchipNumber}
          />
        </>
      ) : null}
    </>
  );
}

function FoundInformationStep({
  draft,
  onOpenDateTime,
  onOpenLocation,
  onUpdate,
}: {
  draft: FoundReportDraft;
  onOpenDateTime: () => void;
  onOpenLocation: () => void;
  onUpdate: (values: Partial<FoundReportDraft>) => void;
}) {
  return (
    <>
      <Text style={styles.sectionTitle}>발견 정보</Text>
      <FieldLabel first required>
        발견일
      </FieldLabel>
      <Pressable onPress={onOpenDateTime} style={styles.selectField}>
        <Text style={[styles.inputText, !draft.foundAt && styles.placeholder]}>
          {formatFoundDateTime(draft.foundAt)}
        </Text>
        <CalendarIcon height={20} width={20} />
      </Pressable>
      <FieldLabel required>발견 장소</FieldLabel>
      {draft.location ? (
        <View style={styles.addressBox}>
          <Text style={styles.addressText}>{draft.location}</Text>
        </View>
      ) : null}
      <Pressable onPress={onOpenLocation} style={styles.locationButton}>
        <LocationIcon height={20} width={20} />
        <Text style={styles.locationText}>지도에서 위치 선택</Text>
      </Pressable>
      <FieldLabel>주변 지형</FieldLabel>
      <FormInput
        onChangeText={terrain => onUpdate({ terrain })}
        placeholder="예: 인동도서관 인근 산책로"
        value={draft.terrain}
      />
      <FieldLabel required>발견 상황</FieldLabel>
      <FormInput
        multiline
        onChangeText={foundSituation => onUpdate({ foundSituation })}
        placeholder="발견 당시 상황을 입력해 주세요"
        value={draft.foundSituation}
      />
    </>
  );
}

function ContactStep({
  draft,
  onUpdate,
}: {
  draft: FoundReportDraft;
  onUpdate: (values: Partial<FoundReportDraft>) => void;
}) {
  return (
    <>
      <Text style={styles.sectionTitle}>연락처</Text>
      <View style={styles.switchRow}>
        <View>
          <Text style={styles.switchTitle}>전화번호 노출 여부</Text>
          <Text style={styles.switchDescription}>긴급 시 빠른 연락 수단이 될 수 있습니다.</Text>
        </View>
        <Switch
          label="전화번호 공개"
          onValueChange={exposeContact => onUpdate({ exposeContact })}
          value={draft.exposeContact}
        />
      </View>
      <FieldLabel>전화번호</FieldLabel>
      <FormInput
        editable={draft.exposeContact}
        keyboardType="phone-pad"
        onChangeText={contact => onUpdate({ contact: formatPhone(contact) })}
        placeholder="010-0000-0000"
        value={draft.contact}
      />
    </>
  );
}

function FieldLabel({
  children,
  first = false,
  required = false,
}: {
  children: ReactNode;
  first?: boolean;
  required?: boolean;
}) {
  return (
    <Text style={[styles.label, first && styles.firstLabel]}>
      {children}
      {required ? '*' : ''}
    </Text>
  );
}

function FormInput({
  editable = true,
  keyboardType,
  multiline = false,
  onChangeText,
  placeholder,
  value,
}: {
  editable?: boolean;
  keyboardType?: 'decimal-pad' | 'numeric' | 'phone-pad';
  multiline?: boolean;
  onChangeText: (value: string) => void;
  placeholder: string;
  value: string;
}) {
  return (
    <TextInput
      editable={editable}
      keyboardType={keyboardType}
      multiline={multiline}
      onChangeText={onChangeText}
      placeholder={placeholder}
      placeholderTextColor={colors.gray500}
      style={[styles.textInput, multiline && styles.multiline, !editable && styles.disabledInput]}
      value={value}
    />
  );
}

function OptionRow({
  onSelect,
  options,
  value,
}: {
  onSelect: (value: string) => void;
  options: [string, string][];
  value: string | null;
}) {
  return (
    <View style={styles.optionRow}>
      {options.map(([key, label]) => (
        <Pressable
          key={key}
          onPress={() => {
            Keyboard.dismiss();
            onSelect(key);
          }}
          style={[styles.optionButton, value === key && styles.optionButtonSelected]}
        >
          <Text style={[styles.optionLabel, value === key && styles.optionLabelSelected]}>
            {label}
          </Text>
        </Pressable>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  flex: { flex: 1 },
  indicator: { alignItems: 'center', height: 24, paddingTop: 2 },
  content: { paddingHorizontal: 26 },
  sectionTitle: {
    color: colors.black,
    fontFamily: 'Pretendard-Bold',
    fontSize: 20,
    lineHeight: 26,
    marginBottom: 22,
  },
  photoRow: { flexGrow: 0 },
  photoWrap: { marginRight: 10, position: 'relative' },
  photo: { borderRadius: 10, height: 80, width: 80 },
  addPhoto: {
    alignItems: 'center',
    backgroundColor: colors.gray200,
    borderRadius: 10,
    height: 80,
    justifyContent: 'center',
    width: 80,
  },
  removePhoto: { position: 'absolute', right: -6, top: -6 },
  representative: {
    ...typography.captionRegular10,
    backgroundColor: colors.green400,
    bottom: 0,
    color: colors.white,
    left: 0,
    paddingHorizontal: 6,
    paddingVertical: 2,
    position: 'absolute',
  },
  photoGuide: {
    ...typography.captionRegular10,
    color: colors.gray600,
    marginBottom: 24,
    marginTop: 8,
  },
  label: { ...typography.bodySemiBold16, color: colors.black, marginBottom: 10, marginTop: 22 },
  firstLabel: { marginTop: 0 },
  selectField: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    height: 46,
    justifyContent: 'space-between',
    paddingHorizontal: 14,
  },
  inputText: { ...typography.bodyRegular16, color: colors.gray700 },
  placeholder: { color: colors.gray500 },
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
  multiline: { height: 107, paddingTop: 12, textAlignVertical: 'top' },
  disabledInput: { backgroundColor: colors.gray10, color: colors.gray300 },
  optionRow: { flexDirection: 'row', gap: 12 },
  optionButton: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    flex: 1,
    height: 46,
    justifyContent: 'center',
  },
  optionButtonSelected: { backgroundColor: 'rgba(0, 157, 88, 0.08)', borderColor: colors.green400 },
  optionLabel: { ...typography.bodyRegular16, color: colors.gray300 },
  optionLabelSelected: { color: colors.green400 },
  twoColumn: { flexDirection: 'row', gap: 12 },
  half: { flex: 1 },
  addressBox: {
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    minHeight: 46,
    padding: 12,
  },
  addressText: { ...typography.bodyRegular16, color: colors.gray500 },
  locationButton: {
    alignItems: 'center',
    borderColor: colors.green400,
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 8,
    height: 48,
    justifyContent: 'center',
    marginTop: 12,
  },
  locationText: { ...typography.bodySemiBold16, color: colors.green400 },
  switchRow: { alignItems: 'flex-start', flexDirection: 'row', justifyContent: 'space-between' },
  switchTitle: { ...typography.bodySemiBold16, color: colors.black },
  switchDescription: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 2 },
  error: { ...typography.bodyRegular14, color: colors.danger, marginTop: 12 },
  submit: {
    alignItems: 'center',
    backgroundColor: colors.green400,
    borderRadius: radius.md,
    height: 48,
    justifyContent: 'center',
    left: 26,
    position: 'absolute',
    right: 26,
  },
  submitDisabled: { backgroundColor: colors.gray200 },
  submitText: { ...typography.bodySemiBold16, color: colors.white },
  pressed: { opacity: 0.72 },
  modalBackdrop: {
    backgroundColor: 'rgba(0,0,0,0.25)',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 26,
  },
  optionSheet: { backgroundColor: colors.white, borderRadius: radius.lg, overflow: 'hidden' },
  optionItem: {
    borderBottomColor: colors.gray200,
    borderBottomWidth: 1,
    paddingHorizontal: 20,
    paddingVertical: 16,
  },
  optionText: { ...typography.bodyRegular16, color: colors.gray700 },
});
