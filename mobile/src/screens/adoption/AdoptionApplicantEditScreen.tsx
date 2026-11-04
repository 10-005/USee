import { DateTimePickerAndroid } from '@react-native-community/datetimepicker';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMemo, useState } from 'react';
import { Keyboard, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import CalendarIcon from '../../assets/icons/common/calendar.svg';
import { AppHeader } from '../../components/common';
import { useAdoptionApplicantStore } from '../../stores/adoptionApplicantStore';
import { colors, radius, typography } from '../../theme';
import {
  formatDate,
  formatPhoneNumber,
  getNameError,
  isValidPhoneNumber,
  parseDate,
} from '../MyPage/profileValidation';

type Props = NativeStackScreenProps<RootStackParamList, 'AdoptionApplicantEdit'>;

export function AdoptionApplicantEditScreen({ navigation }: Props) {
  const insets = useSafeAreaInsets();
  const draft = useAdoptionApplicantStore(state => state.draft);
  const save = useAdoptionApplicantStore(state => state.save);
  const [name, setName] = useState(draft.name);
  const [birthDate, setBirthDate] = useState(draft.birthDate);
  const [phone, setPhone] = useState(draft.phone);
  const [touched, setTouched] = useState({ name: false, phone: false });

  const nameError = getNameError(name);
  const phoneError = isValidPhoneNumber(phone) ? null : '올바른 휴대폰 번호를 입력해주세요.';
  const canSubmit = useMemo(
    () => Boolean(name.trim() && birthDate && phone && !nameError && !phoneError),
    [birthDate, name, nameError, phone, phoneError],
  );

  const openDatePicker = () => {
    Keyboard.dismiss();
    DateTimePickerAndroid.open({
      maximumDate: new Date(),
      mode: 'date',
      onChange: (_, date) => date && setBirthDate(formatDate(date)),
      value: parseDate(birthDate),
    });
  };

  const submit = () => {
    if (!canSubmit) {
      return;
    }
    save({ birthDate, name: name.trim(), phone });
    navigation.goBack();
  };

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <AppHeader onBackPress={navigation.goBack} title="신청자 정보 수정" />
      <ScrollView
        contentContainerStyle={styles.content}
        keyboardDismissMode="on-drag"
        keyboardShouldPersistTaps="handled"
        onTouchStart={Keyboard.dismiss}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.field}>
          <Text style={styles.label}>이름</Text>
          <TextInput
            maxLength={20}
            onBlur={() => setTouched(current => ({ ...current, name: true }))}
            onChangeText={setName}
            style={[styles.input, touched.name && nameError && styles.inputError]}
            value={name}
          />
          {touched.name && nameError ? <Text style={styles.errorText}>{nameError}</Text> : null}
        </View>

        <View style={styles.field}>
          <Text style={styles.label}>생년월일</Text>
          <Pressable onPress={openDatePicker} style={styles.dateInput}>
            <Text style={styles.dateText}>{birthDate}</Text>
            <CalendarIcon height={20} width={20} />
          </Pressable>
        </View>

        <View style={styles.field}>
          <Text style={styles.label}>휴대폰</Text>
          <TextInput
            keyboardType="number-pad"
            maxLength={13}
            onBlur={() => setTouched(current => ({ ...current, phone: true }))}
            onChangeText={value => setPhone(formatPhoneNumber(value))}
            style={[styles.input, touched.phone && phoneError && styles.inputError]}
            value={phone}
          />
          {touched.phone && phoneError ? <Text style={styles.errorText}>{phoneError}</Text> : null}
        </View>
      </ScrollView>

      <View style={[styles.bottomArea, { paddingBottom: Math.max(insets.bottom, 16) }]}>
        <Pressable
          disabled={!canSubmit}
          onPress={submit}
          style={[styles.submitButton, canSubmit && styles.submitButtonActive]}
        >
          <Text style={styles.submitText}>수정하기</Text>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  content: { paddingBottom: 110, paddingHorizontal: 26 },
  field: { marginTop: 18 },
  label: { ...typography.bodySemiBold16, color: colors.black, marginBottom: 10 },
  input: {
    ...typography.bodyRegular16,
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    color: colors.gray700,
    height: 46,
    paddingHorizontal: 14,
    paddingVertical: 0,
  },
  inputError: { borderColor: colors.danger },
  errorText: { ...typography.captionRegular10, color: colors.danger, marginTop: 4 },
  dateInput: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    height: 46,
    justifyContent: 'space-between',
    paddingHorizontal: 14,
  },
  dateText: { ...typography.bodyRegular16, color: colors.gray500 },
  bottomArea: {
    backgroundColor: colors.white,
    bottom: 0,
    left: 0,
    paddingHorizontal: 26,
    paddingTop: 8,
    position: 'absolute',
    right: 0,
  },
  submitButton: {
    alignItems: 'center',
    backgroundColor: colors.gray200,
    borderRadius: radius.md,
    height: 48,
    justifyContent: 'center',
  },
  submitButtonActive: { backgroundColor: colors.green400 },
  submitText: { ...typography.bodySemiBold16, color: colors.white },
});
