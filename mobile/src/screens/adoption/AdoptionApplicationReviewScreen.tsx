import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import FilterArrowIcon from '../../assets/icons/common/filter-arrow.svg';
import { AppHeader, DetailScreenSkeleton } from '../../components/common';
import {
  type AdoptionApplicationDetail,
  type AdoptionAnswer,
  getAdoptionApplication,
} from '../../services/adoption';
import { colors, radius, spacing, typography } from '../../theme';
import type { AdoptionApplicationForm } from './AdoptionApplicationScreen';

type Props = NativeStackScreenProps<RootStackParamList, 'AdoptionApplicationReview'>;

type Answer = {
  label: string;
  value?: string;
};

type Section = {
  answers: Answer[];
  title: string;
};

const EMPTY_FORM: AdoptionApplicationForm = {
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
  allergy: '',
  motivation: '',
  monthlyBudget: '',
  medicalConsent: '',
  dailyPlan: '',
  medicalPlan: '',
  changePlan: '',
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

function display(value?: string) {
  return value?.trim() || '-';
}

function answerText(value?: AdoptionAnswer) {
  if (Array.isArray(value)) {
    return value.join(', ');
  }
  return value ?? '';
}

const applicationQuestionLabels: Record<number, string> = {
  1: '주거 형태',
  2: '주거 소유 형태',
  3: '반려동물 양육 가능 여부',
  4: '동거인 유무',
  5: '동거인의 입양 동의',
  6: '현재 함께 사는 동물',
  7: '함께 사는 동물 소개',
  8: '과거 양육 경험',
  9: '반려동물이 하루 평균 혼자 있는 시간',
  10: '주로 돌볼 사람',
  11: '동물 알레르기가 있는 가족',
  12: '이사·장기 부재 시 돌봄 계획',
  13: '입양 동기',
  14: '한 달 예상 양육비',
  15: '양육이 어려워질 경우 대처 계획',
  16: '중성화·예방접종 동의 여부',
  17: '교육 이수 여부',
  18: '교육기관',
  19: '이수한 교육 과정',
  20: '이수일',
};

function buildApiSections(application: AdoptionApplicationDetail): Section[] {
  const questionAnswers = application.questions
    .slice()
    .sort((a, b) => a.displayOrder - b.displayOrder)
    .map(question => ({
      label: applicationQuestionLabels[question.questionSeq] ?? question.questionText,
      value: answerText(application.answers[String(question.questionSeq)]),
      order: question.displayOrder,
    }));
  const section = (title: string, from: number, to: number): Section => ({
    title,
    answers: questionAnswers
      .filter(answer => answer.order >= from && answer.order <= to)
      .map(({ label, value }) => ({ label, value })),
  });

  return [
    {
      title: '신청자 기본 정보',
      answers: [
        { label: '신청자', value: application.applicant.name ?? '' },
        { label: '생년월일', value: application.applicant.birthDate ?? '' },
        { label: '연락처', value: application.applicant.phone ?? '' },
        {
          label: '실제 양육 예정 장소',
          value: [application.careAddress, application.careAddressDetail].filter(Boolean).join(' '),
        },
        { label: '이메일', value: application.email ?? '' },
        { label: '방문 상담 희망일', value: application.visitHopeDate ?? '' },
      ],
    },
    section('주거 및 동거 환경', 1, 12),
    section('입양 신청서', 13, 16),
    section('입양 전 교육', 17, 20),
  ].filter(item => item.answers.length > 0);
}

function buildSections(form: AdoptionApplicationForm): Section[] {
  const housingAnswers: Answer[] = [
    { label: '주거 형태', value: form.housing },
    { label: '반려동물 양육 가능 여부', value: form.petAllowed },
    { label: '동거인 유무', value: form.cohabitant },
  ];
  if (form.cohabitant === '있음') {
    housingAnswers.push({ label: '동거인의 입양 동의', value: form.cohabitantConsent });
  }
  housingAnswers.push(
    { label: '주 양육자', value: form.primaryCaretaker },
    { label: '주로 생활할 공간', value: form.livingSpace },
    { label: '하루 평균 혼자 있는 시간', value: form.aloneTime },
  );

  const careAnswers: Answer[] = [{ label: '현재 함께 사는 동물', value: form.currentPets }];
  if (form.currentPets === '있음') {
    careAnswers.push({
      label: '동물 종류와 마릿수 및 합사 준비',
      value: form.currentPetDetails,
    });
  }
  careAnswers.push({ label: '과거 양육 경험', value: form.pastExperience });
  if (form.pastExperience === '있음') {
    careAnswers.push(
      { label: '과거 양육 동물', value: form.pastAnimal },
      { label: '과거 동물과 함께하지 않는 이유', value: form.pastReason },
    );
  }
  careAnswers.push(
    { label: '비상상황 발생 시 돌봄 계획', value: form.emergencyPlan },
    { label: '부재 시 돌봄 계획', value: form.alternativeCare },
  );

  const educationAnswers: Answer[] = [{ label: '교육 이수 여부', value: form.education }];
  if (form.education === '이수') {
    educationAnswers.push(
      { label: '교육기관', value: form.educationInstitution },
      { label: '이수한 교육 과정', value: form.educationCourse },
      { label: '이수일', value: form.educationDate },
    );
  }

  return [
    {
      title: '신청자 기본 정보',
      answers: [
        { label: '신청자', value: form.applicantName },
        { label: '생년월일', value: form.applicantBirthDate },
        { label: '연락처', value: form.applicantPhone },
        { label: '현재 거주지', value: form.address },
        { label: '이메일', value: form.email },
        { label: '방문 상담 희망일', value: form.visitDate },
      ],
    },
    { title: '주거 및 동거 환경', answers: housingAnswers },
    { title: '양육 경험 및 돌봄 환경', answers: careAnswers },
    {
      title: '입양 및 돌봄 계획',
      answers: [
        { label: '입양 동기', value: form.motivation },
        { label: '일상 돌봄 계획', value: form.dailyPlan },
        { label: '의료비 및 건강 관리 계획', value: form.medicalPlan },
        { label: '생활환경 변화 시 계획', value: form.changePlan },
      ],
    },
    { title: '입양 전 교육', answers: educationAnswers },
  ];
}

export function AdoptionApplicationReviewScreen({ navigation, route }: Props) {
  const [application, setApplication] = useState<AdoptionApplicationDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  useEffect(() => {
    setLoading(true);
    setError(false);
    getAdoptionApplication(route.params.applicationSeq)
      .then(setApplication)
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, [route.params.applicationSeq]);

  const sections = application ? buildApiSections(application) : buildSections(EMPTY_FORM);
  const toggleSection = (index: number) => {
    setExpanded(current => {
      const next = new Set(current);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
  };

  return (
    <SafeAreaView edges={['top', 'bottom']} style={styles.safeArea}>
      <AppHeader onBackPress={navigation.goBack} title="" />
      {loading ? (
        <DetailScreenSkeleton accessibilityLabel="입양 신청서 불러오는 중" />
      ) : error || !application ? (
        <View style={styles.errorState}>
          <Text style={styles.errorTitle}>입양 신청서를 불러오지 못했어요</Text>
          <Text style={styles.errorDescription}>잠시 후 다시 시도해 주세요</Text>
        </View>
      ) : (
      <ScrollView
        contentContainerStyle={styles.content}
        decelerationRate="fast"
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.animalCard}>
          <Image
            source={
              application.animalSummary?.representativeImageUrl
                ? { uri: application.animalSummary.representativeImageUrl }
                : require('../../assets/images/adoption/animal-detail.jpg')
            }
            style={styles.animalImage}
          />
          <View style={styles.animalSummary}>
            <Text style={styles.animalTitle}>
              [{application.animalSummary?.speciesName ?? '동물'}]{' '}
              {application.animalSummary?.breedName ?? '품종 정보 없음'}
            </Text>
            <Text style={styles.animalMeta}>
              {[application.animalSummary?.age, application.animalSummary?.gender,
                application.animalSummary?.weight].filter(Boolean).join(' | ') || '-'}
            </Text>
            <Text style={styles.animalMeta}>
              중성화 여부{' '}
              <Text style={styles.neutered}>
                {application.animalSummary?.isNeutered == null
                  ? '-'
                  : application.animalSummary.isNeutered
                    ? 'O'
                    : 'X'}
              </Text>
            </Text>
          </View>
          <View style={styles.identifiers}>
            <View style={styles.identifierRow}>
              <Text style={styles.identifierLabel}>등록번호</Text>
              <Text style={styles.identifierValue}>
                {application.animalSummary?.registrationNumber ?? '-'}
              </Text>
            </View>
            <View style={styles.identifierRow}>
              <Text style={styles.identifierLabel}>마이크로칩번호</Text>
              <Text style={styles.identifierValue}>
                {application.animalSummary?.microchipNumber ?? '-'}
              </Text>
            </View>
          </View>
        </View>

        <View style={styles.sections}>
          {sections.map((section, index) => {
            const isExpanded = expanded.has(index);
            return (
              <View
                key={section.title}
                style={[styles.section, index === sections.length - 1 && styles.lastSection]}
              >
                <Pressable
                  accessibilityRole="button"
                  onPress={() => toggleSection(index)}
                  style={styles.sectionHeader}
                >
                  <Text style={styles.sectionNumber}>{String(index + 1).padStart(2, '0')}</Text>
                  <Text style={styles.sectionTitle}>{section.title}</Text>
                  <View style={[styles.arrow, isExpanded && styles.arrowExpanded]}>
                    <FilterArrowIcon height={18} width={18} />
                  </View>
                </Pressable>
                {isExpanded ? (
                  <View style={styles.answerList}>
                    {section.answers.map(answer => (
                      <View key={answer.label} style={styles.answer}>
                        <Text style={styles.answerLabel}>{answer.label}</Text>
                        <Text style={styles.answerValue}>{display(answer.value)}</Text>
                      </View>
                    ))}
                  </View>
                ) : null}
              </View>
            );
          })}
        </View>
      </ScrollView>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  errorState: { alignItems: 'center', flex: 1, justifyContent: 'center', padding: spacing[8] },
  errorTitle: { ...typography.bodySemiBold16, color: colors.gray700 },
  errorDescription: { ...typography.bodyRegular14, color: colors.gray400, marginTop: spacing[2] },
  content: { paddingBottom: spacing[16], paddingHorizontal: spacing[8] },
  animalCard: {
    backgroundColor: colors.gray10,
    borderRadius: radius.md,
    minHeight: 164,
    padding: spacing[8],
    position: 'relative',
  },
  animalImage: { borderRadius: radius.md, height: 86, width: 86 },
  animalSummary: { left: 118, position: 'absolute', top: 18 },
  animalTitle: { ...typography.titleBold18, color: colors.black, marginBottom: spacing[2] },
  animalMeta: { ...typography.bodyRegular14, color: colors.gray700, marginTop: spacing[1] },
  neutered: { color: colors.green400 },
  identifiers: { gap: spacing[3], marginTop: spacing[7] },
  identifierRow: { flexDirection: 'row', justifyContent: 'space-between' },
  identifierLabel: { ...typography.bodyRegular14, color: colors.gray400 },
  identifierValue: { ...typography.bodyRegular14, color: colors.gray700 },
  sections: { marginHorizontal: spacing[4], marginTop: spacing[4] },
  section: { borderBottomColor: colors.gray200, borderBottomWidth: StyleSheet.hairlineWidth },
  lastSection: { borderBottomWidth: 0 },
  sectionHeader: { alignItems: 'center', flexDirection: 'row', minHeight: 62 },
  sectionNumber: { ...typography.captionSemiBold12, color: colors.gray400, width: 32 },
  sectionTitle: { ...typography.bodySemiBold16, color: colors.gray700, flex: 1 },
  arrow: { transform: [{ rotate: '0deg' }] },
  arrowExpanded: { transform: [{ rotate: '180deg' }] },
  answerList: {
    gap: spacing[8],
    paddingBottom: spacing[10],
    paddingLeft: 32,
    paddingRight: spacing[4],
  },
  answer: { gap: spacing[2] },
  answerLabel: { ...typography.captionSemiBold12, color: colors.gray400 },
  answerValue: { ...typography.bodyRegular14, color: colors.gray700 },
});
