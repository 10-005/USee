import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Animated,
  Easing,
  FlatList,
  Image,
  Keyboard,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
} from 'react-native';

import type { RootStackParamList } from '../../app/navigation/types';
import DateIcon from '../../assets/icons/common/date.svg';
import BookmarkActiveIcon from '../../assets/icons/common/bookmark-active.svg';
import BookmarkIcon from '../../assets/icons/common/bookmark.svg';
import MapButtonIcon from '../../assets/icons/common/map-2.svg';
import MapIcon from '../../assets/icons/common/map.svg';
import { FilterChip, SearchBar, SkeletonBlock, SkeletonGroup } from '../../components/common';
import { colors, radius, typography } from '../../theme';
import { formatKoreanAddress } from '../../utils/address';
import { useFoundReportEditStore } from '../../stores/foundReportEditStore';
import { useFoundReportStore } from '../../stores/foundReportStore';
import { useLostReportEditStore } from '../../stores/lostReportEditStore';
import { useMissingReportStore } from '../../stores/missingReportStore';
import { useCatalogStore } from '../../stores/catalogStore';
import { useReportStatusStore } from '../../stores/reportStatusStore';
import { useAdoptionBookmarkStore } from '../../stores/adoptionBookmarkStore';
import {
  AdoptionFilterSheet,
  type AdoptionFilters,
  type FilterKey,
  type TwoColumnFilterOptions,
} from '../Home/AdoptionFilterSheet';
import { foundReports, type FoundReport } from './data';
import type { LostReport } from '../lost-report/data';

type Props = {
  bottomInset: number;
  initialTab?: ReportTab;
  navigation: NativeStackNavigationProp<RootStackParamList, 'Home'>;
  onFilterOpenChange?: (open: boolean) => void;
};

const filterItems: Array<{ key: FilterKey; label: string }> = [
  { key: 'period', label: '기간' },
  { key: 'region', label: '지역' },
  { key: 'species', label: '축종' },
  { key: 'status', label: '상태' },
  { key: 'gender', label: '성별' },
  { key: 'neutered', label: '중성화여부' },
  { key: 'sort', label: '최신순' },
];

const formatDate = (date: Date) =>
  `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(
    date.getDate(),
  ).padStart(2, '0')}`;

const initialFilters: AdoptionFilters = {
  gender: '전체',
  neutered: '전체',
  period: `전체|${formatDate(new Date())}`,
  region: '전체',
  sort: '최신순',
  species: '전체',
  status: '전체',
};

const SEARCH_FILTERS_HEIGHT = 120;
const HIDE_CONTROLS_OFFSET = 120;
const HIDE_DIRECTION_DISTANCE = 40;
const SHOW_DIRECTION_DISTANCE = 16;

const matchesSidoName = (address: string, sidoName: string) => {
  const normalizedAddress = formatKoreanAddress(address);
  return (
    normalizedAddress.startsWith(sidoName) ||
    normalizedAddress.startsWith(sidoName.replace(/(특별시|광역시)$/, '시'))
  );
};

export const filterFoundReports = (query: string, filters: AdoptionFilters) => {
  return filterReports(foundReports, query, filters);
};

type Report = FoundReport | LostReport;
type ReportTab = 'found' | 'lost';

export const filterReports = <T extends Report>(
  source: T[],
  query: string,
  filters: AdoptionFilters,
) => {
  const filtered = source.filter(report => {
    const searchable = `${report.name} ${report.location} ${report.description} ${report.color}`;
    const [start = '전체', end = ''] = filters.period.split('|');
    const startDate = start.replaceAll('.', '-');
    const endDate = end.replaceAll('.', '-');
    const region = filters.region.split(' > ')[0];
    return (
      searchable.toLowerCase().includes(query.trim().toLowerCase()) &&
      (start === '전체' || report.date >= startDate) &&
      (!endDate || report.date <= endDate) &&
      (region === '전체' || matchesSidoName(report.location, region)) &&
      (filters.species === '전체' || report.species === filters.species.split(' > ')[0]) &&
      (filters.status === '전체' || report.status === filters.status) &&
      (filters.gender === '전체' || report.gender === filters.gender || report.gender === '모름') &&
      (filters.neutered === '전체' ||
        report.neutered === null ||
        report.neutered === (filters.neutered === '중성화 O'))
    );
  });
  return [...filtered].sort((a, b) =>
    filters.sort === '최신순' ? b.date.localeCompare(a.date) : a.date.localeCompare(b.date),
  );
};

export function FoundReportListContent({
  bottomInset,
  initialTab = 'found',
  navigation,
  onFilterOpenChange,
}: Props) {
  const { width } = useWindowDimensions();
  const cardWidth = (width - 52) / 2;
  const listRef = useRef<FlatList<Report>>(null);
  const controlsProgress = useRef(new Animated.Value(1)).current;
  const controlsVisibleRef = useRef(true);
  const lastScrollOffset = useRef(0);
  const scrollDirection = useRef<'down' | 'up' | null>(null);
  const scrollDirectionDistance = useRef(0);
  const [controlsVisible, setControlsVisible] = useState(true);
  const [activeTab, setActiveTab] = useState<ReportTab>(initialTab);
  const [query, setQuery] = useState('');
  const [activeFilter, setActiveFilter] = useState<FilterKey | null>(null);
  const [filters, setFilters] = useState(initialFilters);
  const statusById = useReportStatusStore(state => state.statusById);
  const bookmarkedIds = useAdoptionBookmarkStore(state => state.bookmarkedIds);
  const toggleBookmark = useAdoptionBookmarkStore(state => state.toggleBookmark);
  const deletedFoundReportIds = useFoundReportEditStore(state => state.deletedIds);
  const foundReportEdits = useFoundReportEditStore(state => state.editsById);
  const deletedLostReportIds = useLostReportEditStore(state => state.deletedIds);
  const lostReportEdits = useLostReportEditStore(state => state.editsById);
  const apiLostReports = useMissingReportStore(state => state.reports);
  const fetchLostReports = useMissingReportStore(state => state.fetchReports);
  const lostReportsLoading = useMissingReportStore(state => state.isLoading);
  const lostReportsError = useMissingReportStore(state => state.error);
  const apiFoundReports = useFoundReportStore(state => state.reports);
  const fetchFoundReports = useFoundReportStore(state => state.fetchReports);
  const foundReportsLoading = useFoundReportStore(state => state.isLoading);
  const foundReportsError = useFoundReportStore(state => state.error);
  const districts = useCatalogStore(state => state.districts);
  const speciesCatalog = useCatalogStore(state => state.species);
  const breedsBySpecies = useCatalogStore(state => state.breedsBySpecies);
  const loadDistricts = useCatalogStore(state => state.loadDistricts);
  const loadSpecies = useCatalogStore(state => state.loadSpecies);
  const loadBreeds = useCatalogStore(state => state.loadBreeds);

  useEffect(() => {
    loadDistricts().catch(() => undefined);
    loadSpecies().catch(() => undefined);
  }, [activeTab, loadDistricts, loadSpecies]);

  useEffect(() => {
    if (speciesCatalog.length > 0) {
      Promise.all(speciesCatalog.map(item => loadBreeds(item.speciesSeq))).catch(() => undefined);
    }
  }, [activeTab, loadBreeds, speciesCatalog]);

  const regionOptions = useMemo<TwoColumnFilterOptions>(() => {
    const grouped: TwoColumnFilterOptions = { 전체: [] };
    districts.forEach(district => {
      grouped[district.sidoName] ??= ['전체'];
      if (!grouped[district.sidoName].includes(district.sigunguName)) {
        grouped[district.sidoName].push(district.sigunguName);
      }
    });
    return grouped;
  }, [districts]);

  const speciesOptions = useMemo<TwoColumnFilterOptions>(() => {
    const options: TwoColumnFilterOptions = { 전체: [] };
    speciesCatalog.forEach(item => {
      options[item.speciesName] = [
        '전체',
        ...(breedsBySpecies[item.speciesSeq] ?? []).map(breed => breed.breedName),
      ];
    });
    return options;
  }, [breedsBySpecies, speciesCatalog]);

  const reportSearchParams = useMemo(() => {
    const [periodStart = '전체', periodEnd = ''] = filters.period.split('|');
    const [sidoName, sigunguName] = filters.region.split(' > ');
    const speciesName = filters.species.split(' > ')[0];
    const district = sigunguName
      ? districts.find(item => item.sidoName === sidoName && item.sigunguName === sigunguName)
      : undefined;
    const selectedSpecies = speciesCatalog.find(item => item.speciesName === speciesName);
    return {
      districtCode: district?.districtCode,
      gender:
        filters.gender === '수컷'
          ? ('MALE' as const)
          : filters.gender === '암컷'
          ? ('FEMALE' as const)
          : undefined,
      isNeutered:
        filters.neutered === '중성화 O'
          ? true
          : filters.neutered === '중성화 X'
          ? false
          : undefined,
      keyword: query.trim() || undefined,
      occurredFrom: periodStart === '전체' ? undefined : periodStart.replaceAll('.', '-'),
      occurredTo: periodEnd ? periodEnd.replaceAll('.', '-') : undefined,
      sort: filters.sort === '오래된 순' ? 'regDttm,asc' : 'regDttm,desc',
      speciesSeq: selectedSpecies?.speciesSeq,
      status: ['실종', '진행 중', '제보'].includes(filters.status)
        ? ('ACTIVE' as const)
        : ['종료', '완료'].includes(filters.status)
        ? ('CLOSED' as const)
        : undefined,
    };
  }, [districts, filters, query, speciesCatalog]);

  useEffect(() => {
    const timer = setTimeout(() => {
      const fetchReports = activeTab === 'found' ? fetchFoundReports : fetchLostReports;
      fetchReports(reportSearchParams).catch(() => undefined);
    }, 250);
    return () => clearTimeout(timer);
  }, [activeTab, fetchFoundReports, fetchLostReports, reportSearchParams]);

  const reports = useMemo(() => {
    const source = (activeTab === 'found' ? apiFoundReports : apiLostReports)
      .filter(report =>
        activeTab === 'found'
          ? !deletedFoundReportIds.includes(report.id)
          : !deletedLostReportIds.includes(report.id),
      )
      .map(report => {
        const editedReport =
          activeTab === 'found' ? foundReportEdits[report.id] : lostReportEdits[report.id];
        const currentReport = (editedReport ? { ...report, ...editedReport } : report) as Report;
        const storedStatus = statusById[report.id];
        if (!storedStatus) {
          return currentReport;
        }
        return {
          ...currentReport,
          status:
            activeTab === 'found'
              ? storedStatus === 'active'
                ? '진행 중'
                : '완료'
              : storedStatus === 'active'
              ? '실종'
              : '종료',
        } as Report;
      });
    const [selectedSido, selectedSigungu] = filters.region.split(' > ');
    const selectedBreed = filters.species.split(' > ')[1];
    return source.filter(report => {
      const matchesSido =
        selectedSido === '전체' ||
        Boolean(selectedSigungu) ||
        matchesSidoName(report.location, selectedSido);
      const matchesBreed =
        !selectedBreed || selectedBreed === '전체' || report.name === selectedBreed;
      const matchesGender =
        filters.gender === '전체' || report.gender === filters.gender || report.gender === '모름';
      const matchesNeutered =
        filters.neutered === '전체' ||
        report.neutered === null ||
        report.neutered === (filters.neutered === '중성화 O');
      return matchesSido && matchesBreed && matchesGender && matchesNeutered;
    });
  }, [
    activeTab,
    apiFoundReports,
    apiLostReports,
    deletedFoundReportIds,
    deletedLostReportIds,
    filters,
    foundReportEdits,
    lostReportEdits,
    statusById,
  ]);

  const updateControlsVisibility = useCallback(
    (visible: boolean) => {
      if (controlsVisibleRef.current === visible) {
        return;
      }
      controlsVisibleRef.current = visible;
      setControlsVisible(visible);
      Animated.timing(controlsProgress, {
        duration: 300,
        easing: Easing.bezier(0.22, 1, 0.36, 1),
        toValue: visible ? 1 : 0,
        useNativeDriver: false,
      }).start();
    },
    [controlsProgress],
  );

  const resetListHeader = useCallback(() => {
    controlsProgress.stopAnimation();
    controlsProgress.setValue(1);
    controlsVisibleRef.current = true;
    lastScrollOffset.current = 0;
    scrollDirection.current = null;
    scrollDirectionDistance.current = 0;
    setControlsVisible(true);
  }, [controlsProgress]);

  const handleListScroll = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const currentOffset = Math.max(0, event.nativeEvent.contentOffset.y);
    const delta = currentOffset - lastScrollOffset.current;
    lastScrollOffset.current = currentOffset;

    if (activeFilter) {
      return;
    }
    if (currentOffset <= 20) {
      scrollDirection.current = null;
      scrollDirectionDistance.current = 0;
      updateControlsVisibility(true);
      return;
    }
    if (Math.abs(delta) < 1) {
      return;
    }

    const nextDirection = delta > 0 ? 'down' : 'up';
    if (scrollDirection.current !== nextDirection) {
      scrollDirection.current = nextDirection;
      scrollDirectionDistance.current = 0;
    }
    scrollDirectionDistance.current += Math.abs(delta);

    if (
      nextDirection === 'down' &&
      currentOffset > HIDE_CONTROLS_OFFSET &&
      scrollDirectionDistance.current >= HIDE_DIRECTION_DISTANCE
    ) {
      updateControlsVisibility(false);
      scrollDirectionDistance.current = 0;
    } else if (
      nextDirection === 'up' &&
      scrollDirectionDistance.current >= SHOW_DIRECTION_DISTANCE
    ) {
      updateControlsVisibility(true);
      scrollDirectionDistance.current = 0;
    }
  };

  useEffect(() => {
    listRef.current?.scrollToOffset({ animated: false, offset: 0 });
  }, [activeTab, filters, query]);

  useEffect(() => {
    if (activeFilter) {
      updateControlsVisibility(true);
    }
  }, [activeFilter, updateControlsVisibility]);

  useEffect(() => {
    onFilterOpenChange?.(activeFilter !== null);
  }, [activeFilter, onFilterOpenChange]);

  useEffect(
    () => () => {
      onFilterOpenChange?.(false);
    },
    [onFilterOpenChange],
  );

  const selectTab = (tab: ReportTab) => {
    Keyboard.dismiss();
    resetListHeader();
    if (tab === activeTab) {
      listRef.current?.scrollToOffset({ animated: true, offset: 0 });
      return;
    }
    setActiveTab(tab);
    setActiveFilter(null);
    setFilters(initialFilters);
    setQuery('');
  };

  const applied = (key: FilterKey) => filters[key] !== initialFilters[key];
  const labelFor = (key: FilterKey, fallback: string) => {
    const value = filters[key];
    if (!applied(key)) {
      return fallback;
    }
    if (key === 'period') {
      const [start, end] = value.split('|');
      return start === '전체' ? `~${end.slice(5)}` : `${start.slice(5)}~${end.slice(5)}`;
    }
    if (key === 'region' || key === 'species') {
      return value.split(' > ')[1] ?? value;
    }
    return value === '전체' ? fallback : value;
  };

  const renderReport = ({ item }: { item: Report }) => (
    <Pressable
      accessibilityLabel={`${item.name} ${activeTab === 'found' ? '발견 제보' : '실종 신고'} 상세`}
      accessibilityRole="button"
      onPress={() =>
        activeTab === 'found'
          ? navigation.navigate('FoundReportDetail', { reportId: item.id })
          : navigation.navigate('LostReportDetail', { reportId: item.id })
      }
      style={({ pressed }) => [
        styles.card,
        { height: cardWidth + 118, width: cardWidth },
        pressed && styles.pressed,
      ]}
    >
      <Image source={item.images[0]} style={[styles.cardImage, { height: cardWidth }]} />
      <Pressable
        accessibilityLabel={`${item.name} 북마크 ${
          bookmarkedIds.includes(item.id) ? '해제' : '등록'
        }`}
        accessibilityRole="button"
        accessibilityState={{ selected: bookmarkedIds.includes(item.id) }}
        hitSlop={8}
        onPress={event => {
          event.stopPropagation();
          toggleBookmark(item.id);
        }}
        style={({ pressed }) => [styles.bookmarkButton, pressed && styles.pressed]}
      >
        {bookmarkedIds.includes(item.id) ? <BookmarkActiveIcon /> : <BookmarkIcon />}
      </Pressable>
      <View
        style={[
          styles.badge,
          { top: cardWidth - 24 },
          (item.status === '완료' || item.status === '종료') && styles.closedBadge,
          activeTab === 'lost' && item.status === '실종' && styles.lostBadge,
        ]}
      >
        <Text style={styles.badgeText}>
          {activeTab === 'found' ? (item.status === '완료' ? '종료' : '제보') : item.status}
        </Text>
      </View>
      <Text numberOfLines={1} style={styles.cardTitle}>
        {item.name}
      </Text>
      <View style={styles.metaRow}>
        <MapIcon height={18} width={18} />
        <Text numberOfLines={1} style={styles.metaText}>
          {item.location}
        </Text>
      </View>
      <View style={styles.metaRow}>
        <DateIcon height={18} width={18} />
        <Text style={styles.metaText}>{item.date}</Text>
      </View>
      <Text
        numberOfLines={1}
        style={styles.summary}
      >{`${item.age} | ${item.gender} | ${item.color} | ${item.weight}`}</Text>
      <Text numberOfLines={1} style={styles.summary}>
        {item.description}
      </Text>
    </Pressable>
  );

  return (
    <View style={styles.container}>
      <View style={styles.tabs}>
        <Pressable
          accessibilityRole="tab"
          accessibilityState={{ selected: activeTab === 'found' }}
          onPress={() => selectTab('found')}
          style={styles.tab}
        >
          <Text style={activeTab === 'found' ? styles.activeTabText : styles.tabText}>제보</Text>
          <View style={activeTab === 'found' ? styles.activeIndicator : styles.inactiveIndicator} />
        </Pressable>
        <Pressable
          accessibilityRole="tab"
          accessibilityState={{ selected: activeTab === 'lost' }}
          onPress={() => selectTab('lost')}
          style={styles.tab}
        >
          <Text style={activeTab === 'lost' ? styles.activeTabText : styles.tabText}>실종</Text>
          <View style={activeTab === 'lost' ? styles.activeIndicator : styles.inactiveIndicator} />
        </Pressable>
      </View>
      <View style={styles.listArea}>
        <FlatList
          ListEmptyComponent={
            <View style={styles.empty}>
              {activeTab === 'found' && foundReportsLoading ? (
                <ReportListSkeleton label="발견 신고 목록 불러오는 중" />
              ) : activeTab === 'lost' && lostReportsLoading ? (
                <ReportListSkeleton label="실종 신고 목록 불러오는 중" />
              ) : activeTab === 'found' && foundReportsError ? (
                <>
                  <Text style={styles.emptyTitle}>{foundReportsError}</Text>
                  <Pressable accessibilityRole="button" onPress={() => fetchFoundReports()}>
                    <Text style={styles.retryText}>다시 시도</Text>
                  </Pressable>
                </>
              ) : activeTab === 'lost' && lostReportsError ? (
                <>
                  <Text style={styles.emptyTitle}>{lostReportsError}</Text>
                  <Pressable accessibilityRole="button" onPress={() => fetchLostReports()}>
                    <Text style={styles.retryText}>다시 시도</Text>
                  </Pressable>
                </>
              ) : (
                <>
                  <Text style={styles.emptyTitle}>검색 결과가 없습니다</Text>
                  <Text style={styles.emptyBody}>다른 검색어나 필터 조건을 선택해보세요.</Text>
                </>
              )}
            </View>
          }
          columnWrapperStyle={reports.length ? styles.columns : undefined}
          contentContainerStyle={[
            styles.list,
            { paddingBottom: 104 + bottomInset, paddingTop: SEARCH_FILTERS_HEIGHT },
            !reports.length && styles.emptyList,
          ]}
          data={reports}
          decelerationRate="fast"
          keyExtractor={item => item.id}
          keyboardDismissMode="on-drag"
          numColumns={2}
          onScroll={handleListScroll}
          ref={listRef}
          renderItem={renderReport}
          scrollEventThrottle={16}
          showsVerticalScrollIndicator={false}
        />
        <Animated.View
          pointerEvents={controlsVisible ? 'auto' : 'none'}
          style={[
            styles.searchAndFilters,
            activeFilter && styles.searchAndFiltersAboveSheet,
            {
              filter: [
                {
                  blur: controlsProgress.interpolate({
                    inputRange: [0, 1],
                    outputRange: [3, 0],
                  }),
                },
              ],
              opacity: controlsProgress.interpolate({
                inputRange: [0, 0.35, 1],
                outputRange: [0, 0, 1],
              }),
              transform: [
                {
                  translateY: controlsProgress.interpolate({
                    inputRange: [0, 1],
                    outputRange: [-SEARCH_FILTERS_HEIGHT, 0],
                  }),
                },
              ],
            },
          ]}
        >
          <View style={styles.searchRow}>
            <SearchBar onChangeText={setQuery} placeholder="특징으로 검색해보세요" value={query} />
            {activeTab === 'found' ? (
              <Pressable
                accessibilityLabel="지도로 보기"
                accessibilityRole="button"
                onPress={() => navigation.navigate('FoundReportMap')}
                style={({ pressed }) => [styles.mapButton, pressed && styles.pressed]}
              >
                <MapButtonIcon height={24} width={24} />
              </Pressable>
            ) : null}
          </View>
          <ScrollView
            contentContainerStyle={styles.filters}
            horizontal
            showsHorizontalScrollIndicator={false}
            style={[styles.filterScroll, activeFilter && styles.filterScrollAboveSheet]}
          >
            {filterItems.map(item => (
              <FilterChip
                accessibilityLabel={`${item.label} 필터`}
                applied={applied(item.key)}
                key={item.key}
                label={labelFor(item.key, item.label)}
                onPress={() => setActiveFilter(current => (current === item.key ? null : item.key))}
                open={activeFilter === item.key}
              />
            ))}
          </ScrollView>
        </Animated.View>
      </View>
      <AdoptionFilterSheet
        activeFilter={activeFilter}
        filters={filters}
        onChange={(key, value) => setFilters(current => ({ ...current, [key]: value }))}
        onClose={() => setActiveFilter(null)}
        regionOptions={regionOptions}
        speciesOptions={speciesOptions}
        statusOptions={activeTab === 'found' ? ['전체', '제보', '종료'] : ['전체', '실종', '종료']}
      />
    </View>
  );
}

function ReportListSkeleton({ label }: { label: string }) {
  return (
    <SkeletonGroup accessibilityLabel={label} style={styles.gridSkeleton}>
      {[0, 1, 2, 3].map(index => (
        <View key={index} style={styles.gridSkeletonCard}>
          <SkeletonBlock style={styles.gridSkeletonImage} />
          <SkeletonBlock style={styles.gridSkeletonTitle} />
          <SkeletonBlock style={styles.gridSkeletonLine} />
          <SkeletonBlock style={styles.gridSkeletonShortLine} />
        </View>
      ))}
    </SkeletonGroup>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  tabs: { flexDirection: 'row', height: 56 },
  tab: { alignItems: 'center', flex: 1, justifyContent: 'space-between', paddingTop: 16 },
  activeTabText: { ...typography.bodySemiBold16, color: colors.green400, height: 20 },
  tabText: { ...typography.bodyRegular16, color: colors.gray600, height: 20 },
  activeIndicator: { backgroundColor: colors.green400, height: 4, width: '100%' },
  inactiveIndicator: { backgroundColor: colors.gray200, height: 1, width: '100%' },
  listArea: { flex: 1 },
  searchAndFilters: {
    backgroundColor: colors.white,
    height: SEARCH_FILTERS_HEIGHT,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
    zIndex: 2,
  },
  searchAndFiltersAboveSheet: { zIndex: 21 },
  searchRow: { flexDirection: 'row', gap: 8, marginHorizontal: 20, marginTop: 16 },
  mapButton: {
    alignItems: 'center',
    backgroundColor: '#ECFDF3',
    borderColor: colors.green400,
    borderRadius: radius.md,
    borderWidth: 1,
    height: 46,
    justifyContent: 'center',
    marginTop: 2,
    width: 46,
  },
  filterScroll: { flexGrow: 0, height: 54 },
  filterScrollAboveSheet: { position: 'relative', zIndex: 21 },
  filters: { gap: 6, paddingHorizontal: 20, paddingVertical: 14 },
  list: { paddingHorizontal: 20 },
  columns: { gap: 12 },
  card: { marginBottom: 18 },
  cardImage: { borderRadius: radius.sm, width: '100%' },
  bookmarkButton: {
    alignItems: 'center',
    height: 24,
    justifyContent: 'center',
    position: 'absolute',
    right: 8,
    top: 8,
    width: 24,
    zIndex: 2,
  },
  badge: {
    alignItems: 'center',
    backgroundColor: colors.blue,
    borderBottomLeftRadius: radius.xs,
    borderTopRightRadius: radius.xs,
    height: 24,
    justifyContent: 'center',
    left: 0,
    paddingHorizontal: 10,
    position: 'absolute',
  },
  lostBadge: { backgroundColor: '#FA705B' },
  closedBadge: { backgroundColor: colors.gray400, opacity: 1 },
  badgeText: { ...typography.captionSemiBold12, color: colors.white },
  cardTitle: { ...typography.titleBold18, color: colors.black, marginTop: 8 },
  metaRow: { alignItems: 'center', flexDirection: 'row', gap: 4, width: '100%' },
  metaText: {
    ...typography.bodyRegular14,
    color: colors.gray700,
    flex: 1,
    minWidth: 0,
  },
  summary: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 3 },
  emptyList: { flexGrow: 1 },
  empty: { alignItems: 'center', flex: 1, justifyContent: 'center', paddingBottom: 120 },
  emptyTitle: { ...typography.titleSemiBold18, color: colors.gray600 },
  emptyBody: { ...typography.bodyRegular14, color: colors.gray400, marginTop: 8 },
  retryText: { ...typography.bodySemiBold16, color: colors.green400, marginTop: 12 },
  gridSkeleton: { flexDirection: 'row', flexWrap: 'wrap', gap: 14, width: '100%' },
  gridSkeletonCard: { gap: 9, width: '47%' },
  gridSkeletonImage: { aspectRatio: 1, borderRadius: 10, width: '100%' },
  gridSkeletonTitle: { height: 17, width: '64%' },
  gridSkeletonLine: { height: 12, width: '90%' },
  gridSkeletonShortLine: { height: 12, width: '58%' },
  pressed: { opacity: 0.7 },
});
