import {
  NaverMapMarkerOverlay,
  NaverMapView,
  type NaverMapViewRef,
} from '@mj-studio/react-native-naver-map';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import DateIcon from '../../assets/icons/common/date.svg';
import MapLocationIcon from '../../assets/icons/common/map-location.svg';
import MapIcon from '../../assets/icons/common/map.svg';
import RightArrowGreenIcon from '../../assets/icons/common/right-arrow-green.svg';
import { AppHeader, SkeletonBlock, SkeletonGroup } from '../../components/common';
import { getMapFoundReports } from '../../services/foundReports/mapReports';
import { useFoundReportEditStore } from '../../stores/foundReportEditStore';
import { colors, radius, typography } from '../../theme';
import type { FoundReport } from './data';

type Props = NativeStackScreenProps<RootStackParamList, 'FoundReportMap'>;

const DEFAULT_CAMERA = { latitude: 36.1195, longitude: 128.4217, zoom: 12 };

export function FoundReportMapScreen({ navigation }: Props) {
  const insets = useSafeAreaInsets();
  const mapRef = useRef<NaverMapViewRef>(null);
  const deletedReportIds = useFoundReportEditStore(state => state.deletedIds);
  const reportEdits = useFoundReportEditStore(state => state.editsById);
  const [reports, setReports] = useState<FoundReport[]>([]);
  const [selectedReport, setSelectedReport] = useState<FoundReport | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const applyReportState = useCallback(
    (nextReports: FoundReport[]) =>
      nextReports
        .filter(report => !deletedReportIds.includes(report.id))
        .map(report => ({ ...report, ...reportEdits[report.id] })),
    [deletedReportIds, reportEdits],
  );

  useEffect(() => {
    getMapFoundReports()
      .then(nextReports => setReports(applyReportState(nextReports)))
      .finally(() => setIsLoading(false));
  }, [applyReportState]);

  const selectReport = (report: FoundReport) => {
    setSelectedReport(report);
    mapRef.current?.animateCameraTo({
      duration: 350,
      latitude: report.latitude,
      longitude: report.longitude,
      zoom: 16,
    });
  };

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <AppHeader onBackPress={navigation.goBack} title="제보신고 지도" />
      <View style={styles.mapContainer}>
        <NaverMapView
          initialCamera={DEFAULT_CAMERA}
          isShowCompass={false}
          isShowIndoorLevelPicker={false}
          isShowLocationButton
          isShowScaleBar={false}
          isShowZoomControls={false}
          mapPadding={{ bottom: insets.bottom + 12 }}
          onTapMap={() => {
            setSelectedReport(null);
          }}
          ref={mapRef}
          style={styles.map}
        >
          {reports.map(report => (
            <NaverMapMarkerOverlay
              height={selectedReport?.id === report.id ? 48 : 42}
              key={report.id}
              latitude={report.latitude}
              longitude={report.longitude}
              onTap={() => selectReport(report)}
              width={selectedReport?.id === report.id ? 40 : 35}
              zIndex={selectedReport?.id === report.id ? 10 : 1}
            >
              <MapLocationIcon height="100%" width="100%" />
            </NaverMapMarkerOverlay>
          ))}
        </NaverMapView>

        {isLoading ? <MapSkeleton /> : null}

        <View style={styles.countBadge}>
          <Text style={styles.countText}>신고 {reports.length}건</Text>
        </View>

        {!isLoading && reports.length === 0 ? (
          <View style={styles.emptyCard}>
            <EmptyState />
          </View>
        ) : null}

        {selectedReport ? (
          <Pressable
            accessibilityLabel={`${selectedReport.name} 발견 신고 상세 보기`}
            accessibilityRole="button"
            onPress={() =>
              navigation.navigate('FoundReportDetail', { reportId: selectedReport.id })
            }
            style={({ pressed }) => [
              styles.reportCard,
              { bottom: Math.max(insets.bottom, 12) + 12 },
              pressed && styles.pressed,
            ]}
          >
            <Image source={selectedReport.images[0]} style={styles.reportImage} />
            <View style={styles.reportInfo}>
              <View style={styles.reportTitleRow}>
                <Text numberOfLines={1} style={styles.reportTitle}>
                  {selectedReport.name}
                </Text>
                <View style={styles.statusBadge}>
                  <Text style={styles.statusText}>{selectedReport.status}</Text>
                </View>
              </View>
              <View style={styles.metaRow}>
                <MapIcon height={16} width={16} />
                <Text numberOfLines={1} style={styles.metaText}>
                  {selectedReport.location}
                </Text>
              </View>
              <View style={styles.metaRow}>
                <DateIcon height={16} width={16} />
                <Text style={styles.metaText}>{selectedReport.date}</Text>
              </View>
              <View style={styles.detailLink}>
                <Text numberOfLines={1} style={styles.detailHint}>
                  상세 정보 보기
                </Text>
                <RightArrowGreenIcon height={14} width={14} />
              </View>
            </View>
          </Pressable>
        ) : null}
      </View>
    </SafeAreaView>
  );
}

function MapSkeleton() {
  return (
    <SkeletonGroup accessibilityLabel="발견 신고 지도 불러오는 중" style={styles.loadingOverlay}>
      <SkeletonBlock style={styles.skeletonCount} />
      <SkeletonBlock style={[styles.skeletonMarker, styles.skeletonMarkerOne]} />
      <SkeletonBlock style={[styles.skeletonMarker, styles.skeletonMarkerTwo]} />
      <SkeletonBlock style={[styles.skeletonMarker, styles.skeletonMarkerThree]} />
      <View style={styles.skeletonReportCard}>
        <SkeletonBlock style={styles.skeletonReportImage} />
        <View style={styles.skeletonReportInfo}>
          <SkeletonBlock style={styles.skeletonReportTitle} />
          <SkeletonBlock style={styles.skeletonReportLine} />
          <SkeletonBlock style={styles.skeletonReportLineShort} />
        </View>
      </View>
    </SkeletonGroup>
  );
}

function EmptyState() {
  return (
    <View style={styles.emptyContent}>
      <Text style={styles.emptyTitle}>등록된 제보가 없어요</Text>
      <Text style={styles.emptyDescription}>새로운 제보가 등록되면 지도에 표시됩니다.</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  mapContainer: { flex: 1 },
  map: { flex: 1 },
  loadingOverlay: {
    backgroundColor: colors.gray10,
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
    zIndex: 3,
  },
  skeletonCount: { height: 30, left: 16, position: 'absolute', top: 12, width: 68 },
  skeletonMarker: { borderRadius: 20, height: 40, position: 'absolute', width: 34 },
  skeletonMarkerOne: { left: '28%', top: '28%' },
  skeletonMarkerTwo: { right: '24%', top: '44%' },
  skeletonMarkerThree: { left: '48%', top: '59%' },
  skeletonReportCard: {
    backgroundColor: colors.white,
    borderRadius: radius.md,
    bottom: 24,
    flexDirection: 'row',
    left: 16,
    padding: 12,
    position: 'absolute',
    right: 16,
  },
  skeletonReportImage: { height: 106, width: 106 },
  skeletonReportInfo: { flex: 1, gap: 12, marginLeft: 12, paddingTop: 6 },
  skeletonReportLine: { height: 14, width: '88%' },
  skeletonReportLineShort: { height: 14, width: '62%' },
  skeletonReportTitle: { height: 20, width: '72%' },
  countBadge: {
    backgroundColor: colors.white,
    borderRadius: radius.pill,
    elevation: 3,
    left: 16,
    paddingHorizontal: 12,
    paddingVertical: 7,
    position: 'absolute',
    top: 12,
    zIndex: 1,
  },
  countText: { ...typography.captionSemiBold12, color: colors.gray700 },
  emptyCard: {
    backgroundColor: colors.white,
    borderRadius: radius.md,
    elevation: 5,
    left: 24,
    position: 'absolute',
    right: 24,
    top: 68,
  },
  emptyContent: { alignItems: 'center', gap: 5, paddingHorizontal: 18, paddingVertical: 24 },
  emptyTitle: { ...typography.bodySemiBold16, color: colors.gray700, textAlign: 'center' },
  emptyDescription: { ...typography.bodyRegular14, color: colors.gray400, textAlign: 'center' },
  reportCard: {
    backgroundColor: colors.white,
    borderRadius: radius.md,
    elevation: 7,
    flexDirection: 'row',
    left: 16,
    padding: 12,
    position: 'absolute',
    right: 16,
  },
  reportImage: { borderRadius: radius.sm, height: 106, width: 106 },
  reportInfo: { flex: 1, marginLeft: 12, minWidth: 0 },
  reportTitleRow: { alignItems: 'center', flexDirection: 'row', gap: 6 },
  reportTitle: { ...typography.titleSemiBold18, color: colors.black, flex: 1 },
  statusBadge: {
    backgroundColor: '#ECFDF3',
    borderRadius: radius.pill,
    paddingHorizontal: 8,
    paddingVertical: 3,
  },
  statusText: { ...typography.captionSemiBold12, color: colors.green400 },
  metaRow: { alignItems: 'center', flexDirection: 'row', gap: 4, marginTop: 6 },
  metaText: { ...typography.bodyRegular14, color: colors.gray600, flex: 1 },
  detailLink: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'flex-end',
    marginTop: 7,
  },
  detailHint: {
    ...typography.captionSemiBold12,
    color: colors.green400,
  },
  pressed: { opacity: 0.72 },
});
