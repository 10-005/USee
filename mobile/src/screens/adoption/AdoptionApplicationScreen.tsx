import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { Fragment, useEffect, useRef, useState, type ComponentRef } from 'react';
import {
  Image,
  Keyboard,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import type { TextInputProps } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import AlertCircleIcon from '../../assets/icons/common/alert-circle.svg';
import CalendarIcon from '../../assets/icons/common/calendar.svg';
import EditIcon from '../../assets/icons/common/edit.svg';
import LocationIcon from '../../assets/icons/common/location.svg';
import { CheckIndicator } from '../../components/common/CheckIndicator';
import PhoneIcon from '../../assets/icons/common/phone.svg';
import {
  AppHeader,
  Button,
  DatePickerModal,
  KeyboardAwareInputProvider,
  KeyboardAwareTextInput,
  StepIndicator,
} from '../../components/common';
import {
  createAdoptionDraft,
  deleteAdoptionDraft,
  getAdoptionApplication,
  getAdoptionQuestions,
  getMyAdoptionApplications,
  saveAdoptionDraft,
  submitAdoptionApplication,
  type AdoptionApplicationFormRequest,
  type AdoptionQuestion,
  type ShelterAnimalDetail,
} from '../../services/adoption';
import { ApiClientError, getApiErrorDisplayMessage } from '../../services/api';
import { useAdoptionApplicantStore } from '../../stores/adoptionApplicantStore';
import { useAdoptionAnimalStore } from '../../stores/adoptionAnimalStore';
import { useAdoptionLocationStore } from '../../stores/adoptionLocationStore';
import { useProfileStore } from '../../stores/profileStore';
import { colors, radius, spacing, typography } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'AdoptionApplication'>;
export type AdoptionApplicationForm = {
  address: string;
  addressLatitude: number | null;
  addressLongitude: number | null;
  applicantBirthDate: string;
  applicantName: string;
  applicantPhone: string;
  email: string;
  visitDate: string;
  housing: string;
  housingOwnership: string;
  petAllowed: string;
  cohabitant: string;
  cohabitantConsent: string;
  primaryCaretaker: string;
  livingSpace: string;
  aloneTime: string;
  currentPets: string;
  currentPetDetails: string;
  pastExperience: string;
  pastAnimal: string;
  pastReason: string;
  emergencyPlan: string;
  alternativeCare: string;
  motivation: string;
  dailyPlan: string;
  medicalPlan: string;
  changePlan: string;
  allergy: string;
  monthlyBudget: string;
  medicalConsent: string;
  education: string;
  educationInstitution: string;
  educationCourse: string;
  educationDate: string;
  consentHealth: boolean;
  consentResponsibility: boolean;
  consentCost: boolean;
  consentLostPrevention: boolean;
  consentNoCommercialUse: boolean;
  consentInformationCheck: boolean;
  consentBudget: boolean;
  consentAdaptation: boolean;
  consentVisit: boolean;
  consentReturn: boolean;
  consentPrivacy: boolean;
  consentShelter: boolean;
  consentPublic: boolean;
  finalConfirmation: boolean;
};

type FormState = AdoptionApplicationForm;

type ResumableDraft = {
  form: FormState;
  savedAt: string;
  step: number;
};

const initialForm: FormState = {
  address: '',
  addressLatitude: null,
  addressLongitude: null,
  applicantBirthDate: '',
  applicantName: '',
  applicantPhone: '',
  email: '',
  visitDate: '',
  housing: '',
  housingOwnership: '',
  petAllowed: '',
  cohabitant: '',
  cohabitantConsent: '',
  primaryCaretaker: '',
  livingSpace: '',
  aloneTime: '',
  currentPets: '',
  currentPetDetails: '',
  pastExperience: '',
  pastAnimal: '',
  pastReason: '',
  emergencyPlan: '',
  alternativeCare: '',
  motivation: '',
  dailyPlan: '',
  medicalPlan: '',
  changePlan: '',
  allergy: '',
  monthlyBudget: '',
  medicalConsent: '',
  education: '',
  educationInstitution: '',
  educationCourse: '',
  educationDate: '',
  consentHealth: false,
  consentResponsibility: false,
  consentCost: false,
  consentLostPrevention: false,
  consentNoCommercialUse: false,
  consentInformationCheck: false,
  consentBudget: false,
  consentAdaptation: false,
  consentVisit: false,
  consentReturn: false,
  consentPrivacy: false,
  consentShelter: false,
  consentPublic: false,
  finalConfirmation: false,
};

const stepLabels = ['신청자 정보 기입', '주거 및 동거 환경', '돌봄 계획', '동의 여부', '최종 확인'];
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const questionFieldByText: Array<[RegExp, keyof FormState]> = [
  [/주거\s*형태/, 'housing'],
  [/양육\s*가능/, 'petAllowed'],
  [/동거인.*동의/, 'cohabitantConsent'],
  [/동거인/, 'cohabitant'],
  [/주\s*양육자/, 'primaryCaretaker'],
  [/생활할\s*공간/, 'livingSpace'],
  [/혼자\s*있는\s*시간/, 'aloneTime'],
  [/종류.*마릿수|마릿수.*합사|합사.*계획/, 'currentPetDetails'],
  [/현재.*동물/, 'currentPets'],
  [/과거\s*양육\s*경험/, 'pastExperience'],
  [/과거.*동물.*기간/, 'pastAnimal'],
  [/함께하지\s*않는\s*이유/, 'pastReason'],
  [/대체\s*돌봄\s*방법|돌봄을\s*맡길\s*방법/, 'alternativeCare'],
  [/부재|응급상황/, 'emergencyPlan'],
  [/입양\s*동기/, 'motivation'],
  [/일상.*돌봄\s*계획/, 'dailyPlan'],
  [/치료|행동\s*적응/, 'medicalPlan'],
  [/생활환경\s*변화/, 'changePlan'],
  [/교육\s*이수\s*여부/, 'education'],
  [/교육기관/, 'educationInstitution'],
  [/교육\s*과정/, 'educationCourse'],
  [/이수일/, 'educationDate'],
];

const questionFieldBySeq: Partial<Record<number, keyof FormState>> = {
  1: 'housing',
  2: 'housingOwnership',
  3: 'petAllowed',
  4: 'cohabitant',
  5: 'cohabitantConsent',
  6: 'currentPets',
  7: 'currentPetDetails',
  8: 'pastExperience',
  9: 'aloneTime',
  10: 'primaryCaretaker',
  11: 'allergy',
  12: 'alternativeCare',
  13: 'motivation',
  14: 'monthlyBudget',
  15: 'changePlan',
  16: 'medicalConsent',
  17: 'education',
  18: 'educationInstitution',
  19: 'educationCourse',
  20: 'educationDate',
};

function getQuestionField(questionText: string) {
  return questionFieldByText.find(([pattern]) => pattern.test(questionText))?.[1];
}

function getQuestionFormField(question: AdoptionQuestion) {
  const field = questionFieldBySeq[question.questionSeq] ?? getQuestionField(question.questionText);
  if (question.questionType !== 'TEXT') {
    if (field === 'alternativeCare') {
      return 'emergencyPlan';
    }
    return field;
  }
  if (field === 'currentPets') {
    return 'currentPetDetails';
  }
  if (field === 'pastExperience') {
    return 'pastAnimal';
  }
  if (field === 'emergencyPlan') {
    return 'alternativeCare';
  }
  return field;
}

function getQuestionForField(questions: AdoptionQuestion[], field: keyof FormState) {
  return questions.find(question => getQuestionFormField(question) === field);
}

function toApiDate(value: string) {
  return value ? value.replace(/\./g, '-') : null;
}

function getAdoptionApplicationErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiClientError) {
    if (error.code === 'ADOPTION_ANIMAL_NOT_AVAILABLE') {
      return '지금은 이 보호동물의 입양 신청을 받지 않아요.';
    }
    if (error.code === 'ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS') {
      return '이미 진행 중인 입양 신청이 있어요.';
    }
  }
  return getApiErrorDisplayMessage(error, fallback);
}

function buildApplicationRequest(
  animalId: string,
  form: FormState,
  questions: AdoptionQuestion[],
): AdoptionApplicationFormRequest {
  const answers = questions.reduce<Record<string, string>>((result, question) => {
    const field = getQuestionFormField(question);
    if (!field) {
      return result;
    }
    const value = form[field];
    if (typeof value === 'string' && value.trim()) {
      if (
        (question.questionType === 'SINGLE_CHOICE' || question.questionType === 'MULTI_CHOICE') &&
        question.options &&
        !question.options.includes(value.trim())
      ) {
        return result;
      }
      result[String(question.questionSeq)] =
        question.questionType === 'DATE' ? value.replace(/\./g, '-') : value.trim();
    }
    return result;
  }, {});
  return {
    animalSeq: Number(animalId),
    answers,
    careAddress: form.address,
    email: form.email || null,
    visitHopeDate: toApiDate(form.visitDate),
  };
}

function formatSavedAt(savedAt: string) {
  const date = new Date(savedAt);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  return `${year}.${month}.${day} ${hour}:${minute}`;
}

export function isAdoptionStepValid(form: FormState, step: number) {
  if (step === 1) {
    return Boolean(
      form.address && form.visitDate && (!form.email || emailPattern.test(form.email.trim())),
    );
  }
  if (step === 2) {
    const baseValid = Boolean(
      form.housing &&
        form.housingOwnership &&
        form.petAllowed &&
        form.cohabitant &&
        form.primaryCaretaker &&
        form.aloneTime &&
        form.currentPets &&
        form.pastExperience &&
        form.allergy &&
        form.alternativeCare,
    );
    const cohabitantValid = form.cohabitant !== '있음' || Boolean(form.cohabitantConsent);
    const currentPetsValid = form.currentPets !== '있음' || Boolean(form.currentPetDetails);
    return baseValid && cohabitantValid && currentPetsValid;
  }
  if (step === 3) {
    const baseValid = Boolean(
      form.motivation &&
        form.monthlyBudget &&
        form.changePlan &&
        form.medicalConsent &&
        form.education,
    );
    const educationValid =
      form.education !== '이수' ||
      Boolean(form.educationInstitution && form.educationCourse && form.educationDate);
    return baseValid && educationValid;
  }
  if (step === 4) {
    return (
      form.consentHealth &&
      form.consentResponsibility &&
      form.consentCost &&
      form.consentLostPrevention &&
      form.consentNoCommercialUse &&
      form.consentInformationCheck &&
      form.consentBudget &&
      form.consentAdaptation &&
      form.consentVisit &&
      form.consentReturn &&
      form.consentPrivacy &&
      form.consentShelter
    );
  }
  return form.finalConfirmation;
}

function FieldLabel({ children, compact = false }: { children: string; compact?: boolean }) {
  return <Text style={[styles.label, compact && styles.compactLabel]}>{children}</Text>;
}

function ChoiceGroup({
  options,
  value,
  onChange,
}: {
  options: string[];
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <View style={styles.choiceGroup}>
      {options.map(option => (
        <Pressable
          key={option}
          onPress={() => onChange(option)}
          style={[
            styles.choice,
            options.length === 3 ? styles.choiceThird : styles.choiceHalf,
            value === option && styles.choiceSelected,
          ]}
        >
          <Text
            adjustsFontSizeToFit
            minimumFontScale={0.65}
            numberOfLines={1}
            style={[styles.choiceText, value === option && styles.choiceTextSelected]}
          >
            {option}
          </Text>
        </Pressable>
      ))}
    </View>
  );
}

function CalendarField({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const [calendarOpen, setCalendarOpen] = useState(false);
  const parsedValue = value.split('.').map(Number);
  const selectedValue = value
    ? new Date(parsedValue[0], parsedValue[1] - 1, parsedValue[2])
    : new Date();
  const openCalendar = () => {
    Keyboard.dismiss();
    setCalendarOpen(true);
  };

  return (
    <>
      <Pressable accessibilityRole="button" onPress={openCalendar} style={styles.calendarField}>
        <Text style={[styles.calendarText, !value && styles.placeholder]}>
          {value || '날짜를 선택해 주세요'}
        </Text>
        <CalendarIcon />
      </Pressable>
      <DatePickerModal
        minimumDate={new Date()}
        onClose={() => setCalendarOpen(false)}
        onConfirm={selectedDate => {
          const year = selectedDate.getFullYear();
          const month = String(selectedDate.getMonth() + 1).padStart(2, '0');
          const day = String(selectedDate.getDate()).padStart(2, '0');
          onChange(`${year}.${month}.${day}`);
          setCalendarOpen(false);
        }}
        value={selectedValue}
        visible={calendarOpen}
      />
    </>
  );
}

function FormInput({
  autoCapitalize,
  editable = true,
  keyboardType,
  maxLength,
  multiline = false,
  placeholder = '',
  value,
  onChangeText,
  compact = false,
}: {
  autoCapitalize?: TextInputProps['autoCapitalize'];
  editable?: boolean;
  keyboardType?: TextInputProps['keyboardType'];
  maxLength?: number;
  multiline?: boolean;
  placeholder?: string;
  value: string;
  onChangeText?: (value: string) => void;
  compact?: boolean;
}) {
  return (
    <KeyboardAwareTextInput
      autoCapitalize={autoCapitalize}
      editable={editable}
      keyboardType={keyboardType}
      maxLength={maxLength}
      multiline={multiline}
      onChangeText={onChangeText}
      placeholder={placeholder}
      placeholderTextColor={colors.gray400}
      style={[styles.input, multiline && styles.textArea, compact && styles.compactInput]}
      textAlignVertical={multiline ? 'top' : 'center'}
      value={value}
    />
  );
}

function ConsentRow({
  label,
  value,
  onChange,
}: {
  label: string;
  value: boolean;
  onChange: (value: boolean) => void;
}) {
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityState={{ checked: value }}
      onPress={() => onChange(!value)}
      style={styles.consentRow}
    >
      <CheckIndicator checked={value} />
      <Text style={styles.consentText}>{label}</Text>
    </Pressable>
  );
}

export function AdoptionApplicationScreen({ navigation, route }: Props) {
  const [step, setStep] = useState(1);
  const [form, setForm] = useState(initialForm);
  const [error, setError] = useState('');
  const [draftFeedback, setDraftFeedback] = useState('');
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [draftToResume, setDraftToResume] = useState<ResumableDraft | null>(null);
  const [draftApplicationSeq, setDraftApplicationSeq] = useState<number | null>(null);
  const [questions, setQuestions] = useState<AdoptionQuestion[]>([]);
  const [isSaving, setIsSaving] = useState(false);
  const scrollRef = useRef<ComponentRef<typeof ScrollView>>(null);
  const locationSelection = useAdoptionLocationStore(state => state.selection);
  const appliedLocationIdRef = useRef(locationSelection.id);
  const applicantResult = useAdoptionApplicantStore(state => state.result);
  const beginApplicantEditing = useAdoptionApplicantStore(state => state.beginEditing);
  const appliedApplicantIdRef = useRef(applicantResult.id);
  const profile = useProfileStore(state => state.profile);
  const loadProfile = useProfileStore(state => state.loadProfile);
  const animal = useAdoptionAnimalStore(state => state.detailsById[route.params.animalId]);
  const fetchAnimal = useAdoptionAnimalStore(state => state.fetchAnimal);
  const update = <Key extends keyof FormState>(key: Key, value: FormState[Key]) =>
    setForm(current => ({ ...current, [key]: value }));

  const handleNext = async () => {
    if (!isAdoptionStepValid(form, step)) {
      setError('필수 항목을 모두 입력해 주세요.');
      return;
    }
    setError('');
    if (step < 5) {
      setStep(current => current + 1);
    } else {
      try {
        setIsSaving(true);
        const submittedApplication = await submitAdoptionApplication(
          buildApplicationRequest(route.params.animalId, form, questions),
        );
        navigation.replace('AdoptionApplicationHistory', {
          animalId: route.params.animalId,
          applicationSeq: submittedApplication.applicationSeq,
          applicationStatus: submittedApplication.status,
          source: 'record',
        });
      } catch (submitError) {
        setError(getAdoptionApplicationErrorMessage(submitError, '신청서를 제출하지 못했습니다.'));
      } finally {
        setIsSaving(false);
      }
    }
  };
  const canContinue = isAdoptionStepValid(form, step) && questions.length > 0;

  const handleSaveDraft = async () => {
    try {
      setIsSaving(true);
      const request = buildApplicationRequest(route.params.animalId, form, questions);
      const savedDraft = draftApplicationSeq
        ? await saveAdoptionDraft(draftApplicationSeq, {
            answers: request.answers,
            careAddress: request.careAddress,
            careAddressDetail: request.careAddressDetail,
            email: request.email,
            visitHopeDate: request.visitHopeDate,
          })
        : await createAdoptionDraft(request);
      setDraftApplicationSeq(savedDraft.applicationSeq);
      setError('');
      setDraftFeedback(`${formatSavedAt(new Date().toISOString())} 임시저장되었습니다.`);
    } catch (saveError) {
      setDraftFeedback('');
      setError(getAdoptionApplicationErrorMessage(saveError, '임시저장에 실패했습니다.'));
    } finally {
      setIsSaving(false);
    }
  };

  const handlePrevious = () => {
    if (step === 1) {
      navigation.goBack();
      return;
    }
    setStep(current => current - 1);
  };

  const handleResumeDraft = () => {
    if (!draftToResume) {
      return;
    }
    setForm({ ...initialForm, ...draftToResume.form });
    setStep(Math.min(5, Math.max(1, draftToResume.step)));
    setDraftToResume(null);
    setDraftFeedback('저장된 신청서를 불러왔습니다.');
  };

  const deleteDraft = async () => {
    try {
      if (draftApplicationSeq) {
        await deleteAdoptionDraft(draftApplicationSeq);
      }
      setShowDeleteConfirm(false);
      setDraftToResume(null);
      setDraftApplicationSeq(null);
      setForm(initialForm);
      setStep(1);
      setError('');
      setDraftFeedback('임시저장된 신청서를 삭제했습니다.');
    } catch {
      setShowDeleteConfirm(false);
      setError('임시저장 삭제에 실패했습니다. 다시 시도해 주세요.');
    }
  };

  useEffect(() => {
    let active = true;
    Promise.all([
      getAdoptionQuestions(),
      getMyAdoptionApplications({
        animalSeq: Number(route.params.animalId),
        status: 'DRAFT',
      }),
    ])
      .then(async ([loadedQuestions, page]) => {
        if (!active) {
          return;
        }
        setQuestions(loadedQuestions);
        const draft = page.content[0];
        if (draft) {
          const detail = await getAdoptionApplication(draft.applicationSeq);
          if (!active) {
            return;
          }
          const restoredForm = { ...initialForm };
          restoredForm.address = detail.careAddress ?? '';
          restoredForm.email = detail.email ?? '';
          restoredForm.visitDate = detail.visitHopeDate?.replace(/-/g, '.') ?? '';
          restoredForm.applicantName = detail.applicant.name ?? '';
          restoredForm.applicantBirthDate = detail.applicant.birthDate?.replace(/-/g, '.') ?? '';
          restoredForm.applicantPhone = detail.applicant.phone ?? '';
          loadedQuestions.forEach(question => {
            const field = getQuestionFormField(question);
            const answer = detail.answers[String(question.questionSeq)];
            if (field && typeof answer === 'string') {
              (restoredForm as unknown as Record<string, unknown>)[field] =
                question.questionType === 'DATE' ? answer.replace(/-/g, '.') : answer;
            }
          });
          setDraftApplicationSeq(draft.applicationSeq);
          setForm(restoredForm);
          setDraftToResume(null);
          setDraftFeedback(
            `${formatSavedAt(
              draft.displayDttm ?? new Date().toISOString(),
            )} 임시저장 내용을 불러왔습니다.`,
          );
        }
      })
      .catch(loadError => {
        if (active) {
          setError(getApiErrorDisplayMessage(loadError, '입양 신청 정보를 불러오지 못했습니다.'));
        }
      });
    return () => {
      active = false;
    };
  }, [route.params.animalId]);

  useEffect(() => {
    loadProfile().catch(() => undefined);
  }, [loadProfile]);

  useEffect(() => {
    if (!animal) {
      fetchAnimal(route.params.animalId).catch(() => undefined);
    }
  }, [animal, fetchAnimal, route.params.animalId]);

  useEffect(() => {
    if (!profile.userSeq) {
      return;
    }
    setForm(current => ({
      ...current,
      applicantBirthDate: profile.birthDate,
      applicantName: profile.name,
      applicantPhone: profile.phone,
    }));
  }, [profile.birthDate, profile.name, profile.phone, profile.userSeq]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ animated: false, y: 0 });
  }, [step]);

  useEffect(() => {
    if (locationSelection.id === appliedLocationIdRef.current) {
      return;
    }
    appliedLocationIdRef.current = locationSelection.id;
    setForm(current => ({
      ...current,
      address: locationSelection.address,
      addressLatitude: locationSelection.latitude,
      addressLongitude: locationSelection.longitude,
    }));
    setError('');
  }, [locationSelection]);

  useEffect(() => {
    if (applicantResult.id === appliedApplicantIdRef.current) {
      return;
    }
    appliedApplicantIdRef.current = applicantResult.id;
    setForm(current => ({
      ...current,
      applicantBirthDate: applicantResult.birthDate,
      applicantName: applicantResult.name,
      applicantPhone: applicantResult.phone,
    }));
  }, [applicantResult]);

  return (
    <SafeAreaView edges={['top', 'bottom']} style={styles.safeArea}>
      <AppHeader
        onBackPress={step === 1 ? navigation.goBack : () => setStep(current => current - 1)}
        rightAction={
          <Pressable
            accessibilityLabel="입양 신청서 임시저장"
            accessibilityRole="button"
            disabled={isSaving || questions.length === 0}
            hitSlop={8}
            onPress={handleSaveDraft}
          >
            <Text style={styles.headerSaveText}>{isSaving ? '저장 중' : '임시저장'}</Text>
          </Pressable>
        }
        title="입양 신청"
      />
      <View style={styles.progress}>
        <StepIndicator currentStep={Math.min(step, 4)} totalSteps={4} />
        <Text style={styles.progressLabel}>{stepLabels[step - 1]}</Text>
      </View>
      <KeyboardAwareInputProvider scrollRef={scrollRef}>
        <KeyboardAvoidingView
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
          style={styles.keyboardArea}
        >
          <ScrollView
            ref={scrollRef}
            automaticallyAdjustKeyboardInsets={Platform.OS === 'ios'}
            contentContainerStyle={[styles.content, step === 5 && styles.finalContent]}
            decelerationRate="fast"
            keyboardDismissMode="on-drag"
            keyboardShouldPersistTaps="handled"
            onTouchStart={Keyboard.dismiss}
            scrollEnabled={step !== 5}
            showsVerticalScrollIndicator={false}
          >
            {step === 1 ? (
              <StepOne
                animal={animal}
                form={form}
                onEditApplicant={() => {
                  beginApplicantEditing({
                    birthDate: form.applicantBirthDate,
                    name: form.applicantName,
                    phone: form.applicantPhone,
                  });
                  navigation.navigate('AdoptionApplicantEdit');
                }}
                onOpenLocationPicker={() =>
                  navigation.navigate('LostReportLocationPicker', { target: 'adoption' })
                }
                update={update}
              />
            ) : null}
            {step === 2 ? <StepTwo form={form} questions={questions} update={update} /> : null}
            {step === 3 ? <StepThree form={form} questions={questions} update={update} /> : null}
            {step === 4 ? <StepFour form={form} update={update} /> : null}
            {step === 5 ? <StepFive form={form} update={update} /> : null}
            {error ? (
              <Text accessibilityLiveRegion="polite" style={styles.error}>
                {error}
              </Text>
            ) : null}
            {draftFeedback ? (
              <Text accessibilityLiveRegion="polite" style={styles.draftFeedback}>
                {draftFeedback}
              </Text>
            ) : null}
            {step < 5 ? (
              <View style={styles.actions}>
                <Button onPress={handlePrevious} variant="secondary" style={styles.action}>
                  이전
                </Button>
                <Button
                  disabled={!canContinue || isSaving}
                  onPress={handleNext}
                  style={[styles.action, !canContinue && styles.disabledNext]}
                >
                  {step === 4 ? '최종확인하기' : '다음'}
                </Button>
              </View>
            ) : (
              <View style={styles.finalFooter}>
                <View style={styles.submissionNotice}>
                  <AlertCircleIcon height={14} width={14} />
                  <Text style={styles.finalNoticeText}>
                    신청서가 보호센터로 전달됩니다. 제출만으로 입양이 확정되지 않습니다.
                  </Text>
                </View>
                <Button
                  disabled={!canContinue || isSaving}
                  onPress={handleNext}
                  style={!canContinue && styles.disabledNext}
                >
                  {isSaving ? '제출 중' : '입양 신청서 제출'}
                </Button>
              </View>
            )}
          </ScrollView>
        </KeyboardAvoidingView>
      </KeyboardAwareInputProvider>
      <Modal
        animationType="fade"
        onRequestClose={navigation.goBack}
        transparent
        visible={Boolean(draftToResume) && !showDeleteConfirm}
      >
        <View style={styles.draftOverlay}>
          <View style={styles.draftModal}>
            <Text style={styles.draftTitle}>작성 중인 신청서를 불러올까요?</Text>
            <Text style={styles.draftDescription}>
              저장한 단계부터 이어서 작성하거나 기존 내용을 삭제할 수 있습니다.
            </Text>
            {draftToResume ? (
              <Text style={styles.draftSavedAt}>
                마지막 저장 {formatSavedAt(draftToResume.savedAt)}
              </Text>
            ) : null}
            <View style={styles.draftActions}>
              <Button
                onPress={() => setShowDeleteConfirm(true)}
                style={[styles.draftAction, styles.deleteDraftButton]}
              >
                삭제하기
              </Button>
              <Button onPress={handleResumeDraft} style={styles.draftAction}>
                이어쓰기
              </Button>
            </View>
          </View>
        </View>
      </Modal>
      <Modal
        animationType="fade"
        onRequestClose={() => setShowDeleteConfirm(false)}
        transparent
        visible={showDeleteConfirm}
      >
        <View style={styles.draftOverlay}>
          <View style={styles.draftModal}>
            <Text style={styles.draftTitle}>임시저장한 신청서를 삭제할까요?</Text>
            <Text style={styles.draftDescription}>
              삭제 후에는 작성한 내용을 복구할 수 없습니다.
            </Text>
            <View style={[styles.draftActions, styles.deleteConfirmActions]}>
              <Button
                onPress={() => setShowDeleteConfirm(false)}
                style={[styles.draftAction, styles.cancelDeleteButton]}
              >
                취소
              </Button>
              <Button onPress={deleteDraft} style={[styles.draftAction, styles.deleteDraftButton]}>
                삭제하기
              </Button>
            </View>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

type StepProps = {
  form: FormState;
  update: <Key extends keyof FormState>(key: Key, value: FormState[Key]) => void;
};

function StepOne({
  animal,
  form,
  onEditApplicant,
  onOpenLocationPicker,
  update,
}: StepProps & {
  animal?: ShelterAnimalDetail;
  onEditApplicant: () => void;
  onOpenLocationPicker: () => void;
}) {
  const gender = animal?.gender === 'MALE' ? '수컷' : animal?.gender === 'FEMALE' ? '암컷' : '모름';
  const neutered =
    animal?.isNeutered === null || animal?.isNeutered === undefined
      ? '모름'
      : animal.isNeutered
      ? 'O'
      : 'X';
  return (
    <>
      <View style={styles.animalSummary}>
        <Image
          source={
            animal?.images[0]?.imageUrl
              ? { uri: animal.images[0].imageUrl }
              : require('../../assets/images/adoption/animal-dog-white.png')
          }
          style={styles.animalImage}
        />
        <View>
          <Text style={styles.animalTitle}>
            [{animal?.classification?.speciesName ?? '동물'}]{' '}
            {animal?.classification?.breedName ?? '품종 정보 없음'}
          </Text>
          <Text style={styles.animalMeta}>
            {animal?.age ?? '모름'} | {gender} | {animal?.weight ?? '모름'}
          </Text>
          <Text style={styles.animalMeta}>
            중성화 여부 <Text style={styles.greenText}>{neutered}</Text>
          </Text>
        </View>
        <View style={styles.numberRows}>
          <Text style={styles.numberLabel}>등록번호</Text>
          <Text style={styles.numberValue}>{animal?.registrationNumber ?? '미등록'}</Text>
          <Text style={styles.numberLabel}>마이크로칩번호</Text>
          <Text style={styles.numberValue}>{animal?.microchipNumber ?? '미등록'}</Text>
        </View>
      </View>
      <Text style={styles.notice}>
        ⓘ 신청 후 보호센터의 상담과 심사를 거쳐 입양 여부가 결정됩니다.
      </Text>
      <Text style={styles.heading}>신청자 기본 정보</Text>
      <View style={styles.profileCard}>
        <View style={styles.profileHeader}>
          <Text style={styles.profileName}>
            {form.applicantName}　<Text style={styles.profileMeta}>{form.applicantBirthDate}</Text>
          </Text>
          <Pressable
            accessibilityLabel="신청자 정보 수정"
            accessibilityRole="button"
            onPress={onEditApplicant}
          >
            <EditIcon height={20} width={20} />
          </Pressable>
        </View>
        <View style={styles.profileMetaRow}>
          <PhoneIcon />
          <Text style={styles.profileMeta}>{form.applicantPhone}</Text>
        </View>
      </View>
      <FieldLabel compact>실제 양육 예정 장소*</FieldLabel>
      {form.address ? <FormInput editable={false} value={form.address} /> : null}
      <Button
        onPress={onOpenLocationPicker}
        variant="secondary"
        style={[styles.locationButton, form.address && styles.locationButtonWithAddress]}
      >
        <View style={styles.locationButtonContent}>
          <LocationIcon />
          <Text style={styles.locationButtonText}>지도에서 위치 선택</Text>
        </View>
      </Button>
      <FieldLabel compact>이메일</FieldLabel>
      <FormInput
        autoCapitalize="none"
        keyboardType="email-address"
        onChangeText={value => update('email', value)}
        placeholder="example@email.com"
        value={form.email}
      />
      {form.email && !emailPattern.test(form.email.trim()) ? (
        <Text style={styles.fieldError}>이메일 형식에 맞게 입력해 주세요.</Text>
      ) : null}
      <FieldLabel compact>방문 상담 희망일*</FieldLabel>
      <CalendarField onChange={value => update('visitDate', value)} value={form.visitDate} />
    </>
  );
}

function formatToday() {
  const date = new Date();
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}.${month}.${day}`;
}

function StepFive({ form, update }: StepProps) {
  return (
    <>
      <Text style={styles.finalHeading}>최종 확인</Text>
      <ConsentRow
        label="작성한 내용이 사실임을 확인하며, 보호센터 상담·심사 후 입양이 결정됨을 이해합니다."
        onChange={value => update('finalConfirmation', value)}
        value={form.finalConfirmation}
      />
      <FieldLabel>신청자 성명</FieldLabel>
      <View style={styles.readOnlyField}>
        <Text style={styles.readOnlyText}>{form.applicantName}</Text>
      </View>
      <FieldLabel>작성일(자동)</FieldLabel>
      <View style={styles.readOnlyField}>
        <Text style={styles.readOnlyDate}>{formatToday()}</Text>
        <CalendarIcon height={20} width={20} />
      </View>
      <View style={styles.finalDivider} />
    </>
  );
}

function StepTwo({ form, questions, update }: StepProps & { questions: AdoptionQuestion[] }) {
  const optionsFor = (field: keyof FormState, fallback: string[]) =>
    getQuestionForField(questions, field)?.options ?? fallback;
  const groups: Array<[keyof FormState, string, string[]]> = [
    [
      'housing',
      '주거 형태*',
      optionsFor('housing', ['아파트', '단독주택', '다세대', '오피스텔', '기타']),
    ],
    [
      'housingOwnership',
      '주거 소유 형태*',
      optionsFor('housingOwnership', ['자가', '전세', '월세', '기타']),
    ],
    [
      'petAllowed',
      '반려동물 양육 가능 여부*',
      optionsFor('petAllowed', ['허용', '허용되지 않음', '확인 필요']),
    ],
    ['cohabitant', '동거인 유무*', optionsFor('cohabitant', ['없음', '있음'])],
    [
      'aloneTime',
      '반려동물이 하루 평균 혼자 있는 시간*',
      optionsFor('aloneTime', ['4시간 미만', '4~8시간', '8시간 이상']),
    ],
    ['currentPets', '현재 함께 사는 동물*', optionsFor('currentPets', ['없음', '있음'])],
    ['pastExperience', '과거 양육 경험*', optionsFor('pastExperience', ['없음', '있음'])],
    ['allergy', '동물 알레르기가 있는 가족*', optionsFor('allergy', ['있음', '없음', '모름'])],
  ];
  return (
    <>
      <Text style={styles.heading}>주거 및 동거 환경</Text>
      {groups.map(([key, label, options]) => {
        const showDivider = ['currentPets', 'pastExperience'].includes(key);
        return (
          <Fragment key={key}>
            {showDivider ? <View style={styles.divider} /> : null}
            <View style={styles.question}>
              <FieldLabel>{label}</FieldLabel>
              <ChoiceGroup
                onChange={value => update(key, value)}
                options={options}
                value={String(form[key])}
              />
            </View>
            {key === 'cohabitant' && form.cohabitant === '있음' ? (
              <View style={[styles.conditionalField, styles.cohabitantConsentField]}>
                <FieldLabel>동거인의 입양 동의*</FieldLabel>
                <ChoiceGroup
                  onChange={value => update('cohabitantConsent', value)}
                  options={optionsFor('cohabitantConsent', [
                    '모두 동의',
                    '일부 동의',
                    '동의하지 않음',
                  ])}
                  value={form.cohabitantConsent}
                />
              </View>
            ) : null}
            {key === 'currentPets' && form.currentPets === '있음' ? (
              <View style={styles.conditionalField}>
                <FieldLabel>함께 사는 동물 소개*</FieldLabel>
                <FormInput
                  compact
                  multiline
                  onChangeText={value => update('currentPetDetails', value)}
                  placeholder="종류·마릿수와 합사 계획을 입력해 주세요"
                  value={form.currentPetDetails}
                />
              </View>
            ) : null}
          </Fragment>
        );
      })}
      <FieldLabel>주로 돌볼 사람*</FieldLabel>
      <FormInput
        compact
        onChangeText={value => update('primaryCaretaker', value)}
        placeholder="주로 돌볼 사람을 입력해 주세요"
        value={form.primaryCaretaker}
      />
      <FieldLabel>이사·장기 부재 시 돌봄 계획*</FieldLabel>
      <FormInput
        compact
        multiline
        onChangeText={value => update('alternativeCare', value)}
        placeholder="계속 돌볼 방법을 작성해 주세요"
        value={form.alternativeCare}
      />
    </>
  );
}

function StepThree({ form, questions, update }: StepProps & { questions: AdoptionQuestion[] }) {
  return (
    <>
      <Text style={styles.heading}>입양 신청서</Text>
      <FieldLabel>입양 동기*</FieldLabel>
      <FormInput
        multiline
        onChangeText={value => update('motivation', value)}
        placeholder="입양을 결정한 계기와 이 동물을 선택한 이유"
        value={form.motivation}
      />
      <FieldLabel>한 달 예상 양육비*</FieldLabel>
      <ChoiceGroup
        onChange={value => update('monthlyBudget', value)}
        options={
          getQuestionForField(questions, 'monthlyBudget')?.options ?? [
            '10만 원 미만',
            '10~30만 원',
            '30만 원 이상',
          ]
        }
        value={form.monthlyBudget}
      />
      <FieldLabel>양육이 어려워질 경우 대처 계획*</FieldLabel>
      <FormInput
        multiline
        onChangeText={value => update('changePlan', value)}
        placeholder="보호센터 상담 등 대처 계획을 작성해 주세요"
        value={form.changePlan}
      />
      <FieldLabel>중성화·예방접종 동의 여부*</FieldLabel>
      <ChoiceGroup
        onChange={value => update('medicalConsent', value)}
        options={
          getQuestionForField(questions, 'medicalConsent')?.options ?? ['동의', '동의하지 않음']
        }
        value={form.medicalConsent}
      />
      <View style={styles.divider} />
      <Text style={styles.heading}>입양 전 교육</Text>
      <FieldLabel>교육 이수 여부*</FieldLabel>
      <ChoiceGroup
        onChange={value => update('education', value)}
        options={getQuestionForField(questions, 'education')?.options ?? ['예정', '미이수', '이수']}
        value={form.education}
      />
      {form.education === '이수' ? (
        <View style={styles.conditionalField}>
          <FieldLabel>교육기관*</FieldLabel>
          <FormInput
            onChangeText={value => update('educationInstitution', value)}
            placeholder="교육기관을 입력해 주세요"
            value={form.educationInstitution}
          />
          <FieldLabel>이수한 교육 과정*</FieldLabel>
          <FormInput
            onChangeText={value => update('educationCourse', value)}
            placeholder="이수한 교육 과정명을 입력해 주세요"
            value={form.educationCourse}
          />
          <FieldLabel>이수일*</FieldLabel>
          <CalendarField
            onChange={value => update('educationDate', value)}
            value={form.educationDate}
          />
        </View>
      ) : null}
    </>
  );
}

function StepFour({ form, update }: StepProps) {
  const responsibilityRows: Array<[keyof FormState, string]> = [
    [
      'consentResponsibility',
      '유기·학대하지 않고 적절한 먹이, 물, 생활공간과 돌봄을 제공하겠습니다.',
    ],
    ['consentCost', '질병이나 부상 발생 시 필요한 진료와 관리를 제공하겠습니다.'],
    [
      'consentLostPrevention',
      '유실 예방 조치를 하고, 대상 동물의 동물등록·변경신고 절차를 이행하겠습니다.',
    ],
    [
      'consentNoCommercialUse',
      '판매·번식·실험 등 영리 또는 부적절한 이용을 목적으로 신청하지 않습니다.',
    ],
    ['consentReturn', '양육을 지속하기 어려워지면 보호센터와 먼저 상담하겠습니다.'],
    [
      'consentInformationCheck',
      '작성 내용의 확인을 위해 추가 자료나 상담을 요청받을 수 있음을 이해했습니다.',
    ],
  ];
  const preparationRows: Array<[keyof FormState, string]> = [
    ['consentBudget', '사료·용품·예방접종·진료 등에 지속적인 비용이 발생함을 이해했습니다.'],
    ['consentAdaptation', '입양 초기에는 적응과 행동 변화에 시간과 노력이 필요함을 이해했습니다.'],
    ['consentVisit', '보호센터와 방문 상담 및 동물 만남 일정을 조율할 수 있습니다.'],
  ];
  const privacyRows: Array<[keyof FormState, string]> = [
    ['consentPrivacy', '[필수] 입양 신청 처리를 위한 개인정보 수집·이용에 동의합니다.'],
    ['consentShelter', '[필수] 보호센터 제공에 동의합니다.'],
    ['consentPublic', '[선택] 입양 후기의 서비스 공개에 동의합니다.'],
  ];
  return (
    <>
      <Text style={styles.heading}>동물 상태 안내</Text>
      <View style={styles.guideBox}>
        <Text style={styles.body}>
          건강검진 결과　<Text style={styles.warning}>미확인</Text>
        </Text>
        <Text style={styles.body}>예방접종 여부　코로나 접종, 0000 접종</Text>
        <Text style={styles.body}>행동 특성　　　경계심이 살짝 있지만 순한 편</Text>
      </View>
      <ConsentRow
        label="[필수] 제공된 건강·행동 정보를 확인했으며, 미확인 사항은 상담 시 추가 안내받겠습니다."
        onChange={value => update('consentHealth', value)}
        value={form.consentHealth}
      />
      <View style={styles.divider} />
      <Text style={styles.heading}>책임 있는 양육 확인</Text>
      {responsibilityRows.map(([key, label]) => (
        <ConsentRow
          key={key}
          label={label}
          onChange={value => update(key, value)}
          value={Boolean(form[key])}
        />
      ))}
      <View style={styles.divider} />
      <Text style={styles.heading}>돌봄 준비 확인</Text>
      {preparationRows.map(([key, label]) => (
        <ConsentRow
          key={key}
          label={label}
          onChange={value => update(key, value)}
          value={Boolean(form[key])}
        />
      ))}
      <View style={styles.divider} />
      <Text style={styles.heading}>개인정보 안내 및 동의</Text>
      <View style={styles.guideBox}>
        <Text style={styles.body}>1. 목적 - 입양 접수·상담·심사·결과 안내</Text>
        <Text style={styles.body}>2. 항목 - 신청자 정보 및 신청서 응답</Text>
        <Text style={styles.body}>3. 보유 기간 - 서비스 운영 정책에 따름</Text>
      </View>
      {privacyRows.map(([key, label]) => (
        <ConsentRow
          key={key}
          label={label}
          onChange={value => update(key, value)}
          value={Boolean(form[key])}
        />
      ))}
      <View style={styles.submissionNotice}>
        <AlertCircleIcon />
        <Text style={styles.submissionNoticeText}>
          신청서가 보호센터로 전달됩니다. 제출만으로 입양이 확정되지 않습니다.
        </Text>
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  keyboardArea: { flex: 1 },
  headerSaveText: {
    ...typography.bodySemiBold16,
    color: colors.green400,
    fontSize: 14,
    lineHeight: 18,
  },
  progress: { alignItems: 'center', gap: 6, paddingBottom: 18 },
  progressLabel: {
    ...typography.bodySemiBold16,
    color: colors.green400,
    fontSize: 14,
    lineHeight: 18,
  },
  content: { paddingBottom: 24, paddingHorizontal: 26 },
  finalContent: { flexGrow: 1 },
  heading: { ...typography.titleBold18, color: colors.black, marginBottom: 18, marginTop: 12 },
  label: { ...typography.bodySemiBold16, color: colors.black, marginBottom: 12, marginTop: 10 },
  compactLabel: { ...typography.bodyRegular14, fontFamily: 'Pretendard-SemiBold' },
  input: {
    ...typography.bodyRegular16,
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    color: colors.gray700,
    height: 48,
    paddingHorizontal: 14,
  },
  compactInput: { fontSize: 14, lineHeight: 20 },
  textArea: { height: 98, paddingTop: 14 },
  choiceGroup: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  choice: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    justifyContent: 'center',
    minHeight: 42,
    paddingHorizontal: 14,
  },
  choiceSelected: { backgroundColor: '#3DDC97', borderColor: '#3DDC97' },
  choiceHalf: { width: '48.8%' },
  choiceThird: { width: '31.8%' },
  choiceText: {
    ...typography.bodyRegular14,
    color: colors.gray400,
    flexShrink: 1,
    textAlign: 'center',
    width: '100%',
  },
  choiceTextSelected: { color: colors.white },
  question: { marginBottom: 14 },
  conditionalField: { marginTop: 2 },
  cohabitantConsentField: { marginBottom: 14 },
  conditionalQuestionGap: { height: 8 },
  pastExperienceFields: { marginBottom: 14 },
  animalSummary: {
    backgroundColor: colors.gray10,
    borderRadius: radius.md,
    minHeight: 168,
    padding: 16,
  },
  animalImage: { borderRadius: radius.lg, height: 80, width: 80 },
  animalTitle: {
    ...typography.titleSemiBold18,
    color: colors.black,
    left: 104,
    position: 'absolute',
    top: -80,
  },
  animalMeta: {
    ...typography.bodyRegular14,
    color: colors.black,
    left: 104,
    lineHeight: 22,
    position: 'relative',
    top: -51,
  },
  greenText: { color: colors.green400 },
  numberRows: { flexDirection: 'row', flexWrap: 'wrap', marginTop: -32 },
  numberLabel: {
    ...typography.bodyRegular14,
    color: colors.gray400,
    fontSize: 12,
    lineHeight: 15,
    marginTop: 8,
    width: '35%',
  },
  numberValue: {
    ...typography.bodyRegular14,
    color: colors.black,
    fontSize: 12,
    lineHeight: 15,
    marginTop: 8,
    width: '65%',
  },
  notice: {
    ...typography.bodyRegular14,
    color: colors.gray300,
    fontSize: 12,
    lineHeight: 15,
    marginVertical: 10,
  },
  profileCard: {
    borderColor: colors.gray300,
    borderRadius: radius.md,
    borderWidth: 1,
    gap: 8,
    padding: 16,
  },
  profileName: { ...typography.bodySemiBold16, color: colors.black },
  profileHeader: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  profileMeta: { ...typography.bodyRegular14, color: colors.gray500 },
  profileMetaRow: { alignItems: 'center', flexDirection: 'row', gap: 6 },
  calendarField: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    height: 48,
    justifyContent: 'space-between',
    paddingHorizontal: 14,
  },
  calendarText: { ...typography.bodyRegular16, color: colors.gray700 },
  placeholder: { color: colors.gray400 },
  fieldError: {
    ...typography.captionSemiBold12,
    color: colors.danger,
    fontFamily: 'Pretendard-Regular',
    marginTop: 6,
  },
  locationButton: {
    backgroundColor: colors.white,
    borderColor: colors.green400,
    borderWidth: 1,
  },
  locationButtonWithAddress: { marginTop: 12 },
  locationButtonContent: { alignItems: 'center', flexDirection: 'row', gap: 8 },
  locationButtonText: { ...typography.bodySemiBold16, color: colors.green400 },
  divider: { backgroundColor: colors.gray200, height: 1, marginVertical: 20 },
  guideBox: { backgroundColor: colors.gray10, borderRadius: radius.md, gap: 10, padding: 14 },
  body: { ...typography.bodyRegular14, color: colors.gray700 },
  warning: { color: '#FA705B' },
  consentRow: { alignItems: 'flex-start', flexDirection: 'row', gap: 8, marginVertical: 9 },
  checkbox: {
    alignItems: 'center',
    borderColor: colors.gray300,
    borderRadius: 2,
    borderWidth: 1,
    height: 18,
    justifyContent: 'center',
    width: 18,
  },
  checkboxSelected: { backgroundColor: colors.green400, borderColor: colors.green400 },
  consentText: { ...typography.bodyRegular14, color: colors.gray700, flex: 1 },
  submissionNotice: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 6,
    justifyContent: 'center',
    marginTop: 16,
  },
  submissionNoticeText: { ...typography.captionSemiBold12, color: colors.gray400 },
  finalNoticeText: {
    ...typography.captionSemiBold12,
    color: colors.gray300,
    flex: 1,
    fontFamily: 'Pretendard-Regular',
  },
  error: { ...typography.bodyRegular14, color: colors.danger, marginTop: 16, textAlign: 'center' },
  draftFeedback: {
    ...typography.bodyRegular14,
    color: colors.green400,
    marginTop: 16,
    textAlign: 'center',
  },
  actions: { flexDirection: 'row', gap: spacing[4], marginTop: 28 },
  action: { flex: 1 },
  disabledNext: { backgroundColor: colors.gray200, opacity: 1 },
  finalHeading: {
    ...typography.titleBold18,
    color: colors.black,
    fontSize: 20,
    lineHeight: 26,
    marginBottom: 16,
  },
  readOnlyField: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    height: 48,
    justifyContent: 'space-between',
    paddingHorizontal: 14,
  },
  readOnlyText: { ...typography.bodyRegular16, color: colors.gray800 },
  readOnlyDate: { ...typography.bodyRegular16, color: colors.gray500 },
  finalDivider: { backgroundColor: colors.gray300, height: 1, marginTop: 24 },
  finalFooter: { gap: 16, marginTop: 'auto', paddingBottom: 12, paddingTop: 32 },
  draftOverlay: {
    alignItems: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.35)',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 26,
  },
  draftModal: {
    backgroundColor: colors.white,
    borderRadius: 24,
    padding: 24,
    width: '100%',
  },
  draftTitle: { ...typography.titleBold18, color: colors.gray800, fontSize: 20, lineHeight: 26 },
  draftDescription: {
    ...typography.bodyRegular14,
    color: colors.gray500,
    marginTop: 10,
  },
  draftSavedAt: {
    ...typography.captionSemiBold12,
    color: colors.gray400,
    marginBottom: 20,
    marginTop: 8,
  },
  draftActions: { flexDirection: 'row', gap: 14 },
  deleteConfirmActions: { marginTop: 22 },
  draftAction: { flex: 1 },
  cancelDeleteButton: { backgroundColor: colors.gray200 },
  deleteDraftButton: { backgroundColor: '#FF6C5B' },
});
