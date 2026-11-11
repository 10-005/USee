import { useCallback, useEffect, useRef, useState } from 'react';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { Animated, Easing, Image, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import { AppHeader, Button, SkeletonBlock, SkeletonGroup } from '../../components/common';
import {
  acknowledgeSimulationReport,
  getSimulationReport,
  getSimulationReportErrorMessage,
  type SimulationReportDetail,
  type SimulationReportStatus,
} from '../../services/simulation/reports';
import { colors, radius, typography } from '../../theme';
import { findMockAdoptionPetForBreed } from './data';
import { CategorySpending, DailySpending, FinalStateChart, GradientFill } from './reportCharts';
import {
  budgetGap,
  REPORT_PERSONALITY_LABEL,
  REPORT_STATUS_LABEL,
  reportPeriod,
  won,
} from './reportLabels';

const SUMMARY_REFRESH_MS = 10_000;
const SKELETON_BARS = [112, 72, 128, 36, 96, 24, 64];
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
  return findMockAdoptionPetForBreed(report.breed)?.image;
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
      <View style={styles.identity}>
        <Text style={styles.breed}>{`${species} · ${report.breed.breedName}`}</Text>
        <Text style={styles.period}>{reportPeriod(report.startedDttm, report.endedDttm)}</Text>
        <Text style={styles.personality}>{REPORT_PERSONALITY_LABEL[report.personality]}</Text>
      </View>
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
      <View style={styles.totalBlock}>
        <Text style={styles.total}>{won(report.totalSpent)}</Text>
        <Text style={styles.budgetLine}>
          {`예상 ${won(report.expectedCost)} · `}
          <Text style={styles[gap.tone]}>{gap.label}</Text>
        </Text>
      </View>
      <Text accessibilityRole="header" style={styles.subTitle}>
        카테고리별 지출
      </Text>
      <CategorySpending spent={report.spentByCategory} total={report.totalSpent} />
      <Text accessibilityRole="header" style={[styles.subTitle, styles.dailyHeading]}>
        일별 지출
      </Text>
      <DailySpending daily={report.dailySpent} />
    </View>
  );
}

function EventFill({ rate }: { rate: number }) {
  const progress = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    Animated.timing(progress, {
      toValue: rate,
      duration: 520,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: false,
    }).start();
  }, [progress, rate]);
  const width = progress.interpolate({ inputRange: [0, 1], outputRange: ['0%', '100%'] });
  return (
    <Animated.View style={[styles.eventFill, { width }]}>
      <GradientFill id="event-resolve" from="#56AB91" to="#14746F" horizontal />
    </Animated.View>
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
        {stats.map((item, index) => (
          <View key={item.label} style={styles.careItem}>
            <GradientFill id={`care-${index}`} from="#EDF8F1" to="#F8FBF9" />
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
          <EventFill rate={rate} />
        </View>
        <View style={styles.eventLabels}>
          <Text
            style={[styles.eventLabel, styles.solved]}
          >{`해결 ${report.eventResolvedCount}건`}</Text>
          <Text style={[styles.eventLabel, unresolved > 0 ? styles.unsolved : null]}>
            {`미해결 ${unresolved}건`}
          </Text>
        </View>
      </View>
    </View>
  );
}

function ReportSkeleton() {
  return (
    <SkeletonGroup accessibilityLabel="리포트 불러오는 중" style={styles.skeleton}>
      <View style={styles.card}>
        <SkeletonBlock style={styles.skeletonKicker} />
        <View style={styles.summaryHeader}>
          <SkeletonBlock style={styles.skeletonPet} />
          <View style={styles.skeletonNameCol}>
            <SkeletonBlock style={styles.skeletonName} />
            <SkeletonBlock style={styles.skeletonBadge} />
          </View>
        </View>
        <View style={styles.identity}>
          <SkeletonBlock style={styles.skeletonBreed} />
          <SkeletonBlock style={styles.skeletonPeriod} />
          <SkeletonBlock style={styles.skeletonPersonality} />
        </View>
        <View style={styles.skeletonSummary}>
          <SkeletonBlock style={styles.skeletonLine} />
          <SkeletonBlock style={styles.skeletonLine} />
          <SkeletonBlock style={styles.skeletonLineShort} />
        </View>
      </View>
      <View style={styles.card}>
        <SkeletonBlock style={styles.skeletonSection} />
        <SkeletonBlock style={styles.skeletonTotal} />
        <SkeletonBlock style={styles.skeletonBudget} />
        <SkeletonBlock style={styles.skeletonSub} />
        <SkeletonBlock style={styles.skeletonChart} />
        <View style={styles.skeletonLegend}>
          {[0, 1, 2].map(row => (
            <View key={row} style={styles.skeletonLegendRow}>
              <SkeletonBlock style={styles.skeletonSwatch} />
              <SkeletonBlock style={styles.skeletonLegendLabel} />
              <SkeletonBlock style={styles.skeletonLegendAmount} />
            </View>
          ))}
        </View>
        <SkeletonBlock style={[styles.skeletonSub, styles.skeletonDailyHeading]} />
        <View style={styles.skeletonDaily}>
          <View style={styles.skeletonAxis}>
            {[0, 1, 2, 3, 4].map(tick => (
              <SkeletonBlock key={tick} style={styles.skeletonAxisTick} />
            ))}
          </View>
          <View style={styles.skeletonBars}>
            {SKELETON_BARS.map(height => (
              <View key={height} style={styles.skeletonBarCol}>
                <SkeletonBlock style={[styles.skeletonBar, { height }]} />
                <SkeletonBlock style={styles.skeletonDay} />
              </View>
            ))}
          </View>
        </View>
      </View>
      <View style={styles.card}>
        <SkeletonBlock style={styles.skeletonSectionShort} />
        <SkeletonBlock style={styles.skeletonChart} />
      </View>
      <View style={styles.card}>
        <SkeletonBlock style={styles.skeletonSection} />
        <View style={styles.careRow}>
          {[0, 1, 2].map(item => (
            <View key={item} style={styles.careItem}>
              <SkeletonBlock style={styles.skeletonCareLabel} />
              <SkeletonBlock style={styles.skeletonCareValue} />
            </View>
          ))}
        </View>
        <View style={styles.eventBox}>
          <SkeletonBlock style={styles.skeletonSub} />
          <SkeletonBlock style={styles.skeletonEventTrack} />
          <View style={styles.eventLabels}>
            <SkeletonBlock style={styles.skeletonEventLabel} />
            <SkeletonBlock style={styles.skeletonEventLabel} />
          </View>
        </View>
      </View>
    </SkeletonGroup>
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
    acknowledgeSimulationReport(sessionSeq);
  }, [sessionSeq]);

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
      {loading ? (
        <ScrollView
          contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 32 }]}
          showsVerticalScrollIndicator={false}
        >
          <ReportSkeleton />
        </ScrollView>
      ) : null}
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
  content: { gap: 20, paddingHorizontal: 20, paddingTop: 20 },
  card: {
    backgroundColor: colors.white,
    borderColor: '#E2E5EB',
    borderRadius: 16,
    borderWidth: 1,
    gap: 16,
    padding: 22,
  },
  kicker: { ...typography.captionSemiBold12, color: colors.green400 },
  summaryHeader: { alignItems: 'center', flexDirection: 'row', gap: 14 },
  petImage: { height: 72, width: 72 },
  nameBlock: { alignItems: 'center', flex: 1, flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  animalName: {
    color: colors.black,
    flexShrink: 1,
    fontFamily: 'Pretendard-Bold',
    fontSize: 26,
    lineHeight: 34,
  },
  badge: { borderRadius: radius.pill, paddingHorizontal: 10, paddingVertical: 4 },
  badgeText: { ...typography.captionSemiBold12 },
  identity: { gap: 6 },
  breed: { ...typography.bodySemiBold16, color: colors.gray800 },
  period: { ...typography.bodyRegular14, color: colors.gray500, lineHeight: 20 },
  personality: { ...typography.bodySemiBold16, color: '#14746F' },
  summary: {
    ...typography.bodyRegular16,
    borderTopColor: '#EBEAE5',
    borderTopWidth: 1,
    color: colors.gray700,
    lineHeight: 26,
    marginTop: 4,
    paddingTop: 18,
  },
  sectionTitle: {
    color: colors.black,
    fontFamily: 'Pretendard-Bold',
    fontSize: 22,
    lineHeight: 28,
  },
  subTitle: { ...typography.bodySemiBold16, color: colors.gray800, fontSize: 17, lineHeight: 22 },
  dailyHeading: { marginTop: 20 },
  totalBlock: { gap: 4 },
  total: {
    color: '#036666',
    fontFamily: 'Pretendard-Bold',
    fontSize: 36,
    lineHeight: 44,
  },
  budgetLine: { ...typography.bodyRegular16, color: colors.gray600, lineHeight: 24 },
  over: { color: '#FA705B', fontFamily: 'Pretendard-Bold', fontSize: 16 },
  saved: { color: colors.green400, fontFamily: 'Pretendard-Bold', fontSize: 16 },
  even: { color: colors.gray700, fontFamily: 'Pretendard-Bold', fontSize: 16 },
  careRow: { flexDirection: 'row', gap: 10 },
  careItem: {
    backgroundColor: '#F8FBF9',
    borderColor: '#DFEDE5',
    borderRadius: 12,
    borderWidth: 1,
    flex: 1,
    gap: 8,
    overflow: 'hidden',
    paddingHorizontal: 10,
    paddingVertical: 14,
  },
  careLabel: { ...typography.bodyRegular14, color: '#435E54' },
  careValue: {
    color: '#036666',
    fontFamily: 'Pretendard-Bold',
    fontSize: 26,
    lineHeight: 32,
  },
  eventBox: {
    borderColor: '#DFEDE5',
    borderRadius: 12,
    borderWidth: 1,
    gap: 14,
    marginTop: 6,
    padding: 16,
  },
  eventTrack: { backgroundColor: '#EAF1ED', borderRadius: 99, height: 9, overflow: 'hidden' },
  eventFill: { borderRadius: 99, height: '100%', overflow: 'hidden' },
  eventLabels: { flexDirection: 'row', justifyContent: 'space-between' },
  eventLabel: { ...typography.bodyRegular14, color: '#435E54' },
  solved: { color: '#036666', fontFamily: 'Pretendard-Bold' },
  unsolved: { color: colors.danger, fontFamily: 'Pretendard-Bold' },
  status: { gap: 16, padding: 24 },
  statusText: { ...typography.bodyRegular16, color: colors.gray600, lineHeight: 24 },
  skeleton: { gap: 20 },
  skeletonKicker: { height: 12, width: 92 },
  skeletonPet: { borderRadius: 12, height: 72, width: 72 },
  skeletonNameCol: { flex: 1, gap: 10 },
  skeletonName: { height: 26, width: '62%' },
  skeletonBadge: { borderRadius: radius.pill, height: 22, width: 64 },
  skeletonBreed: { height: 16, width: '46%' },
  skeletonPeriod: { height: 14, width: '78%' },
  skeletonPersonality: { height: 16, width: '34%' },
  skeletonSummary: {
    borderTopColor: '#EBEAE5',
    borderTopWidth: 1,
    gap: 10,
    marginTop: 4,
    paddingTop: 18,
  },
  skeletonLine: { height: 16, width: '100%' },
  skeletonLineShort: { height: 16, width: '68%' },
  skeletonSection: { height: 22, width: 108 },
  skeletonSectionShort: { height: 22, width: 88 },
  skeletonTotal: { height: 36, width: 148 },
  skeletonBudget: { height: 16, width: '64%' },
  skeletonSub: { height: 17, width: 124 },
  skeletonChart: { alignSelf: 'center', borderRadius: 110, height: 220, width: 220 },
  skeletonLegend: { gap: 12 },
  skeletonLegendRow: { alignItems: 'center', flexDirection: 'row', gap: 8, minHeight: 28 },
  skeletonSwatch: { borderRadius: 3, height: 10, width: 10 },
  skeletonLegendLabel: { flex: 1, height: 14 },
  skeletonLegendAmount: { height: 14, width: 68 },
  skeletonDailyHeading: { marginTop: 20 },
  skeletonDaily: { flexDirection: 'row', gap: 8, marginTop: 8 },
  skeletonAxis: { height: 148, justifyContent: 'space-between', width: 36 },
  skeletonAxisTick: { height: 8, width: '100%' },
  skeletonBars: { alignItems: 'flex-end', flex: 1, flexDirection: 'row', height: 168 },
  skeletonBarCol: { alignItems: 'center', flex: 1, justifyContent: 'flex-end' },
  skeletonBar: { borderTopLeftRadius: 8, borderTopRightRadius: 8, width: 18 },
  skeletonDay: { height: 10, marginTop: 10, width: 22 },
  skeletonCareLabel: { height: 14, width: '78%' },
  skeletonCareValue: { height: 26, width: '62%' },
  skeletonEventTrack: { borderRadius: 99, height: 9, width: '100%' },
  skeletonEventLabel: { height: 14, width: 72 },
});
