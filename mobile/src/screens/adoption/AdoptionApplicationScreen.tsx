import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import { AppHeader, Button, StepIndicator } from '../../components/common';
import { colors, radius, spacing, typography } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'AdoptionApplication'>;
type FormState = {
  address: string;
  email: string;
  visitDate: string;
  housing: string;
  petAllowed: string;
  cohabitant: string;
  primaryCaretaker: string;
  livingSpace: string;
  aloneTime: string;
  currentPets: string;
  pastExperience: string;
  emergencyPlan: string;
  alternativeCare: string;
  motivation: string;
  dailyPlan: string;
  medicalPlan: string;
  changePlan: string;
  education: string;
  consentHealth: boolean;
  consentResponsibility: boolean;
  consentCost: boolean;
  consentAdaptation: boolean;
  consentVisit: boolean;
  consentReturn: boolean;
  consentPrivacy: boolean;
  consentShelter: boolean;
  consentPublic: boolean;
};

const initialForm: FormState = {
  address: '',
  email: '',
  visitDate: '',
  housing: '',
  petAllowed: '',
  cohabitant: '',
  primaryCaretaker: '',
  livingSpace: '',
  aloneTime: '',
  currentPets: '',
  pastExperience: '',
  emergencyPlan: '',
  alternativeCare: '',
  motivation: '',
  dailyPlan: '',
  medicalPlan: '',
  changePlan: '',
  education: '',
  consentHealth: false,
  consentResponsibility: false,
  consentCost: false,
  consentAdaptation: false,
  consentVisit: false,
  consentReturn: false,
  consentPrivacy: false,
  consentShelter: false,
  consentPublic: false,
};

const stepLabels = ['신청자 정보 기입', '주거 및 동거 환경', '돌봄 계획', '동의 여부'];

export function isAdoptionStepValid(form: FormState, step: number) {
  if (step === 1) {
    return Boolean(form.address && form.visitDate);
  }
  if (step === 2) {
    return Boolean(
      form.housing &&
        form.petAllowed &&
        form.cohabitant &&
        form.primaryCaretaker &&
        form.livingSpace &&
        form.aloneTime &&
        form.currentPets &&
        form.pastExperience &&
        form.emergencyPlan &&
        form.alternativeCare,
    );
  }
  if (step === 3) {
    return Boolean(
      form.motivation && form.dailyPlan && form.medicalPlan && form.changePlan && form.education,
    );
  }
  return (
    form.consentHealth &&
    form.consentResponsibility &&
    form.consentCost &&
    form.consentAdaptation &&
    form.consentVisit &&
    form.consentReturn &&
    form.consentPrivacy &&
    form.consentShelter
  );
}

function FieldLabel({ children }: { children: string }) {
  return <Text style={styles.label}>{children}</Text>;
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
          style={[styles.choice, value === option && styles.choiceSelected]}
        >
          <Text style={[styles.choiceText, value === option && styles.choiceTextSelected]}>
            {option}
          </Text>
        </Pressable>
      ))}
    </View>
  );
}

function FormInput({
  multiline = false,
  placeholder,
  value,
  onChangeText,
}: {
  multiline?: boolean;
  placeholder: string;
  value: string;
  onChangeText: (value: string) => void;
}) {
  return (
    <TextInput
      multiline={multiline}
      onChangeText={onChangeText}
      placeholder={placeholder}
      placeholderTextColor={colors.gray400}
      style={[styles.input, multiline && styles.textArea]}
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
      <View style={[styles.checkbox, value && styles.checkboxSelected]}>
        {value ? <Text style={styles.checkmark}>✓</Text> : null}
      </View>
      <Text style={styles.consentText}>{label}</Text>
    </Pressable>
  );
}

export function AdoptionApplicationScreen({ navigation }: Props) {
  const [step, setStep] = useState(1);
  const [form, setForm] = useState(initialForm);
  const [error, setError] = useState('');
  const update = <Key extends keyof FormState>(key: Key, value: FormState[Key]) =>
    setForm(current => ({ ...current, [key]: value }));

  const handleNext = () => {
    if (!isAdoptionStepValid(form, step)) {
      setError('필수 항목을 모두 입력해 주세요.');
      return;
    }
    setError('');
    if (step < 4) {
      setStep(current => current + 1);
    } else {
      setError('입력 확인이 완료되었습니다. 제출은 API 연동 후 제공됩니다.');
    }
  };

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <AppHeader
        onBackPress={step === 1 ? navigation.goBack : () => setStep(current => current - 1)}
        title="입양 신청"
      />
      <View style={styles.progress}>
        <StepIndicator currentStep={step} totalSteps={4} />
        <Text style={styles.progressLabel}>{stepLabels[step - 1]}</Text>
      </View>
      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        {step === 1 ? <StepOne form={form} update={update} /> : null}
        {step === 2 ? <StepTwo form={form} update={update} /> : null}
        {step === 3 ? <StepThree form={form} update={update} /> : null}
        {step === 4 ? <StepFour form={form} update={update} /> : null}
        {error ? (
          <Text accessibilityLiveRegion="polite" style={styles.error}>
            {error}
          </Text>
        ) : null}
        <View style={styles.actions}>
          <Button variant="secondary" style={styles.action}>
            임시저장
          </Button>
          <Button onPress={handleNext} style={styles.action}>
            {step === 4 ? '최종확인하기' : '다음'}
          </Button>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

type StepProps = {
  form: FormState;
  update: <Key extends keyof FormState>(key: Key, value: FormState[Key]) => void;
};

function StepOne({ form, update }: StepProps) {
  return (
    <>
      <View style={styles.animalSummary}>
        <Image
          source={require('../../assets/images/adoption/animal-detail.jpg')}
          style={styles.animalImage}
        />
        <View>
          <Text style={styles.animalTitle}>[개] 믹스견</Text>
          <Text style={styles.animalMeta}>2026년생 | 암컷 | 3kg</Text>
          <Text style={styles.animalMeta}>
            중성화 여부 <Text style={styles.greenText}>O</Text>
          </Text>
        </View>
        <View style={styles.numberRows}>
          <Text style={styles.numberLabel}>등록번호</Text>
          <Text style={styles.numberValue}>113122600015</Text>
          <Text style={styles.numberLabel}>마이크로칩번호</Text>
          <Text style={styles.numberValue}>410987654321098</Text>
        </View>
      </View>
      <Text style={styles.notice}>
        ⓘ 신청 후 보호센터의 상담과 심사를 거쳐 입양 여부가 결정됩니다.
      </Text>
      <Text style={styles.heading}>신청자 기본 정보</Text>
      <View style={styles.profileCard}>
        <Text style={styles.profileName}>
          우사기　<Text style={styles.profileMeta}>2000.00.00</Text>
        </Text>
        <Text style={styles.profileMeta}>☎ 010-1234-4567</Text>
      </View>
      <FieldLabel>실제 양육 예정 장소*</FieldLabel>
      <FormInput
        onChangeText={value => update('address', value)}
        placeholder="도로명, 건물명, 지번으로 검색"
        value={form.address}
      />
      <Button variant="secondary" style={styles.locationButton}>
        ⌾ 현재 위치로 찾기
      </Button>
      <FieldLabel>이메일</FieldLabel>
      <FormInput
        onChangeText={value => update('email', value)}
        placeholder="example@email.com"
        value={form.email}
      />
      <FieldLabel>방문 상담 희망일*</FieldLabel>
      <FormInput
        onChangeText={value => update('visitDate', value)}
        placeholder="2026.10.10"
        value={form.visitDate}
      />
    </>
  );
}

function StepTwo({ form, update }: StepProps) {
  const groups: Array<[keyof FormState, string, string[]]> = [
    ['housing', '주거 형태*', ['아파트', '단독주택', '다세대', '오피스텔', '기타']],
    ['petAllowed', '반려동물 양육 가능 여부*', ['가능', '불가능', '확인중']],
    ['cohabitant', '동거인 유무*', ['없음', '있음']],
    ['primaryCaretaker', '주 양육자*', ['본인', '동거인과 공동', '기타']],
    ['livingSpace', '주로 생활할 공간*', ['실내', '실외', '실내외 병행']],
    [
      'aloneTime',
      '반려동물이 하루 평균 혼자 있는 시간*',
      ['2시간 미만', '2~4시간', '4~8시간', '8시간 이상', '일정하지 않음'],
    ],
    ['currentPets', '현재 함께 사는 동물*', ['없음', '있음']],
    ['pastExperience', '과거 양육 경험*', ['없음', '있음']],
    ['emergencyPlan', '부재·응급상황의 대체 돌봄*', ['있음', '준비중', '없음']],
  ];
  return (
    <>
      <Text style={styles.heading}>주거 및 동거 환경</Text>
      {groups.map(([key, label, options]) => (
        <View key={key} style={styles.question}>
          <FieldLabel>{label}</FieldLabel>
          <ChoiceGroup
            onChange={value => update(key, value)}
            options={options}
            value={String(form[key])}
          />
        </View>
      ))}
      <FieldLabel>대체 돌봄 방법*</FieldLabel>
      <FormInput
        multiline
        onChangeText={value => update('alternativeCare', value)}
        placeholder="돌봄을 맡길 방법을 작성해 주세요"
        value={form.alternativeCare}
      />
    </>
  );
}

function StepThree({ form, update }: StepProps) {
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
      <FieldLabel>일상적인 돌봄 계획*</FieldLabel>
      <FormInput
        multiline
        onChangeText={value => update('dailyPlan', value)}
        placeholder="급식, 산책 및 놀이, 위생 관리, 초기 적응 계획 등"
        value={form.dailyPlan}
      />
      <FieldLabel>치료·행동 적응 지원 계획*</FieldLabel>
      <FormInput
        multiline
        onChangeText={value => update('medicalPlan', value)}
        placeholder="동물병원 진료, 전문가 상담, 치료비 마련 계획"
        value={form.medicalPlan}
      />
      <FieldLabel>생활환경 변화 시 돌봄 계획*</FieldLabel>
      <FormInput
        multiline
        onChangeText={value => update('changePlan', value)}
        placeholder="이사, 장기 부재 등의 상황에서 계속 돌볼 방법"
        value={form.changePlan}
      />
      <View style={styles.divider} />
      <Text style={styles.heading}>입양 전 교육</Text>
      <FieldLabel>교육 이수 여부*</FieldLabel>
      <ChoiceGroup
        onChange={value => update('education', value)}
        options={['예정', '미이수', '이수']}
        value={form.education}
      />
    </>
  );
}

function StepFour({ form, update }: StepProps) {
  const rows: Array<[keyof FormState, string]> = [
    [
      'consentHealth',
      '제공된 건강·행동 정보를 확인했으며, 미확인 사항은 상담 시 추가 안내받겠습니다.',
    ],
    [
      'consentResponsibility',
      '유기·학대하지 않고 적절한 먹이, 물, 생활공간과 돌봄을 제공하겠습니다.',
    ],
    ['consentCost', '질병이나 부상 발생 시 필요한 진료와 관리를 제공하겠습니다.'],
    ['consentAdaptation', '초기 적응과 행동 변화에 시간이 필요함을 이해했습니다.'],
    ['consentVisit', '보호센터와 상담 및 동물 만남 일정을 조율할 수 있습니다.'],
    ['consentReturn', '양육을 지속하기 어려워지면 보호센터와 먼저 상담하겠습니다.'],
    ['consentPrivacy', '[필수] 입양 신청 처리를 위한 개인정보 수집·이용에 동의합니다.'],
    ['consentShelter', '[조건부 필수] 보호센터 제공에 동의합니다.'],
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
      <Text style={styles.heading}>책임 있는 양육 확인</Text>
      {rows.slice(0, 6).map(([key, label]) => (
        <ConsentRow
          key={key}
          label={label}
          onChange={value => update(key, value)}
          value={Boolean(form[key])}
        />
      ))}
      <View style={styles.divider} />
      <Text style={styles.heading}>개인정보 안내 및 동의</Text>
      {rows.slice(6).map(([key, label]) => (
        <ConsentRow
          key={key}
          label={label}
          onChange={value => update(key, value)}
          value={Boolean(form[key])}
        />
      ))}
    </>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  progress: { alignItems: 'center', gap: 6, paddingBottom: 18 },
  progressLabel: { ...typography.bodySemiBold16, color: colors.green400 },
  content: { paddingBottom: 24, paddingHorizontal: 26 },
  heading: { ...typography.titleBold18, color: colors.black, marginBottom: 18, marginTop: 12 },
  label: { ...typography.bodySemiBold16, color: colors.black, marginBottom: 12, marginTop: 10 },
  input: {
    ...typography.bodyRegular16,
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    color: colors.gray700,
    height: 48,
    paddingHorizontal: 14,
  },
  textArea: { height: 98, paddingTop: 14 },
  choiceGroup: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  choice: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    justifyContent: 'center',
    minHeight: 42,
    minWidth: 96,
    paddingHorizontal: 14,
  },
  choiceSelected: { backgroundColor: '#3DDC97', borderColor: '#3DDC97' },
  choiceText: { ...typography.bodyRegular14, color: colors.gray400 },
  choiceTextSelected: { color: colors.white },
  question: { borderBottomColor: colors.gray200, borderBottomWidth: 1, paddingBottom: 18 },
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
    left: 96,
    position: 'absolute',
    top: -80,
  },
  animalMeta: {
    ...typography.bodyRegular14,
    color: colors.black,
    left: 96,
    position: 'relative',
    top: -54,
  },
  greenText: { color: colors.green400 },
  numberRows: { flexDirection: 'row', flexWrap: 'wrap', marginTop: -22 },
  numberLabel: { ...typography.bodyRegular14, color: colors.gray400, marginTop: 8, width: 95 },
  numberValue: { ...typography.bodyRegular14, color: colors.black, marginTop: 8, width: 220 },
  notice: { ...typography.captionRegular10, color: colors.gray300, marginVertical: 10 },
  profileCard: {
    borderColor: colors.gray300,
    borderRadius: radius.md,
    borderWidth: 1,
    gap: 8,
    padding: 16,
  },
  profileName: { ...typography.bodySemiBold16, color: colors.black },
  profileMeta: { ...typography.bodyRegular14, color: colors.gray500 },
  locationButton: {
    backgroundColor: colors.white,
    borderColor: colors.green400,
    borderWidth: 1,
    marginTop: 12,
  },
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
  checkmark: { color: colors.white, fontSize: 12 },
  consentText: { ...typography.bodyRegular14, color: colors.gray700, flex: 1 },
  error: { ...typography.bodyRegular14, color: colors.danger, marginTop: 16, textAlign: 'center' },
  actions: { flexDirection: 'row', gap: spacing[4], marginTop: 28 },
  action: { flex: 1 },
});
