import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Animated,
  Easing,
  FlatList,
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  useWindowDimensions,
  View,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
} from 'react-native';

import type { RootStackParamList } from '../../app/navigation/types';
import DateIcon from '../../assets/icons/common/date.svg';
import BookmarkActiveIcon from '../../assets/icons/common/bookmark-active.svg';
import BookmarkIcon from '../../assets/icons/common/bookmark.svg';
import FilterArrowIcon from '../../assets/icons/common/filter-arrow.svg';
import MapButtonIcon from '../../assets/icons/common/map-2.svg';
import MapIcon from '../../assets/icons/common/map.svg';
import SearchIcon from '../../assets/icons/common/search.svg';
import { colors, radius, typography } from '../../theme';
import { useFoundReportEditStore } from '../../stores/foundReportEditStore';
import { useLostReportEditStore } from '../../stores/lostReportEditStore';
import { useReportStatusStore } from '../../stores/reportStatusStore';
import { useAdoptionBookmarkStore } from '../../stores/adoptionBookmarkStore';
import {
  AdoptionFilterSheet,
  type AdoptionFilters,
  type FilterKey,
} from '../Home/AdoptionFilterSheet';
import { foundReports, type FoundReport } from './data';
import { lostReports, type LostReport } from '../lost-report/data';

type Props = {
  bottomInset: number;
  navigation: NativeStackNavigationProp<RootStackParamList, 'Home'>;
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
      (region === '전체' || report.location.startsWith(region.replace(/(특별시|광역시)$/, '시'))) &&
      (filters.species === '전체' || report.species === filters.species.split(' > ')[0]) &&
      (filters.status === '전체' || report.status === filters.status) &&
      (filters.gender === '전체' || report.gender === filters.gender) &&
      (filters.neutered === '전체' || report.neutered === (filters.neutered === '중성화 O'))
    );
  });
  return [...filtered].sort((a, b) =>
    filters.sort === '최신순' ? b.date.localeCompare(a.date) : a.date.localeCompare(b.date),
  );
};

export function FoundReportListContent({ bottomInset, navigation }: Props) {
  const { width } = useWindowDimensions();
  const cardWidth = (width - 52) / 2;
  const listRef = useRef<FlatList<Report>>(null);
  const controlsProgress = useRef(new Animated.Value(1)).current;
  const controlsVisibleRef = useRef(true);
  const lastScrollOffset = useRef(0);
  const scrollDirection = useRef<'down' | 'up' | null>(null);
  const scrollDirectionDistance = useRef(0);
  const [controlsVisible, setControlsVisible] = useState(true);
  const [activeTab, setActiveTab] = useState<ReportTab>('found');
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
  const reports = useMemo(() => {
    const source = (activeTab === 'found' ? foundReports : lostReports)
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
    return filterReports(source, query, filters);
  }, [
    activeTab,
    deletedFoundReportIds,
    deletedLostReportIds,
    filters,
    foundReportEdits,
    lostReportEdits,
    query,
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

  const selectTab = (tab: ReportTab) => {
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
          (item.status === '완료' || item.status === '종료') && styles.completedBadge,
          activeTab === 'lost' && item.status === '실종' && styles.lostBadge,
          activeTab === 'lost' && item.status === '종료' && styles.closedBadge,
        ]}
      >
        <Text style={styles.badgeText}>{activeTab === 'found' ? '제보' : item.status}</Text>
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
              <Text style={styles.emptyTitle}>검색 결과가 없습니다</Text>
              <Text style={styles.emptyBody}>다른 검색어나 필터 조건을 선택해보세요.</Text>
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
            <View style={styles.searchBox}>
              <SearchIcon height={24} width={24} />
              <TextInput
                onChangeText={setQuery}
                placeholder="특징으로 검색해보세요"
                placeholderTextColor={colors.gray400}
                style={styles.searchInput}
                value={query}
              />
            </View>
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
              <Pressable
                accessibilityLabel={`${item.label} 필터`}
                key={item.key}
                onPress={() => setActiveFilter(current => (current === item.key ? null : item.key))}
                style={[
                  styles.filterChip,
                  activeFilter === item.key && styles.filterOpen,
                  applied(item.key) && styles.filterApplied,
                ]}
              >
                <Text style={[styles.filterText, applied(item.key) && styles.filterAppliedText]}>
                  {labelFor(item.key, item.label)}
                </Text>
                <FilterArrowIcon height={14} width={14} />
              </Pressable>
            ))}
          </ScrollView>
        </Animated.View>
      </View>
      <AdoptionFilterSheet
        activeFilter={activeFilter}
        filters={filters}
        onChange={(key, value) => setFilters(current => ({ ...current, [key]: value }))}
        onClose={() => setActiveFilter(null)}
        statusOptions={
          activeTab === 'found' ? ['전체', '진행 중', '완료'] : ['전체', '실종', '종료']
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  tabs: { flexDirection: 'row', height: 56 },
  tab: { alignItems: 'center', flex: 1, justifyContent: 'flex-end' },
  activeTabText: { ...typography.bodySemiBold16, color: colors.green400, marginBottom: 16 },
  tabText: { ...typography.bodyRegular16, color: colors.gray600, marginBottom: 16 },
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
  searchBox: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    flex: 1,
    flexDirection: 'row',
    height: 50,
    paddingHorizontal: 14,
  },
  searchInput: {
    ...typography.bodyRegular16,
    color: colors.gray700,
    flex: 1,
    marginLeft: 4,
    paddingVertical: 0,
  },
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
  filterChip: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.pill,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 2,
    height: 26,
    paddingLeft: 10,
    paddingRight: 5,
  },
  filterOpen: { backgroundColor: '#EBEAE5', borderColor: colors.gray500 },
  filterApplied: { backgroundColor: '#ECFDF3', borderColor: colors.green400 },
  filterText: { ...typography.bodyRegular14, color: colors.gray500 },
  filterAppliedText: { color: colors.green400 },
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
  completedBadge: { opacity: 0.85 },
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
  pressed: { opacity: 0.7 },
});
