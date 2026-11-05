import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useRef, useState, type ComponentRef } from 'react';
import {
  Alert,
  BackHandler,
  Image,
  Keyboard,
  KeyboardAvoidingView,
  Modal,
  PermissionsAndroid,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { launchCamera, launchImageLibrary, type Asset } from 'react-native-image-picker';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import LeftArrowIcon from '../../assets/icons/common/left-arrow.svg';
import FilterArrowIcon from '../../assets/icons/common/filter-arrow.svg';
import PlusIcon from '../../assets/icons/common/plus.svg';
import XCircleIcon from '../../assets/icons/common/x-circle.svg';
import CameraIcon from '../../assets/icons/common/photo-camera.svg';
import LibraryIcon from '../../assets/icons/common/photo-library.svg';
import {
  KeyboardAwareInputProvider,
  KeyboardAwareTextInput,
  StepIndicator,
} from '../../components/common';
import { MAX_IMAGE_SIZE_MB } from '../../constants/upload';
import { useFoundReportDraftStore } from '../../stores/foundReportDraftStore';
import { useLostReportDraftStore } from '../../stores/lostReportDraftStore';
import { usePetStore } from '../../stores/petStore';
import { colors, radius, typography } from '../../theme';
import { PET_OPTIONS, type PetSpecies, validatePetImage } from './petRegistration';

type Props = NativeStackScreenProps<RootStackParamList, 'PetRegistration'>;
type SelectKey = 'species' | 'breed' | 'color' | null;
type Gender = '수컷' | '암컷' | '모름';
type Neutered = '완료' | '미완료' | '모름';
type Microchip = '완료' | '미완료' | '모름';

export function PetRegistrationScreen({ navigation, route }: Props) {
  const scrollRef = useRef<ComponentRef<typeof ScrollView>>(null);
  const isLostReportFlow = route.params?.flow === 'lost-report';
  const isFoundReportFlow = route.params?.flow === 'found-report';
  const isReportFlow = isLostReportFlow || isFoundReportFlow;
  const foundReportDraft = useFoundReportDraftStore(state => state.draft);
  const updateFoundReportDraft = useFoundReportDraftStore(state => state.updateDraft);
  const lostReportAnimal = useLostReportDraftStore(state => state.animal);
  const lostReportEntryMode = useLostReportDraftStore(state => state.entryMode);
  const updateLostReportAnimal = useLostReportDraftStore(state => state.updateAnimal);
  const editingPet = usePetStore(state =>
    !isReportFlow && route.params?.petId
      ? state.pets.find(pet => pet.id === route.params?.petId)
      : undefined,
  );
  const addPet = usePetStore(state => state.addPet);
  const updatePet = usePetStore(state => state.updatePet);
  const isEditing = !isReportFlow && Boolean(editingPet);
  const initialAnimal = isLostReportFlow ? lostReportAnimal : editingPet;
  const [photos, setPhotos] = useState<Asset[]>(() =>
    isFoundReportFlow
      ? foundReportDraft.images
      : initialAnimal
      ? initialAnimal.images.map(image => ({
          type: 'image/jpeg',
          uri: Image.resolveAssetSource(image)?.uri ?? '',
        }))
      : [],
  );
  const [photoMenuOpen, setPhotoMenuOpen] = useState(false);
  const [selectKey, setSelectKey] = useState<SelectKey>(null);
  const [name, setName] = useState(initialAnimal?.name ?? '');
  const initialSpecies = isFoundReportFlow ? foundReportDraft.species : initialAnimal?.species;
  const [species, setSpecies] = useState<PetSpecies | ''>(
    initialSpecies && initialSpecies in PET_OPTIONS ? (initialSpecies as PetSpecies) : '',
  );
  const [breed, setBreed] = useState(
    isFoundReportFlow ? foundReportDraft.breed : initialAnimal?.breed ?? '',
  );
  const [color, setColor] = useState(
    isFoundReportFlow ? foundReportDraft.color : initialAnimal?.color ?? '',
  );
  const [gender, setGender] = useState<Gender | ''>(
    isFoundReportFlow
      ? foundReportDraft.gender === 'male'
        ? '수컷'
        : foundReportDraft.gender === 'female'
        ? '암컷'
        : foundReportDraft.gender === 'unknown'
        ? '모름'
        : ''
      : initialAnimal?.gender ?? '',
  );
  const [neutered, setNeutered] = useState<Neutered | ''>(
    isFoundReportFlow
      ? foundReportDraft.neutered === 'done'
        ? '완료'
        : foundReportDraft.neutered === 'not-done'
        ? '미완료'
        : foundReportDraft.neutered === 'unknown'
        ? '모름'
        : ''
      : isLostReportFlow && lostReportEntryMode === 'manual'
      ? ''
      : initialAnimal
      ? initialAnimal.neutered === null
        ? '모름'
        : initialAnimal.neutered
        ? '완료'
        : '미완료'
      : '',
  );
  const [age, setAge] = useState(
    isFoundReportFlow ? foundReportDraft.age : initialAnimal?.age?.toString() ?? '',
  );
  const [weight, setWeight] = useState(
    isFoundReportFlow ? foundReportDraft.weight : initialAnimal?.weight?.toString() ?? '',
  );
  const [description, setDescription] = useState(
    isFoundReportFlow ? foundReportDraft.description : initialAnimal?.description ?? '',
  );
  const [microchip, setMicrochip] = useState<Microchip | ''>(
    isFoundReportFlow
      ? foundReportDraft.microchipStatus === 'done'
        ? '완료'
        : foundReportDraft.microchipStatus === 'not-done'
        ? '미완료'
        : foundReportDraft.microchipStatus === 'unknown'
        ? '모름'
        : ''
      : isLostReportFlow && lostReportEntryMode === 'manual'
      ? ''
      : initialAnimal
      ? initialAnimal.microchipNumber
        ? '완료'
        : '미완료'
      : '',
  );
  const [microchipNumber, setMicrochipNumber] = useState(
    isFoundReportFlow ? foundReportDraft.microchipNumber : initialAnimal?.microchipNumber ?? '',
  );
  const [cancelModalOpen, setCancelModalOpen] = useState(false);

  const hasDraft = Boolean(
    photos.length ||
      name ||
      species ||
      breed ||
      color ||
      gender ||
      neutered ||
      age ||
      weight ||
      description ||
      microchip ||
      microchipNumber,
  );

  const requestBack = useCallback(() => {
    if (hasDraft) {
      setCancelModalOpen(true);
    } else {
      navigation.goBack();
    }
  }, [hasDraft, navigation]);

  useFocusEffect(
    useCallback(() => {
      const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
        requestBack();
        return true;
      });
      return () => subscription.remove();
    }, [requestBack]),
  );

  const isValid = Boolean(
    photos.length &&
      (isReportFlow || name.trim()) &&
      species &&
      breed &&
      color &&
      gender &&
      neutered &&
      microchip &&
      (microchip !== '완료' || microchipNumber.trim()),
  );

  const appendPhotos = (assets: Asset[] = []) => {
    const accepted: Asset[] = [];
    for (const asset of assets) {
      const error = validatePetImage(asset);
      if (error) {
        Alert.alert('사진을 등록할 수 없어요', error);
        continue;
      }
      accepted.push(asset);
    }
    setPhotos(current => [...current, ...accepted].slice(0, 3));
  };

  const chooseFromLibrary = async () => {
    setPhotoMenuOpen(false);
    try {
      const result = await launchImageLibrary({
        mediaType: 'photo',
        selectionLimit: 3 - photos.length,
        includeExtra: true,
      });
      if (result.errorCode) {
        Alert.alert(
          '사진 보관함을 열 수 없어요',
          result.errorMessage ?? '잠시 후 다시 시도해주세요.',
        );
        return;
      }
      if (!result.didCancel) {
        appendPhotos(result.assets);
      }
    } catch {
      Alert.alert(
        '앱을 다시 설치해주세요',
        '사진 선택 기능이 앱에 반영되지 않았습니다. 앱을 새로 빌드한 뒤 다시 시도해주세요.',
      );
    }
  };

  const takePhoto = async () => {
    setPhotoMenuOpen(false);
    if (Platform.OS === 'android') {
      const permission = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.CAMERA, {
        buttonNegative: '취소',
        buttonPositive: '허용',
        message: '반려동물 사진을 촬영하려면 카메라 권한이 필요합니다.',
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
        return;
      }
      if (!result.didCancel) {
        appendPhotos(result.assets);
      }
    } catch {
      Alert.alert(
        '앱을 다시 설치해주세요',
        '카메라 기능이 앱에 반영되지 않았습니다. 앱을 새로 빌드한 뒤 다시 시도해주세요.',
      );
    }
  };

  const selectOption = (value: string) => {
    if (selectKey === 'species') {
      setSpecies(value as PetSpecies);
      setBreed('');
      setColor('');
    } else if (selectKey === 'breed') {
      setBreed(value);
      setColor('');
    } else if (selectKey === 'color') {
      setColor(value);
    }
    setSelectKey(null);
  };

  const submit = () => {
    if (!isValid || !photos[0]?.uri) {
      return;
    }
    const sources = photos.map(photo => ({ uri: photo.uri! }));
    const pet = {
      age: age ? Number(age) : null,
      breed,
      color,
      description: description.trim(),
      gender: gender as Gender,
      id: editingPet?.id ?? `pet-${Date.now()}`,
      image: sources[0],
      images: sources,
      microchipNumber: microchip === '완료' ? microchipNumber.trim() : '',
      name: name.trim(),
      neutered: neutered === '모름' ? null : neutered === '완료',
      species,
      weight: weight ? Number(weight) : null,
    };
    if (isLostReportFlow) {
      updateLostReportAnimal({
        age: pet.age,
        breed: pet.breed,
        color: pet.color,
        description: pet.description,
        gender: pet.gender,
        images: [...sources],
        microchipNumber: pet.microchipNumber,
        name: pet.name,
        neutered: pet.neutered,
        species: pet.species,
        weight: pet.weight,
      });
      navigation.navigate('LostReportForm', { reportId: route.params?.reportId });
      return;
    }
    if (isFoundReportFlow) {
      updateFoundReportDraft({
        age,
        breed,
        color,
        description: description.trim(),
        gender: gender === '수컷' ? 'male' : gender === '암컷' ? 'female' : 'unknown',
        images: photos,
        microchipNumber: microchip === '완료' ? microchipNumber.trim() : '',
        microchipStatus:
          microchip === '완료' ? 'done' : microchip === '미완료' ? 'not-done' : 'unknown',
        neutered: neutered === '완료' ? 'done' : neutered === '미완료' ? 'not-done' : 'unknown',
        species,
        weight,
      });
      navigation.navigate('FoundReportForm', { reportId: route.params?.reportId });
      return;
    }
    if (isEditing) {
      updatePet(pet);
    } else {
      addPet(pet);
    }
    navigation.goBack();
  };

  return (
    <SafeAreaView edges={['top', 'bottom']} style={styles.safeArea}>
      <View style={styles.header}>
        <Pressable
          accessibilityLabel="뒤로 가기"
          hitSlop={10}
          onPress={requestBack}
          style={styles.backButton}
        >
          <LeftArrowIcon height={24} width={24} />
        </Pressable>
        <Text style={styles.headerTitle}>
          {isFoundReportFlow
            ? '발견 동물 등록'
            : isLostReportFlow
            ? '실종 동물 등록'
            : isEditing
            ? '반려동물 수정하기'
            : '반려동물 등록하기'}
        </Text>
      </View>
      {isReportFlow ? (
        <View style={styles.lostReportIndicator}>
          <StepIndicator
            currentStep={isFoundReportFlow ? 1 : 2}
            totalSteps={isFoundReportFlow ? 3 : 4}
          />
        </View>
      ) : null}
      <KeyboardAwareInputProvider scrollRef={scrollRef}>
        <KeyboardAvoidingView
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
          style={styles.flex}
        >
          <ScrollView
            ref={scrollRef}
          automaticallyAdjustKeyboardInsets={Platform.OS === 'ios'}
          contentContainerStyle={styles.content}
          decelerationRate="fast"
          keyboardDismissMode="on-drag"
          keyboardShouldPersistTaps="handled"
          onTouchStart={Keyboard.dismiss}
        >
          <FieldLabel required>반려동물 사진</FieldLabel>
          <View style={styles.photoArea}>
            {photos.map((photo, index) => (
              <View key={`${photo.uri}-${index}`} style={styles.photoWrapper}>
                <Image source={{ uri: photo.uri }} style={styles.photo} />
                <Pressable
                  accessibilityLabel={`${index + 1}번째 사진 삭제`}
                  hitSlop={8}
                  onPress={() => setPhotos(items => items.filter((_, i) => i !== index))}
                  style={styles.deletePhoto}
                >
                  <XCircleIcon height={18} width={18} />
                </Pressable>
              </View>
            ))}
            {photos.length < 3 && (
              <Pressable
                accessibilityLabel="사진 추가"
                onPress={() => setPhotoMenuOpen(open => !open)}
                style={styles.addPhoto}
              >
                <PlusIcon height={24} width={24} />
              </Pressable>
            )}
            {photoMenuOpen && (
              <View style={styles.photoMenu}>
                <Pressable onPress={chooseFromLibrary} style={styles.photoMenuItem}>
                  <LibraryIcon height={24} width={24} />
                  <Text style={styles.photoMenuText}>사진 보관함</Text>
                </Pressable>
                <Pressable onPress={takePhoto} style={styles.photoMenuItem}>
                  <CameraIcon height={24} width={24} />
                  <Text style={styles.photoMenuText}>사진 찍기</Text>
                </Pressable>
              </View>
            )}
          </View>
          <Text style={styles.representativeGuide}>첫 번째 사진이 대표 사진으로 등록돼요.</Text>
          <Text style={styles.photoGuide}>
            최대 3장 · JPG/PNG · 장당 {MAX_IMAGE_SIZE_MB}MB 이하
          </Text>

          {!isReportFlow ? (
            <InputField
              label="이름"
              onChangeText={setName}
              placeholder="예: 두둥"
              required
              value={name}
            />
          ) : null}
          <SelectField
            isOpen={selectKey === 'species'}
            label="동물 종류"
            onPress={() => setSelectKey(current => (current === 'species' ? null : 'species'))}
            onSelect={selectOption}
            options={Object.keys(PET_OPTIONS)}
            required
            value={species}
          />
          {species && (
            <SelectField
              disabled={!species}
              isOpen={selectKey === 'breed'}
              label="품종"
              onPress={() => setSelectKey(current => (current === 'breed' ? null : 'breed'))}
              onSelect={selectOption}
              options={[...PET_OPTIONS[species].breeds]}
              required
              value={breed}
            />
          )}
          {breed && (
            <SelectField
              disabled={!breed}
              isOpen={selectKey === 'color'}
              label="색상"
              onPress={() => setSelectKey(current => (current === 'color' ? null : 'color'))}
              onSelect={selectOption}
              options={[...PET_OPTIONS[species as PetSpecies].colors]}
              required
              value={color}
            />
          )}
          <ChoiceField
            label="성별"
            onChange={value => setGender(value as Gender)}
            options={['수컷', '암컷', '모름']}
            required
            value={gender}
          />
          <ChoiceField
            label="중성화 여부"
            onChange={value => setNeutered(value as Neutered)}
            options={['완료', '미완료', '모름']}
            required
            value={neutered}
          />
          <View style={styles.row}>
            <View style={styles.half}>
              <InputField
                keyboardType="number-pad"
                label="나이"
                onChangeText={setAge}
                placeholder="예: 12살"
                value={age}
              />
            </View>
            <View style={styles.half}>
              <InputField
                keyboardType="decimal-pad"
                label="몸무게"
                onChangeText={setWeight}
                placeholder="예: 5kg"
                value={weight}
              />
            </View>
          </View>
          <InputField
            label="특징"
            multiline
            onChangeText={setDescription}
            placeholder="예: 꼬리 끝이 뭉툭함"
            value={description}
          />
          <ChoiceField
            label="마이크로칩(RFID) 등록 여부"
            onChange={value => {
              setMicrochip(value as Microchip);
              if (value !== '완료') {
                setMicrochipNumber('');
              }
            }}
            options={isFoundReportFlow ? ['완료', '미완료', '모름'] : ['완료', '미완료']}
            required
            value={microchip}
          />
          {microchip === '완료' && (
            <InputField
              keyboardType="number-pad"
              label="마이크로칩(RFID) 번호"
              onChangeText={setMicrochipNumber}
              placeholder="번호를 입력해주세요"
              required
              value={microchipNumber}
            />
          )}
          <Pressable
            disabled={!isValid}
            onPress={submit}
            style={[styles.submit, !isValid && styles.submitDisabled]}
          >
            <Text style={styles.submitText}>
              {isReportFlow ? '다음' : isEditing ? '수정하기' : '등록하기'}
            </Text>
          </Pressable>
          </ScrollView>
        </KeyboardAvoidingView>
      </KeyboardAwareInputProvider>
      <Modal
        animationType="fade"
        onRequestClose={() => setCancelModalOpen(false)}
        transparent
        visible={cancelModalOpen}
      >
        <View style={styles.cancelBackdrop}>
          <View style={styles.cancelModal}>
            <Text style={styles.cancelTitle}>
              {isReportFlow
                ? `${isFoundReportFlow ? '발견' : '실종'} 동물 등록을 취소하시겠습니까?`
                : `반려동물 ${isEditing ? '수정을' : '등록을'} 취소하시겠습니까?`}
            </Text>
            <Text style={styles.cancelDescription}>
              작성 중인 정보는 저장되지 않으며,{`\n`}다시 복구할 수 없습니다.
            </Text>
            <View style={styles.cancelActions}>
              <Pressable onPress={() => setCancelModalOpen(false)} style={styles.keepWritingButton}>
                <Text style={styles.cancelButtonText}>계속 작성하기</Text>
              </Pressable>
              <Pressable onPress={navigation.goBack} style={styles.confirmCancelButton}>
                <Text style={styles.cancelButtonText}>
                  {isEditing ? '수정 취소하기' : '등록 취소하기'}
                </Text>
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

function FieldLabel({ children, required }: { children: string; required?: boolean }) {
  return (
    <Text style={styles.label}>
      {children}
      {required ? '*' : ''}
    </Text>
  );
}

function InputField({
  label,
  required,
  multiline,
  ...props
}: React.ComponentProps<typeof KeyboardAwareTextInput> & { label: string; required?: boolean }) {
  return (
    <View style={styles.field}>
      <FieldLabel required={required}>{label}</FieldLabel>
      <KeyboardAwareTextInput
        placeholderTextColor={colors.gray400}
        style={[styles.input, multiline && styles.textArea]}
        {...props}
        multiline={multiline}
      />
    </View>
  );
}

function SelectField({
  disabled,
  isOpen,
  label,
  onPress,
  onSelect,
  options,
  required,
  value,
}: {
  disabled?: boolean;
  isOpen: boolean;
  label: string;
  onPress: () => void;
  onSelect: (value: string) => void;
  options: string[];
  required?: boolean;
  value: string;
}) {
  return (
    <View style={[styles.field, isOpen && styles.fieldOpen]}>
      <FieldLabel required={required}>{label}</FieldLabel>
      <Pressable
        disabled={disabled}
        onPress={onPress}
        style={[styles.select, disabled && styles.disabled, isOpen && styles.selectOpen]}
      >
        <Text style={[styles.selectText, !value && styles.placeholder]}>{value || '선택'}</Text>
        <FilterArrowIcon height={20} width={20} />
      </Pressable>
      {isOpen && (
        <View style={styles.dropdown}>
          {options.map(option => (
            <Pressable key={option} onPress={() => onSelect(option)} style={styles.dropdownOption}>
              <Text style={[styles.dropdownText, value === option && styles.dropdownTextSelected]}>
                {option}
              </Text>
            </Pressable>
          ))}
        </View>
      )}
    </View>
  );
}

function ChoiceField({
  label,
  onChange,
  options,
  required,
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  options: string[];
  required?: boolean;
  value: string;
}) {
  return (
    <View style={styles.field}>
      <FieldLabel required={required}>{label}</FieldLabel>
      <View style={styles.choiceRow}>
        {options.map(option => (
          <Pressable
            key={option}
            onPress={() => onChange(option)}
            style={[styles.choice, value === option && styles.choiceSelected]}
          >
            <Text style={[styles.choiceText, value === option && styles.choiceTextSelected]}>
              {option}
            </Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  safeArea: { backgroundColor: colors.white, flex: 1 },
  header: { alignItems: 'center', height: 56, justifyContent: 'center' },
  backButton: {
    alignItems: 'center',
    height: 40,
    justifyContent: 'center',
    left: 8,
    position: 'absolute',
    width: 40,
  },
  headerTitle: { ...typography.titleSemiBold18, color: colors.black },
  lostReportIndicator: { alignItems: 'center', height: 28, paddingTop: 2 },
  content: { paddingBottom: 30, paddingHorizontal: 26, paddingTop: 17 },
  label: { ...typography.bodySemiBold16, color: colors.black, marginBottom: 16 },
  photoArea: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    gap: 10,
    minHeight: 80,
    position: 'relative',
    zIndex: 3,
  },
  addPhoto: {
    alignItems: 'center',
    backgroundColor: colors.gray200,
    borderRadius: 10,
    height: 80,
    justifyContent: 'center',
    width: 80,
  },
  photoWrapper: { height: 80, position: 'relative', width: 80 },
  photo: { borderRadius: 10, height: 80, width: 80 },
  deletePhoto: { position: 'absolute', right: 2, top: 2 },
  photoMenu: {
    backgroundColor: colors.white,
    borderColor: colors.gray200,
    borderRadius: 8,
    borderWidth: 1,
    left: 0,
    paddingVertical: 6,
    position: 'absolute',
    top: 86,
    width: 160,
    zIndex: 10,
  },
  photoMenuItem: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 12,
    height: 48,
    paddingHorizontal: 18,
  },
  photoMenuText: { ...typography.bodyRegular16, color: colors.gray800 },
  representativeGuide: {
    ...typography.captionSemiBold12,
    color: colors.green400,
    marginTop: 10,
  },
  photoGuide: {
    ...typography.captionSemiBold12,
    color: colors.gray400,
    marginBottom: 24,
    marginTop: 2,
  },
  field: { marginBottom: 24, position: 'relative' },
  fieldOpen: { zIndex: 30 },
  input: {
    ...typography.bodyRegular16,
    borderColor: colors.gray200,
    borderRadius: radius.sm,
    borderWidth: 1,
    color: colors.gray800,
    height: 48,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  textArea: { height: 107, textAlignVertical: 'top' },
  select: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.sm,
    borderWidth: 1,
    flexDirection: 'row',
    height: 48,
    justifyContent: 'space-between',
    paddingHorizontal: 14,
  },
  disabled: { backgroundColor: colors.gray10 },
  selectText: { ...typography.bodyRegular16, color: colors.gray800 },
  placeholder: { color: colors.gray600 },
  selectOpen: { borderColor: colors.green400 },
  dropdown: {
    backgroundColor: colors.white,
    borderColor: colors.gray200,
    borderRadius: radius.sm,
    borderWidth: 1,
    left: 0,
    maxHeight: 300,
    overflow: 'hidden',
    position: 'absolute',
    right: 0,
    top: 85,
    zIndex: 20,
  },
  dropdownOption: {
    borderBottomColor: colors.gray200,
    borderBottomWidth: StyleSheet.hairlineWidth,
    paddingHorizontal: 14,
    paddingVertical: 13,
  },
  dropdownText: { ...typography.bodyRegular16, color: colors.gray700 },
  dropdownTextSelected: { color: colors.green400, fontFamily: 'Pretendard-SemiBold' },
  choiceRow: { flexDirection: 'row', gap: 12 },
  choice: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.sm,
    borderWidth: 1,
    flex: 1,
    height: 46,
    justifyContent: 'center',
  },
  choiceSelected: { backgroundColor: colors.green200, borderColor: colors.green200 },
  choiceText: { ...typography.bodyRegular14, color: colors.gray300 },
  choiceTextSelected: { color: colors.white, fontFamily: 'Pretendard-Medium' },
  row: { flexDirection: 'row', gap: 13 },
  half: { flex: 1 },
  submit: {
    alignItems: 'center',
    backgroundColor: colors.green400,
    borderRadius: radius.sm,
    height: 52,
    justifyContent: 'center',
    marginTop: 12,
  },
  submitDisabled: { backgroundColor: colors.gray200 },
  submitText: { ...typography.bodySemiBold16, color: colors.white },
  cancelBackdrop: {
    alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,0.35)',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  cancelModal: {
    backgroundColor: colors.white,
    borderRadius: 18,
    padding: 24,
    width: '100%',
  },
  cancelTitle: { ...typography.titleBold18, color: colors.gray800 },
  cancelDescription: {
    ...typography.bodyRegular14,
    color: colors.gray500,
    lineHeight: 20,
    marginTop: 12,
  },
  cancelActions: { flexDirection: 'row', gap: 12, marginTop: 22 },
  keepWritingButton: {
    alignItems: 'center',
    backgroundColor: colors.gray200,
    borderRadius: radius.sm,
    flex: 1,
    height: 44,
    justifyContent: 'center',
  },
  confirmCancelButton: {
    alignItems: 'center',
    backgroundColor: '#FA705B',
    borderRadius: radius.sm,
    flex: 1,
    height: 44,
    justifyContent: 'center',
  },
  cancelButtonText: { ...typography.bodySemiBold16, color: colors.white },
});
