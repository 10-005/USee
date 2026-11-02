import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import FilterArrowIcon from '../../assets/icons/common/filter-arrow.svg';
import { AppHeader } from '../../components/common';
import { getAdoptionApplicationRecord } from '../../services/storage/adoptionApplicationStorage';
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
  email: '',
  visitDate: '',
  housing: '',
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
        { label: '신청자', value: '우사기' },
        { label: '생년월일', value: '2001.05.27' },
        { label: '연락처', value: '010-1234-5678' },
        { label: '현재 거주지', value: form.address },
        { label: '상세주소', value: '-' },
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

export function AdoptionApplicationReviewScreen({ navigation }: Props) {
  const [form, setForm] = useState<AdoptionApplicationForm>(EMPTY_FORM);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  useEffect(() => {
    getAdoptionApplicationRecord<AdoptionApplicationForm>().then(record => {
      if (record?.form) {
        setForm({ ...EMPTY_FORM, ...record.form });
      }
    });
  }, []);

  const sections = buildSections(form);
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
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.animalCard}>
          <Image
            source={require('../../assets/images/adoption/animal-detail.jpg')}
            style={styles.animalImage}
          />
          <View style={styles.animalSummary}>
            <Text style={styles.animalTitle}>[개] 믹스견</Text>
            <Text style={styles.animalMeta}>2026년생 | 암컷 | 3kg</Text>
            <Text style={styles.animalMeta}>
              중성화 여부 <Text style={styles.neutered}>O</Text>
            </Text>
          </View>
          <View style={styles.identifiers}>
            <View style={styles.identifierRow}>
              <Text style={styles.identifierLabel}>등록번호</Text>
              <Text style={styles.identifierValue}>113122600015</Text>
            </View>
            <View style={styles.identifierRow}>
              <Text style={styles.identifierLabel}>마이크로칩번호</Text>
              <Text style={styles.identifierValue}>410987654321098</Text>
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
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
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
