import {
  NaverMapMarkerOverlay,
  NaverMapView,
  type NaverMapViewRef,
} from '@mj-studio/react-native-naver-map';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Image,
  Keyboard,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import DateIcon from '../../assets/icons/common/date.svg';
import LocationIcon from '../../assets/icons/common/location.svg';
import MapLocationIcon from '../../assets/icons/common/map-location.svg';
import MapIcon from '../../assets/icons/common/map.svg';
import RightArrowGreenIcon from '../../assets/icons/common/right-arrow-green.svg';
import SearchIcon from '../../assets/icons/common/search.svg';
import { AppHeader } from '../../components/common';
import {
  getCandidateAddress,
  searchGeocodeCandidates,
  type GeocodeCandidate,
} from '../../services/geocode/geocode';
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
  const [query, setQuery] = useState('');
  const [reports, setReports] = useState<FoundReport[]>([]);
  const [selectedReport, setSelectedReport] = useState<FoundReport | null>(null);
  const [candidates, setCandidates] = useState<GeocodeCandidate[] | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSearching, setIsSearching] = useState(false);
  const [searchedArea, setSearchedArea] = useState('');

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

  const searchArea = async () => {
    const keyword = query.trim();
    Keyboard.dismiss();
    setSelectedReport(null);
    if (!keyword) {
      setSearchedArea('');
      setCandidates(null);
      setReports(applyReportState(await getMapFoundReports()));
      return;
    }

    setIsSearching(true);
    try {
      const nextCandidates = await searchGeocodeCandidates(keyword);
      setCandidates(nextCandidates);
      if (!nextCandidates.length) {
        setSearchedArea(keyword);
        setReports([]);
      }
    } finally {
      setIsSearching(false);
    }
  };

  const chooseArea = async (candidate: GeocodeCandidate) => {
    const address = getCandidateAddress(candidate);
    setQuery(address);
    setSearchedArea(address);
    setCandidates(null);
    setSelectedReport(null);
    mapRef.current?.animateCameraTo({
      duration: 500,
      latitude: candidate.latitude,
      longitude: candidate.longitude,
      zoom: 14,
    });

    const regionKeyword =
      [...address.split(' ')].reverse().find(part => /(시|군|구|읍|면|동)$/.test(part)) ?? address;
    setReports(applyReportState(await getMapFoundReports({ keyword: regionKeyword })));
  };

  const selectReport = (report: FoundReport) => {
    setSelectedReport(report);
    setCandidates(null);
    Keyboard.dismiss();
    mapRef.current?.animateCameraTo({
      duration: 350,
      latitude: report.latitude,
      longitude: report.longitude,
      zoom: 16,
    });
  };

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <AppHeader onBackPress={navigation.goBack} title="발견 신고 지도" />
      <View style={styles.searchRow}>
        <View style={styles.searchField}>
          <SearchIcon height={20} width={20} />
          <TextInput
            maxLength={100}
            onChangeText={value => {
              setQuery(value);
              setCandidates(null);
            }}
            onSubmitEditing={searchArea}
            placeholder="지역, 동네, 주소로 검색"
            placeholderTextColor={colors.gray400}
            returnKeyType="search"
            style={styles.searchInput}
            value={query}
          />
        </View>
        <Pressable
          accessibilityLabel="지역 검색"
          accessibilityRole="button"
          onPress={searchArea}
          style={({ pressed }) => [styles.searchButton, pressed && styles.pressed]}
        >
          {isSearching ? (
            <ActivityIndicator color={colors.white} size="small" />
          ) : (
            <Text style={styles.searchButtonText}>검색</Text>
          )}
        </Pressable>
      </View>
      <View style={styles.mapContainer}>
        <NaverMapView
          initialCamera={DEFAULT_CAMERA}
          isShowCompass={false}
          isShowIndoorLevelPicker={false}
          isShowLocationButton
          isShowScaleBar={false}
          isShowZoomControls={false}
          onTapMap={() => {
            setSelectedReport(null);
            setCandidates(null);
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

        {isLoading ? (
          <View style={styles.loadingOverlay}>
            <ActivityIndicator color={colors.green400} />
          </View>
        ) : null}

        <View style={styles.countBadge}>
          <Text style={styles.countText}>신고 {reports.length}건</Text>
        </View>

        {candidates ? (
          <View style={styles.resultsPanel}>
            {candidates.length ? (
              <ScrollView
                decelerationRate="fast"
                keyboardShouldPersistTaps="handled"
                style={styles.resultsScroll}
              >
                {candidates.map(candidate => (
                  <Pressable
                    key={`${candidate.latitude}-${candidate.longitude}`}
                    onPress={() => chooseArea(candidate)}
                    style={({ pressed }) => [styles.resultItem, pressed && styles.resultPressed]}
                  >
                    <LocationIcon height={20} width={20} />
                    <View style={styles.resultTextWrap}>
                      <Text numberOfLines={1} style={styles.resultAddress}>
                        {getCandidateAddress(candidate)}
                      </Text>
                      {candidate.jibunAddress ? (
                        <Text numberOfLines={1} style={styles.resultSubAddress}>
                          {candidate.jibunAddress}
                        </Text>
                      ) : null}
                    </View>
                  </Pressable>
                ))}
              </ScrollView>
            ) : (
              <EmptyState area={query.trim()} />
            )}
          </View>
        ) : null}

        {!isLoading && !candidates && reports.length === 0 ? (
          <View style={styles.emptyCard}>
            <EmptyState area={searchedArea} />
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

function EmptyState({ area }: { area: string }) {
  return (
    <View style={styles.emptyContent}>
      <Text style={styles.emptyTitle}>{area ? `'${area}' 주변에` : '이 지역에'} 신고가 없어요</Text>
      <Text style={styles.emptyDescription}>다른 지역이나 주소로 검색해 보세요.</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  searchRow: {
    backgroundColor: colors.white,
    flexDirection: 'row',
    gap: 8,
    paddingBottom: 12,
    paddingHorizontal: 20,
  },
  searchField: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    flex: 1,
    flexDirection: 'row',
    gap: 8,
    height: 46,
    paddingHorizontal: 12,
  },
  searchInput: { ...typography.bodyRegular14, color: colors.gray700, flex: 1, padding: 0 },
  searchButton: {
    alignItems: 'center',
    backgroundColor: colors.green400,
    borderRadius: radius.md,
    height: 46,
    justifyContent: 'center',
    width: 62,
  },
  searchButtonText: { ...typography.bodySemiBold16, color: colors.white },
  mapContainer: { flex: 1 },
  map: { flex: 1 },
  loadingOverlay: {
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.72)',
    bottom: 0,
    justifyContent: 'center',
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  resultsPanel: {
    backgroundColor: colors.white,
    borderBottomLeftRadius: radius.md,
    borderBottomRightRadius: radius.md,
    elevation: 6,
    left: 20,
    maxHeight: 230,
    position: 'absolute',
    right: 20,
    top: -8,
    zIndex: 2,
  },
  resultsScroll: { maxHeight: 230 },
  resultItem: {
    alignItems: 'center',
    borderBottomColor: colors.gray200,
    borderBottomWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    gap: 10,
    minHeight: 68,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  resultPressed: { backgroundColor: colors.gray10 },
  resultTextWrap: { flex: 1, gap: 3 },
  resultAddress: { ...typography.bodyRegular14, color: colors.gray800 },
  resultSubAddress: { ...typography.captionSemiBold12, color: colors.gray400 },
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
