import { useEffect, useRef, useState, type ComponentRef } from 'react';
import {
  Alert,
  Image,
  Keyboard,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import CalendarIcon from '../../assets/icons/common/calendar.svg';
import CheckCircleIcon from '../../assets/icons/common/check-circle.svg';
import MinusCircleIcon from '../../assets/icons/common/minus-circle.svg';
import Logo from '../../assets/images/logo.png';
import { DatePickerModal } from '../../components/common';
import { colors, radius, typography } from '../../theme';
import { ApiClientError, getApiErrorDisplayMessage, getApiFieldError } from '../../services/api';
import { checkNicknameAvailability } from '../../services/users';
import { useAuthStore } from '../../stores/authStore';
import {
  formatBirthDate,
  formatPhoneNumber,
  isValidName,
  isValidNickname,
  isValidPhoneNumber,
  ONBOARDING_NAME_ALERT,
  ONBOARDING_NICKNAME_ALERT,
} from './onboardingValidation';

type NicknameStatus = 'idle' | 'checking' | 'available' | 'unavailable' | 'invalid';

export function SignupOnboardingScreen() {
  const completeRegistration = useAuthStore(state => state.completeRegistration);
  const isLoading = useAuthStore(state => state.isLoading);
  const register = useAuthStore(state => state.register);
  const scrollRef = useRef<ComponentRef<typeof ScrollView>>(null);
  const nameRef = useRef<ComponentRef<typeof TextInput>>(null);
  const phoneRef = useRef<ComponentRef<typeof TextInput>>(null);
  const phoneFocusedRef = useRef(false);
  const phoneFieldYRef = useRef(0);
  const phoneRevealTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const nicknameCheckRequestRef = useRef(0);
  const [nickname, setNickname] = useState('');
  const [nicknameStatus, setNicknameStatus] = useState<NicknameStatus>('idle');
  const [nicknameError, setNicknameError] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [nameTouched, setNameTouched] = useState(false);
  const [birthDate, setBirthDate] = useState('');
  const [phone, setPhone] = useState('');
  const [phoneError, setPhoneError] = useState<string | null>(null);
  const [phoneFocused, setPhoneFocused] = useState(false);
  const [completionModalOpen, setCompletionModalOpen] = useState(false);
  const [datePickerOpen, setDatePickerOpen] = useState(false);

  const revealPhoneInput = () => {
    scrollRef.current?.scrollTo({ animated: true, y: Math.max(0, phoneFieldYRef.current - 32) });
  };

  const schedulePhoneReveal = () => {
    if (phoneRevealTimerRef.current) {
      clearTimeout(phoneRevealTimerRef.current);
    }
    requestAnimationFrame(revealPhoneInput);
    phoneRevealTimerRef.current = setTimeout(revealPhoneInput, 350);
  };

  useEffect(() => {
    const showSubscription = Keyboard.addListener('keyboardDidShow', () => {
      if (phoneFocusedRef.current) {
        revealPhoneInput();
      }
    });
    const hideSubscription = Keyboard.addListener('keyboardDidHide', () => {
      phoneFocusedRef.current = false;
      setPhoneFocused(false);
      phoneRef.current?.blur();
    });
    return () => {
      showSubscription.remove();
      hideSubscription.remove();
      if (phoneRevealTimerRef.current) {
        clearTimeout(phoneRevealTimerRef.current);
      }
    };
  }, []);

  const checkNickname = async () => {
    if (!isValidNickname(nickname)) {
      setNicknameStatus('invalid');
      setNicknameError(ONBOARDING_NICKNAME_ALERT);
      return;
    }
    const requestId = ++nicknameCheckRequestRef.current;
    setNicknameStatus('checking');
    setNicknameError(null);
    try {
      const result = await checkNicknameAvailability(nickname);
      if (requestId !== nicknameCheckRequestRef.current) {
        return;
      }
      if (!result.available) {
        setNicknameStatus('unavailable');
        setNicknameError('이미 사용 중인 닉네임입니다.');
        return;
      }
      Keyboard.dismiss();
      setNicknameStatus('available');
      requestAnimationFrame(() => nameRef.current?.focus());
    } catch (error) {
      if (requestId !== nicknameCheckRequestRef.current) {
        return;
      }
      const fieldError = getApiFieldError(error, 'nickname');
      if (fieldError) {
        setNicknameStatus('invalid');
        setNicknameError(fieldError);
        return;
      }
      setNicknameStatus('idle');
      Alert.alert(
        '닉네임 확인 실패',
        getApiErrorDisplayMessage(error, '잠시 후 다시 시도해 주세요.'),
      );
    }
  };

  const confirmName = () => {
    setNameTouched(true);
    if (!isValidName(name)) {
      return;
    }
    Keyboard.dismiss();
  };

  const openBirthDatePicker = () => {
    if (!isValidName(name)) {
      confirmName();
      return;
    }
    Keyboard.dismiss();
    setDatePickerOpen(true);
  };

  const submit = async () => {
    if (!isValidPhoneNumber(phone)) {
      setPhoneError('올바른 전화번호를 입력해 주세요.');
      phoneRef.current?.focus();
      return;
    }
    Keyboard.dismiss();
    try {
      await register({
        birthDate: birthDate.replaceAll('.', '-'),
        name,
        nickname,
        phone: phone.replaceAll('-', ''),
      });
      setCompletionModalOpen(true);
    } catch (error) {
      if (error instanceof ApiClientError && error.code.includes('NICKNAME')) {
        setNicknameStatus('unavailable');
        setNicknameError('이미 사용 중인 닉네임입니다.');
        Alert.alert('닉네임 확인', '이미 사용 중인 닉네임입니다.');
        return;
      }
      if (error instanceof ApiClientError && error.code === 'USER_DUPLICATE_PHONE') {
        setPhoneError('이미 사용 중인 전화번호입니다.');
        requestAnimationFrame(() => {
          phoneRef.current?.focus();
          schedulePhoneReveal();
        });
        return;
      }
      const phoneFieldError = getApiFieldError(error, 'phone');
      if (phoneFieldError) {
        setPhoneError(phoneFieldError);
        requestAnimationFrame(() => {
          phoneRef.current?.focus();
          schedulePhoneReveal();
        });
        return;
      }
      Alert.alert('회원가입 실패', getApiErrorDisplayMessage(error, '잠시 후 다시 시도해 주세요.'));
    }
  };

  const nicknameChecked = nicknameStatus === 'available';
  const nicknameValid = isValidNickname(nickname);
  const canSubmit =
    nicknameChecked && isValidName(name) && Boolean(birthDate) && isValidPhoneNumber(phone);

  return (
    <SafeAreaView style={styles.safeArea}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={styles.keyboardArea}
      >
        <ScrollView
          ref={scrollRef}
          automaticallyAdjustKeyboardInsets={Platform.OS === 'ios'}
          contentContainerStyle={[styles.content, phoneFocused && styles.contentWithKeyboard]}
          decelerationRate="fast"
          keyboardDismissMode="on-drag"
          keyboardShouldPersistTaps="handled"
          onContentSizeChange={() => {
            if (phoneFocusedRef.current) {
              revealPhoneInput();
            }
          }}
          showsVerticalScrollIndicator={false}
        >
          <Image resizeMode="contain" source={Logo} style={styles.logo} />

          <FieldLabel hint="2~10글자 내로 입력해주세요" title="닉네임" />
          <View style={styles.nicknameRow}>
            <TextInput
              accessibilityLabel="닉네임"
              autoCapitalize="none"
              onChangeText={value => {
                nicknameCheckRequestRef.current += 1;
                setNickname(value);
                setNicknameStatus(
                  value.length === 0 || isValidNickname(value) ? 'idle' : 'invalid',
                );
                setNicknameError(
                  value.length === 0 || isValidNickname(value) ? null : ONBOARDING_NICKNAME_ALERT,
                );
              }}
              onSubmitEditing={checkNickname}
              placeholder="치이카와"
              placeholderTextColor={colors.gray400}
              returnKeyType="done"
              style={[styles.input, styles.nicknameInput]}
              value={nickname}
            />
            <Pressable
              accessibilityRole="button"
              disabled={!nicknameValid || nicknameStatus === 'checking'}
              onPress={checkNickname}
              style={({ pressed }) => [
                styles.checkButton,
                nicknameValid && nicknameStatus !== 'checking' && styles.checkButtonEnabled,
                pressed && nicknameValid && nicknameStatus !== 'checking' && styles.pressed,
              ]}
            >
              <Text style={styles.checkButtonLabel}>
                {nicknameStatus === 'checking' ? '확인 중...' : '중복확인'}
              </Text>
            </Pressable>
          </View>
          {nicknameStatus === 'available' ? (
            <View style={styles.nicknameStatus}>
              <CheckCircleIcon height={15} width={15} />
              <Text style={styles.available}>사용 가능한 닉네임입니다</Text>
            </View>
          ) : null}
          {nicknameError ? (
            <View style={styles.nicknameStatus}>
              <MinusCircleIcon height={15} width={15} />
              <Text style={styles.unavailable}>{nicknameError}</Text>
            </View>
          ) : null}

          {nicknameChecked ? (
            <View style={styles.nextField}>
              <FieldLabel hint="본인의 실명을 입력해 주세요" title="이름" />
              <TextInput
                ref={nameRef}
                accessibilityLabel="이름(실명)"
                onChangeText={value => {
                  setName(value);
                  setNameTouched(true);
                }}
                onSubmitEditing={confirmName}
                placeholder="이름(실명)"
                placeholderTextColor={colors.gray400}
                returnKeyType="next"
                style={styles.input}
                value={name}
              />
              {nameTouched && !isValidName(name) ? (
                <View style={styles.nicknameStatus}>
                  <MinusCircleIcon height={15} width={15} />
                  <Text style={styles.unavailable}>{ONBOARDING_NAME_ALERT}</Text>
                </View>
              ) : null}
            </View>
          ) : null}

          {nicknameChecked && isValidName(name) ? (
            <View style={styles.nextField}>
              <FieldLabel title="생년월일" />
              <Pressable
                accessibilityRole="button"
                onPress={openBirthDatePicker}
                style={styles.dateInput}
              >
                <Text style={[styles.dateText, !birthDate && styles.placeholder]}>
                  {birthDate || 'YYYY.MM.DD'}
                </Text>
                <CalendarIcon height={18} width={18} />
              </Pressable>
            </View>
          ) : null}

          {birthDate ? (
            <View
              onLayout={event => {
                phoneFieldYRef.current = event.nativeEvent.layout.y;
                if (phoneFocusedRef.current) {
                  revealPhoneInput();
                }
              }}
              style={styles.nextField}
            >
              <FieldLabel title="전화번호" />
              <TextInput
                ref={phoneRef}
                accessibilityLabel="전화번호"
                keyboardType="phone-pad"
                maxLength={13}
                onBlur={() => {
                  phoneFocusedRef.current = false;
                  setPhoneFocused(false);
                }}
                onChangeText={value => {
                  setPhone(formatPhoneNumber(value));
                  setPhoneError(null);
                }}
                onFocus={() => {
                  phoneFocusedRef.current = true;
                  setPhoneFocused(true);
                  schedulePhoneReveal();
                }}
                onSubmitEditing={submit}
                placeholder="010-1234-5678"
                placeholderTextColor={colors.gray400}
                returnKeyType="done"
                style={[styles.input, phoneError && styles.inputError]}
                value={phone}
              />
              {phoneError ? (
                <View style={styles.nicknameStatus}>
                  <MinusCircleIcon height={15} width={15} />
                  <Text style={styles.unavailable}>{phoneError}</Text>
                </View>
              ) : null}
            </View>
          ) : null}
        </ScrollView>
        {birthDate && !phoneFocused ? (
          <View style={styles.submitFooter}>
            <Pressable
              accessibilityRole="button"
              disabled={!canSubmit || isLoading}
              onPress={submit}
              style={({ pressed }) => [
                styles.submitButton,
                canSubmit && !isLoading && styles.submitButtonEnabled,
                pressed && canSubmit && !isLoading && styles.pressed,
              ]}
            >
              <Text style={styles.submitLabel}>회원가입</Text>
            </Pressable>
          </View>
        ) : null}
      </KeyboardAvoidingView>
      <DatePickerModal
        maximumDate={new Date()}
        onClose={() => setDatePickerOpen(false)}
        onConfirm={date => {
          setBirthDate(formatBirthDate(date));
          setDatePickerOpen(false);
          requestAnimationFrame(() => phoneRef.current?.focus());
        }}
        title="생년월일 선택"
        value={birthDate ? new Date(birthDate.replaceAll('.', '-')) : new Date(2000, 0, 1)}
        visible={datePickerOpen}
      />
      <Modal
        animationType="fade"
        onRequestClose={() => setCompletionModalOpen(false)}
        transparent
        visible={completionModalOpen}
      >
        <View style={styles.modalOverlay}>
          <View accessibilityViewIsModal style={styles.modalCard}>
            <Text style={styles.modalTitle}>가입이 완료되었습니다.</Text>
            <Text style={styles.modalDescription}>USee와 함께 새로운 만남을 시작해보세요.</Text>
            <Pressable
              accessibilityRole="button"
              onPress={completeRegistration}
              style={({ pressed }) => [styles.modalButton, pressed && styles.pressed]}
            >
              <Text style={styles.modalButtonText}>확인</Text>
            </Pressable>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

function FieldLabel({ hint, title }: { hint?: string; title: string }) {
  return (
    <View style={styles.labelBlock}>
      <Text style={styles.label}>{title}</Text>
      {hint ? <Text style={styles.hint}>{hint}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  keyboardArea: { flex: 1 },
  content: { flexGrow: 1, paddingBottom: 24, paddingHorizontal: 20 },
  contentWithKeyboard: { paddingBottom: 380 },
  logo: { height: 58, marginBottom: 12, marginLeft: -2, marginTop: 34, width: 116 },
  labelBlock: { marginBottom: 9 },
  label: { ...typography.titleBold18, color: '#102E6A' },
  hint: { ...typography.bodyRegular14, color: '#173B82', marginTop: 3 },
  nicknameRow: { flexDirection: 'row', gap: 8 },
  input: {
    ...typography.bodyRegular16,
    borderColor: '#D6D5CE',
    borderRadius: radius.md,
    borderWidth: 1,
    color: colors.gray700,
    height: 46,
    paddingHorizontal: 14,
    paddingVertical: 0,
  },
  inputError: { borderColor: '#FA705B' },
  nicknameInput: { flex: 1 },
  checkButton: {
    alignItems: 'center',
    backgroundColor: '#E9E8E3',
    borderRadius: radius.md,
    justifyContent: 'center',
    width: 120,
  },
  checkButtonEnabled: { backgroundColor: '#00AD5F' },
  checkButtonLabel: { ...typography.bodySemiBold16, color: colors.white },
  nicknameStatus: { alignItems: 'center', flexDirection: 'row', gap: 4, marginTop: 8 },
  available: { ...typography.bodyRegular14, color: '#0BAA5A' },
  unavailable: { ...typography.bodyRegular14, color: '#FA705B' },
  nextField: { marginTop: 26 },
  dateInput: {
    alignItems: 'center',
    borderColor: '#D6D5CE',
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    height: 46,
    justifyContent: 'space-between',
    paddingHorizontal: 14,
  },
  dateText: { ...typography.bodyRegular16, color: colors.gray700 },
  placeholder: { color: colors.gray400 },
  submitButton: {
    alignItems: 'center',
    backgroundColor: '#D6D5CE',
    borderRadius: radius.sm,
    height: 48,
    justifyContent: 'center',
  },
  submitButtonEnabled: { backgroundColor: '#00AD5F' },
  submitFooter: {
    backgroundColor: colors.white,
    paddingBottom: 16,
    paddingHorizontal: 20,
    paddingTop: 8,
  },
  submitLabel: { ...typography.bodySemiBold16, color: colors.white },
  modalOverlay: {
    alignItems: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.3)',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  modalCard: {
    backgroundColor: colors.white,
    borderRadius: 18,
    padding: 24,
    width: '100%',
  },
  modalTitle: {
    color: colors.gray800,
    fontFamily: 'Pretendard-Bold',
    fontSize: 22,
    lineHeight: 29,
  },
  modalDescription: {
    ...typography.bodyRegular16,
    color: colors.gray500,
    lineHeight: 24,
    marginTop: 16,
  },
  modalButton: {
    alignItems: 'center',
    backgroundColor: colors.green400,
    borderRadius: radius.md,
    height: 48,
    justifyContent: 'center',
    marginTop: 20,
  },
  modalButtonText: { ...typography.bodySemiBold16, color: colors.white },
  pressed: { opacity: 0.8 },
});
