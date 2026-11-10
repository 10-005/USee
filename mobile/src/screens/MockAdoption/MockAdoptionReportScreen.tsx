import { useCallback, useEffect, useRef, useState } from 'react';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { Image, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import { AppHeader, Button, DetailScreenSkeleton } from '../../components/common';
import {
  getSimulationReport,
  getSimulationReportErrorMessage,
  type SimulationReportDetail,
  type SimulationReportStatus,
} from '../../services/simulation/reports';
import { colors, radius, typography } from '../../theme';
import { mockAdoptionPets } from './data';
import { CategorySpending, DailySpending, FinalStateChart } from './reportCharts';
import {
  budgetGap,
  REPORT_PERSONALITY_LABEL,
  REPORT_STATUS_LABEL,
  reportPeriod,
  won,
} from './reportLabels';

const SUMMARY_REFRESH_MS = 10_000;
const STATUS_TONE: Record<SimulationReportStatus, { background: string; color: string }> = {
  IN_PROGRESS: { background: '#EAF3FF', color: '#115BCC' },
  COMPLETED: { background: '#E8F5EE', color: '#036666' },
  ABANDONED: { background: '#F3F2EE', color: '#53524D' },
  FAILED: { background: '#FDECEC', color: '#E5484D' },
};

type Props = NativeStackScreenProps<RootStackParamList, 'MockAdoptionReport'>;

function reportPetImage(report: SimulationReportDetail) {
  if (report.breed.representativeImageUrl) {
    return { uri: report.breed.representativeImageUrl };
  }
  return mockAdoptionPets.find(
    pet =>
      pet.breedCode === report.breed.breedSeq ||
      pet.breedCode === report.breed.breedSeq.padStart(5, '0'),
  )?.image;
}

function SummaryCard({ report }: { report: SimulationReportDetail }) {
  const image = reportPetImage(report);
  const species = report.speciesType === 'CAT' ? '고양이' : '개';
  const tone = STATUS_TONE[report.status];
  return (
    <View style={styles.card}>
      <Text style={styles.kicker}>모의 입양 동물</Text>
      <View style={styles.summaryHeader}>
        {image ? (
          <Image
            accessibilityLabel={`${report.animalName} 이미지`}
            source={image}
            resizeMode="contain"
            style={styles.petImage}
          />
        ) : null}
        <View style={styles.nameBlock}>
          <Text accessibilityRole="header" style={styles.animalName}>
            {report.animalName}
          </Text>
          <View style={[styles.badge, { backgroundColor: tone.background }]}>
            <Text style={[styles.badgeText, { color: tone.color }]}>
              {REPORT_STATUS_LABEL[report.status]}
            </Text>
          </View>
        </View>
      </View>
      <Text style={styles.meta}>{`${species} · ${report.breed.breedName}`}</Text>
      <Text style={styles.meta}>
        {`${REPORT_PERSONALITY_LABEL[report.personality]} · ${reportPeriod(
          report.startedDttm,
          report.endedDttm,
        )}`}
      </Text>
      <Text accessibilityLiveRegion="polite" style={styles.summary}>
        {report.summary ?? '요약 준비 중'}
      </Text>
    </View>
  );
}

function SpendingCard({ report }: { report: SimulationReportDetail }) {
  const gap = budgetGap(report.difference);
  return (
    <View style={styles.card}>
      <Text accessibilityRole="header" style={styles.sectionTitle}>
        지출 분석
      </Text>
      <Text style={styles.total}>{won(report.totalSpent)}</Text>
      <Text style={styles.meta}>
        {`예상 ${won(report.expectedCost)} · `}
        <Text style={gap.over ? styles.overBudget : undefined}>{gap.label}</Text>
      </Text>
      <Text accessibilityRole="header" style={styles.subTitle}>
        카테고리별 지출
      </Text>
      <CategorySpending spent={report.spentByCategory} total={report.totalSpent} />
      <Text accessibilityRole="header" style={styles.subTitle}>
        일별 지출
      </Text>
      <DailySpending daily={report.dailySpent} />
    </View>
  );
}

function CareCard({ report }: { report: SimulationReportDetail }) {
  const unresolved = Math.max(0, report.eventCount - report.eventResolvedCount);
  const rate =
    report.eventCount === 0 ? 0 : Math.min(1, report.eventResolvedCount / report.eventCount);
  const stats = [
    { label: '총 돌봄', value: `${report.actionCount}회` },
    { label: '산책', value: `${report.walkCount}회` },
    { label: '돌발 이벤트', value: `${report.eventCount}건` },
  ];
  return (
    <View style={styles.card}>
      <Text accessibilityRole="header" style={styles.sectionTitle}>
        돌봄 활동
      </Text>
      <View style={styles.careRow}>
        {stats.map(item => (
          <View key={item.label} style={styles.careItem}>
            <Text style={styles.careLabel}>{item.label}</Text>
            <Text style={styles.careValue}>{item.value}</Text>
          </View>
        ))}
      </View>
      <View style={styles.eventBox}>
        <Text style={styles.subTitle}>돌발 이벤트 처리 결과</Text>
        <View
          accessibilityLabel={`해결 ${report.eventResolvedCount}건, 미해결 ${unresolved}건`}
          style={styles.eventTrack}
        >
          <View style={[styles.eventFill, { width: `${rate * 100}%` }]} />
        </View>
        <View style={styles.eventLabels}>
          <Text style={styles.eventLabel}>{`해결 ${report.eventResolvedCount}건`}</Text>
          <Text style={styles.eventLabel}>{`미해결 ${unresolved}건`}</Text>
        </View>
      </View>
    </View>
  );
}

function ReportContent({ report }: { report: SimulationReportDetail }) {
  return (
    <>
      <SummaryCard report={report} />
      <SpendingCard report={report} />
      <View style={styles.card}>
        <Text accessibilityRole="header" style={styles.sectionTitle}>
          최종 상태
        </Text>
        <FinalStateChart stats={report.finalStats} />
      </View>
      <CareCard report={report} />
    </>
  );
}

export function MockAdoptionReportScreen({ navigation, route }: Props) {
  const { sessionSeq } = route.params;
  const insets = useSafeAreaInsets();
  const requestId = useRef(0);
  const [report, setReport] = useState<SimulationReportDetail | null>(null);
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    const id = requestId.current + 1;
    requestId.current = id;
    setLoading(true);
    getSimulationReport(sessionSeq)
      .then(value => {
        if (requestId.current !== id) {
          return;
        }
        setReport(value);
        setError(undefined);
      })
      .catch(loadError => {
        if (requestId.current !== id) {
          return;
        }
        setError(getSimulationReportErrorMessage(loadError));
      })
      .finally(() => {
        if (requestId.current === id) {
          setLoading(false);
        }
      });
  }, [sessionSeq]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    return () => {
      requestId.current += 1;
    };
  }, []);

  useEffect(() => {
    if (!report?.isFinal || report.summary !== null) {
      return;
    }
    const id = requestId.current;
    const timer = setInterval(() => {
      getSimulationReport(sessionSeq)
        .then(value => {
          if (requestId.current !== id || value.summary === null) {
            return;
          }
          setReport(value);
        })
        .catch(() => undefined);
    }, SUMMARY_REFRESH_MS);
    return () => clearInterval(timer);
  }, [report?.isFinal, report?.summary, sessionSeq]);

  return (
    <SafeAreaView edges={['top']} style={styles.container}>
      <AppHeader title="리포트 상세" onBackPress={navigation.goBack} />
      {loading ? <DetailScreenSkeleton accessibilityLabel="리포트 불러오는 중" /> : null}
      {!loading && (error || !report) ? (
        <View style={styles.status}>
          <Text style={styles.statusText}>
            {error ?? '리포트를 불러오지 못했어요. 다시 시도해주세요.'}
          </Text>
          <Button accessibilityLabel="리포트 다시 불러오기" onPress={load}>
            다시 불러오기
          </Button>
        </View>
      ) : null}
      {!loading && report && !error ? (
        <ScrollView
          contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 32 }]}
          showsVerticalScrollIndicator={false}
        >
          <ReportContent report={report} />
        </ScrollView>
      ) : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.gray10 },
  content: { gap: 16, paddingHorizontal: 20, paddingTop: 16 },
  card: {
    backgroundColor: colors.white,
    borderColor: '#E2E5EB',
    borderRadius: 16,
    borderWidth: 1,
    gap: 12,
    padding: 20,
  },
  kicker: { ...typography.captionSemiBold12, color: colors.gray500 },
  summaryHeader: { alignItems: 'center', flexDirection: 'row', gap: 12 },
  petImage: { height: 64, width: 64 },
  nameBlock: { alignItems: 'center', flex: 1, flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  animalName: { ...typography.titleBold18, color: colors.black, flexShrink: 1 },
  badge: { borderRadius: radius.pill, paddingHorizontal: 8, paddingVertical: 3 },
  badgeText: { ...typography.captionSemiBold12 },
  meta: { ...typography.bodyRegular14, color: colors.gray600, lineHeight: 20 },
  summary: {
    ...typography.bodyRegular14,
    borderTopColor: '#EBEAE5',
    borderTopWidth: 1,
    color: colors.gray700,
    lineHeight: 22,
    paddingTop: 16,
  },
  sectionTitle: { ...typography.titleBold18, color: colors.black, fontSize: 20, lineHeight: 26 },
  subTitle: { ...typography.bodySemiBold16, color: colors.gray800, marginTop: 4 },
  total: {
    color: colors.black,
    fontFamily: 'Pretendard-Bold',
    fontSize: 30,
    lineHeight: 38,
  },
  overBudget: { color: '#FA705B', fontFamily: 'Pretendard-Bold' },
  careRow: { flexDirection: 'row', gap: 8 },
  careItem: {
    backgroundColor: '#F8FBF9',
    borderColor: '#DFEDE5',
    borderRadius: 12,
    borderWidth: 1,
    flex: 1,
    gap: 8,
    paddingHorizontal: 10,
    paddingVertical: 14,
  },
  careLabel: { ...typography.bodyRegular14, color: '#435E54' },
  careValue: {
    color: '#036666',
    fontFamily: 'Pretendard-Bold',
    fontSize: 22,
    lineHeight: 28,
  },
  eventBox: {
    borderColor: '#DFEDE5',
    borderRadius: 12,
    borderWidth: 1,
    gap: 12,
    marginTop: 4,
    padding: 16,
  },
  eventTrack: { backgroundColor: '#EAF1ED', borderRadius: 99, height: 9, overflow: 'hidden' },
  eventFill: { backgroundColor: '#14746F', borderRadius: 99, height: '100%' },
  eventLabels: { flexDirection: 'row', justifyContent: 'space-between' },
  eventLabel: { ...typography.bodyRegular14, color: '#435E54' },
  status: { gap: 16, padding: 24 },
  statusText: { ...typography.bodyRegular16, color: colors.gray600, lineHeight: 24 },
});
