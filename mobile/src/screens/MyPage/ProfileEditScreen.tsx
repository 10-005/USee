import { DateTimePickerAndroid } from '@react-native-community/datetimepicker';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  BackHandler,
  Image,
  Keyboard,
  Modal,
  PermissionsAndroid,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { launchCamera, launchImageLibrary, type Asset } from 'react-native-image-picker';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import CalendarIcon from '../../assets/icons/common/calendar.svg';
import LeftArrowIcon from '../../assets/icons/common/left-arrow.svg';
import CameraIcon from '../../assets/icons/mypage/photo-camera.svg';
import LibraryIcon from '../../assets/icons/mypage/photo-library.svg';
import {
  ALLOWED_IMAGE_TYPES,
  MAX_IMAGE_SIZE_BYTES,
  MAX_IMAGE_SIZE_MB,
} from '../../constants/upload';
import { useProfileStore } from '../../stores/profileStore';
import { colors, radius, typography } from '../../theme';
import {
  formatDate,
  formatPhoneNumber,
  getNameError,
  getNicknameError,
  isValidPhoneNumber,
  parseDate,
} from './profileValidation';

type Props = NativeStackScreenProps<RootStackParamList, 'ProfileEdit'>;

export function ProfileEditScreen({ navigation }: Props) {
  const insets = useSafeAreaInsets();
  const profile = useProfileStore(state => state.profile);
  const updateProfile = useProfileStore(state => state.updateProfile);
  const [nickname, setNickname] = useState(profile.nickname);
  const [name, setName] = useState(profile.name);
  const [birthDate, setBirthDate] = useState(profile.birthDate);
  const [phone, setPhone] = useState(profile.phone);
  const [image, setImage] = useState(profile.image);
  const [imageMenuOpen, setImageMenuOpen] = useState(false);
  const [cancelModalOpen, setCancelModalOpen] = useState(false);
  const [touched, setTouched] = useState({ name: false, nickname: false, phone: false });

  const nicknameError = getNicknameError(nickname, profile.nickname);
  const nameError = getNameError(name);
  const phoneError = isValidPhoneNumber(phone) ? null : '올바른 휴대폰 번호를 입력해주세요.';
  const hasChanges = useMemo(
    () =>
      nickname.trim() !== profile.nickname ||
      name.trim() !== profile.name ||
      birthDate !== profile.birthDate ||
      phone !== profile.phone ||
      image !== profile.image,
    [birthDate, image, name, nickname, phone, profile],
  );
  const canSubmit = hasChanges && !nicknameError && !nameError && !phoneError;

  const requestBack = useCallback(() => {
    Keyboard.dismiss();
    setImageMenuOpen(false);
    if (hasChanges) {
      setCancelModalOpen(true);
    } else {
      navigation.goBack();
    }
  }, [hasChanges, navigation]);

  useEffect(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      requestBack();
      return true;
    });
    return () => subscription.remove();
  }, [requestBack]);

  const validateImage = (asset?: Asset) => {
    if (!asset?.uri) {
      return '선택한 사진을 불러올 수 없어요.';
    }
    if (
      asset.type &&
      !ALLOWED_IMAGE_TYPES.includes(asset.type as (typeof ALLOWED_IMAGE_TYPES)[number])
    ) {
      return 'JPG 또는 PNG 사진만 등록할 수 있어요.';
    }
    if (asset.fileSize && asset.fileSize > MAX_IMAGE_SIZE_BYTES) {
      return `사진은 장당 ${MAX_IMAGE_SIZE_MB}MB 이하만 등록할 수 있어요.`;
    }
    return null;
  };

  const applyImage = (asset?: Asset) => {
    const error = validateImage(asset);
    if (error) {
      Alert.alert('사진을 변경할 수 없어요', error);
      return;
    }
    setImage({ uri: asset!.uri! });
  };

  const chooseFromLibrary = async () => {
    setImageMenuOpen(false);
    try {
      const result = await launchImageLibrary({
        includeExtra: true,
        mediaType: 'photo',
        selectionLimit: 1,
      });
      if (result.errorCode) {
        Alert.alert(
          '사진 보관함을 열 수 없어요',
          result.errorMessage ?? '잠시 후 다시 시도해주세요.',
        );
      } else if (!result.didCancel) {
        applyImage(result.assets?.[0]);
      }
    } catch {
      Alert.alert('사진을 변경할 수 없어요', '앱을 새로 빌드한 뒤 다시 시도해주세요.');
    }
  };

  const takePhoto = async () => {
    setImageMenuOpen(false);
    if (Platform.OS === 'android') {
      const permission = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.CAMERA, {
        buttonNegative: '취소',
        buttonPositive: '허용',
        message: '프로필 사진을 촬영하려면 카메라 권한이 필요합니다.',
        title: '카메라 권한',
      });
      if (permission !== PermissionsAndroid.RESULTS.GRANTED) {
        Alert.alert('카메라 권한이 필요해요', '설정에서 카메라 권한을 허용해주세요.');
        return;
      }
    }
    try {
      const result = await launchCamera({ mediaType: 'photo', saveToPhotos: false });
      if (result.errorCode) {
        Alert.alert('카메라를 열 수 없어요', result.errorMessage ?? '잠시 후 다시 시도해주세요.');
      } else if (!result.didCancel) {
        applyImage(result.assets?.[0]);
      }
    } catch {
      Alert.alert('카메라를 열 수 없어요', '앱을 새로 빌드한 뒤 다시 시도해주세요.');
    }
  };

  const openDatePicker = () => {
    Keyboard.dismiss();
    setImageMenuOpen(false);
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
    updateProfile({ birthDate, image, name: name.trim(), nickname: nickname.trim(), phone });
    navigation.goBack();
  };

  const dismissOverlays = () => {
    Keyboard.dismiss();
    setImageMenuOpen(false);
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <View style={styles.header}>
        <Pressable
          accessibilityLabel="뒤로가기"
          hitSlop={10}
          onPress={requestBack}
          style={styles.backButton}
        >
          <LeftArrowIcon height={24} width={24} />
        </Pressable>
        <Text style={styles.headerTitle}>내 정보 수정</Text>
      </View>

      <ScrollView
        contentContainerStyle={styles.content}
        keyboardDismissMode="on-drag"
        keyboardShouldPersistTaps="handled"
        onScrollBeginDrag={dismissOverlays}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.profileWrap}>
          <Pressable
            accessibilityLabel="프로필 사진 변경"
            onPress={event => {
              event.stopPropagation();
              Keyboard.dismiss();
              setImageMenuOpen(current => !current);
            }}
          >
            <Image source={image} style={styles.profileImage} />
            <View style={styles.cameraBadge}>
              <CameraIcon height={15} width={15} />
            </View>
          </Pressable>
          {imageMenuOpen ? (
            <View style={styles.imageMenu}>
              <Pressable onPress={chooseFromLibrary} style={styles.imageMenuRow}>
                <LibraryIcon height={24} width={24} />
                <Text style={styles.imageMenuText}>사진 보관함</Text>
              </Pressable>
              <Pressable onPress={takePhoto} style={styles.imageMenuRow}>
                <CameraIcon height={24} width={24} />
                <Text style={styles.imageMenuText}>사진 찍기</Text>
              </Pressable>
            </View>
          ) : null}
        </View>

        <ProfileInput
          error={touched.nickname ? nicknameError : null}
          label="닉네임"
          maxLength={12}
          onBlur={() => setTouched(current => ({ ...current, nickname: true }))}
          onChangeText={setNickname}
          value={nickname}
        />
        <ProfileInput
          error={touched.name ? nameError : null}
          label="이름"
          maxLength={20}
          onBlur={() => setTouched(current => ({ ...current, name: true }))}
          onChangeText={setName}
          value={name}
        />
        <View style={styles.field}>
          <Text style={styles.label}>생년월일</Text>
          <Pressable
            accessibilityLabel="생년월일 선택"
            onPress={openDatePicker}
            style={styles.dateInput}
          >
            <Text style={styles.dateText}>{birthDate}</Text>
            <CalendarIcon height={20} width={20} />
          </Pressable>
        </View>
        <ProfileInput
          error={touched.phone ? phoneError : null}
          keyboardType="number-pad"
          label="휴대폰"
          maxLength={13}
          onBlur={() => setTouched(current => ({ ...current, phone: true }))}
          onChangeText={value => setPhone(formatPhoneNumber(value))}
          value={phone}
        />
      </ScrollView>

      <View style={[styles.bottomArea, { paddingBottom: Math.max(insets.bottom, 16) }]}>
        <Pressable
          accessibilityRole="button"
          accessibilityState={{ disabled: !canSubmit }}
          disabled={!canSubmit}
          onPress={submit}
          style={[styles.submitButton, canSubmit && styles.submitButtonActive]}
        >
          <Text style={styles.submitText}>수정하기</Text>
        </Pressable>
      </View>

      <Modal
        animationType="fade"
        onRequestClose={() => setCancelModalOpen(false)}
        transparent
        visible={cancelModalOpen}
      >
        <View style={styles.modalOverlay}>
          <View style={styles.cancelModal}>
            <Text style={styles.modalTitle}>내 정보 수정을 취소하시겠습니까?</Text>
            <Text style={styles.modalDescription}>
              변경한 정보는 저장되지 않으며,{`\n`}이전 상태로 돌아갑니다.
            </Text>
            <View style={styles.modalButtons}>
              <Pressable
                onPress={() => setCancelModalOpen(false)}
                style={[styles.modalButton, styles.keepButton]}
              >
                <Text style={styles.modalButtonText}>계속 수정하기</Text>
              </Pressable>
              <Pressable
                onPress={() => navigation.goBack()}
                style={[styles.modalButton, styles.cancelButton]}
              >
                <Text style={styles.modalButtonText}>수정 취소하기</Text>
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

type ProfileInputProps = {
  error: string | null;
  keyboardType?: 'default' | 'number-pad';
  label: string;
  maxLength: number;
  onBlur: () => void;
  onChangeText: (value: string) => void;
  value: string;
};

function ProfileInput({
  error,
  keyboardType = 'default',
  label,
  maxLength,
  onBlur,
  onChangeText,
  value,
}: ProfileInputProps) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        keyboardType={keyboardType}
        maxLength={maxLength}
        onBlur={onBlur}
        onChangeText={onChangeText}
        style={[styles.input, error && styles.inputError]}
        value={value}
      />
      {error ? <Text style={styles.errorText}>{error}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  header: { alignItems: 'center', height: 56, justifyContent: 'center' },
  backButton: { left: 18, position: 'absolute' },
  headerTitle: { ...typography.titleBold18, color: colors.black },
  content: { paddingBottom: 110, paddingHorizontal: 26 },
  profileWrap: { alignItems: 'center', height: 128, paddingTop: 15, zIndex: 3 },
  profileImage: { borderRadius: 53, height: 105, width: 105 },
  cameraBadge: {
    alignItems: 'center',
    backgroundColor: colors.gray200,
    borderColor: colors.white,
    borderRadius: 12,
    borderWidth: 2,
    bottom: 0,
    height: 24,
    justifyContent: 'center',
    position: 'absolute',
    right: 0,
    width: 24,
  },
  imageMenu: {
    backgroundColor: colors.white,
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    elevation: 5,
    position: 'absolute',
    top: 112,
    width: 160,
    zIndex: 5,
  },
  imageMenuRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 14,
    height: 48,
    paddingHorizontal: 16,
  },
  imageMenuText: { ...typography.bodyRegular16, color: colors.gray700 },
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
  modalOverlay: {
    alignItems: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.3)',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  cancelModal: { backgroundColor: colors.white, borderRadius: 18, padding: 24, width: '100%' },
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
  modalButtons: { flexDirection: 'row', gap: 14, marginTop: 20 },
  modalButton: {
    alignItems: 'center',
    borderRadius: radius.md,
    flex: 1,
    height: 40,
    justifyContent: 'center',
  },
  keepButton: { backgroundColor: colors.gray200 },
  cancelButton: { backgroundColor: '#FA705B' },
  modalButtonText: { ...typography.bodySemiBold16, color: colors.white },
});
