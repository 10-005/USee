import type { NativeStackScreenProps } from '@react-navigation/native-stack';
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
  TextInput,
  useWindowDimensions,
  View,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import BookmarkActiveIcon from '../../assets/icons/common/bookmark-active.svg';
import BookmarkIcon from '../../assets/icons/common/bookmark.svg';
import DateIcon from '../../assets/icons/common/date.svg';
import FilterArrowIcon from '../../assets/icons/common/filter-arrow.svg';
import MapIcon from '../../assets/icons/common/map.svg';
import NotificationIcon from '../../assets/icons/common/notification.svg';
import SearchInputIcon from '../../assets/icons/common/search.svg';
import AdoptionActiveIcon from '../../assets/icons/navigation/adoption-active.svg';
import AdoptionIcon from '../../assets/icons/navigation/adoption.svg';
import ChatActiveIcon from '../../assets/icons/navigation/chat-active.svg';
import ChatIcon from '../../assets/icons/navigation/chat.svg';
import MyPageActiveIcon from '../../assets/icons/navigation/mypage-active.svg';
import MyPageIcon from '../../assets/icons/navigation/mypage.svg';
import PlusIcon from '../../assets/icons/navigation/plus.svg';
import SearchActiveIcon from '../../assets/icons/navigation/search-active.svg';
import SearchIcon from '../../assets/icons/navigation/search.svg';
import { BottomNavigation, RegistrationMenu } from '../../components/common';
import { useAdoptionBookmarkStore } from '../../stores/adoptionBookmarkStore';
import {
  applyActiveSimulation,
  getActiveSimulation,
} from '../../services/simulation/activeSession';
import {
  isSimulationAutoEnded,
  subscribeSimulationEnded,
} from '../../services/simulation/sessionEnd';
import {
  getActiveMockAdoption,
  type MockAdoptionSession,
} from '../../services/storage/mockAdoptionStorage';
import { MockAdoptionOverviewScreen } from '../MockAdoption/MockAdoptionOverviewScreen';
import { MockAdoptionShortcut } from '../MockAdoption/MockAdoptionShortcut';
import { colors, radius, typography } from '../../theme';
import { adoptionAnimals, type AdoptionAnimal } from '../adoption/data';
import { MyPageScreen } from '../MyPage/MyPageScreen';
import { FoundReportListContent } from '../found-report/FoundReportListContent';
import { ChatListScreen } from '../chat/ChatListScreen';
import { AdoptionFilterSheet, type AdoptionFilters, type FilterKey } from './AdoptionFilterSheet';

type Props = NativeStackScreenProps<RootStackParamList, 'Home'>;
const filterItems: Array<{ key: FilterKey; label: string }> = [
  { key: 'period', label: '기간' },
  { key: 'region', label: '지역' },
  { key: 'species', label: '축종' },
  { key: 'status', label: '상태' },
  { key: 'gender', label: '성별' },
  { key: 'neutered', label: '중성화여부' },
  { key: 'sort', label: '최신순' },
];
const today = new Date();
const todayLabel = `${today.getFullYear()}.${String(today.getMonth() + 1).padStart(
  2,
  '0',
)}.${String(today.getDate()).padStart(2, '0')}`;
const initialFilters: AdoptionFilters = {
  gender: '전체',
  neutered: '전체',
  period: `전체|${todayLabel}`,
  region: '전체',
  sort: '최신순',
  species: '전체',
  status: '전체',
};
const SEARCH_FILTERS_HEIGHT = 120;
const HIDE_CONTROLS_OFFSET = 120;
const HIDE_DIRECTION_DISTANCE = 40;
const SHOW_DIRECTION_DISTANCE = 16;
const navigationItems = [
  {
    key: 'adoption',
    label: '입양',
    icon: (selected: boolean) =>
      selected ? (
        <AdoptionActiveIcon height={24} width={24} />
      ) : (
        <AdoptionIcon height={24} width={24} />
      ),
  },
  {
    key: 'search',
    label: '찾아요',
    icon: (selected: boolean) =>
      selected ? (
        <SearchActiveIcon height={24} width={24} />
      ) : (
        <SearchIcon height={24} width={24} />
      ),
  },
  {
    key: 'chat',
    label: '채팅',
    icon: (selected: boolean) =>
      selected ? <ChatActiveIcon height={24} width={24} /> : <ChatIcon height={24} width={24} />,
  },
  {
    key: 'mypage',
    label: '마이페이지',
    icon: (selected: boolean) =>
      selected ? (
        <MyPageActiveIcon height={24} width={24} />
      ) : (
        <MyPageIcon height={24} width={24} />
      ),
  },
] as const;

export function HomeScreen({ navigation, route }: Props) {
  const insets = useSafeAreaInsets();
  const { width: screenWidth } = useWindowDimensions();
  const cardWidth = (screenWidth - 52) / 2;
  const [query, setQuery] = useState('');
  const listRef = useRef<FlatList<AdoptionAnimal>>(null);
  const controlsProgress = useRef(new Animated.Value(1)).current;
  const controlsVisibleRef = useRef(true);
  const lastScrollOffset = useRef(0);
  const scrollDirection = useRef<'down' | 'up' | null>(null);
  const scrollDirectionDistance = useRef(0);
  const [controlsVisible, setControlsVisible] = useState(true);
  const [activeFilter, setActiveFilter] = useState<FilterKey | null>(null);
  const [registrationMenuOpen, setRegistrationMenuOpen] = useState(false);
  const [filterValues, setFilterValues] = useState(initialFilters);
  const bookmarkedIds = useAdoptionBookmarkStore(state => state.bookmarkedIds);
  const toggleBookmark = useAdoptionBookmarkStore(state => state.toggleBookmark);
  const [selectedTab, setSelectedTab] = useState<
    (typeof navigationItems)[number]['key'] | 'mockAdoption'
  >('adoption');
  const [shortcutSession, setShortcutSession] = useState<MockAdoptionSession | null>(null);
  useEffect(() => {
    let mounted = true;
    getActiveSimulation()
      .then(active => applyActiveSimulation(active))
      .catch(error => (isSimulationAutoEnded(error) ? null : getActiveMockAdoption()))
      .then(value => {
        if (mounted) {
          setShortcutSession(value ?? null);
        }
      });
    return () => {
      mounted = false;
    };
  }, []);
  useEffect(() => subscribeSimulationEnded(() => setShortcutSession(null)), []);
  useEffect(() => {
    if (route.params?.tab !== 'mockAdoption') {
      return;
    }
    setSelectedTab('mockAdoption');
    navigation.setParams({ tab: undefined });
  }, [navigation, route.params?.tab]);
  const animals = useMemo(() => {
    const filtered = adoptionAnimals.filter(item => {
      const queryMatched = `${item.name} ${item.location} ${item.summary}`
        .toLowerCase()
        .includes(query.toLowerCase());
      const region = filterValues.region.split(' > ')[0];
      const [periodStart = '전체', periodEnd = ''] = filterValues.period.split('|');
      const startDate = periodStart.replaceAll('.', '-');
      const endDate = periodEnd.replaceAll('.', '-');
      const periodMatched =
        (periodStart === '전체' || item.date >= startDate) && (!endDate || item.date <= endDate);
      return (
        queryMatched &&
        periodMatched &&
        (region === '전체' || item.location.startsWith(region.replace(/(특별시|광역시)$/, '시'))) &&
        (filterValues.species === '전체' ||
          item.species === filterValues.species.split(' > ')[0]) &&
        (filterValues.status === '전체' || item.status === filterValues.status) &&
        (filterValues.gender === '전체' || item.summary.includes(filterValues.gender)) &&
        (filterValues.neutered === '전체' ||
          item.neutered === (filterValues.neutered === '중성화 O'))
      );
    });
    return [...filtered].sort((first, second) =>
      filterValues.sort === '최신순'
        ? second.date.localeCompare(first.date)
        : first.date.localeCompare(second.date),
    );
  }, [filterValues, query]);
  const listKey = `${query}-${Object.values(filterValues).join('-')}`;

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

  const resetAdoptionListHeader = useCallback(() => {
    controlsProgress.stopAnimation();
    controlsProgress.setValue(1);
    controlsVisibleRef.current = true;
    lastScrollOffset.current = 0;
    scrollDirection.current = null;
    scrollDirectionDistance.current = 0;
    setControlsVisible(true);
  }, [controlsProgress]);

  const scrollAdoptionToTop = useCallback(() => {
    resetAdoptionListHeader();
    listRef.current?.scrollToOffset({ animated: true, offset: 0 });
  }, [resetAdoptionListHeader]);

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
    if (activeFilter) {
      updateControlsVisibility(true);
    }
  }, [activeFilter, updateControlsVisibility]);

  useEffect(() => {
    listRef.current?.scrollToOffset({ animated: false, offset: 0 });
  }, [filterValues, query]);

  const updateFilter = (key: FilterKey, value: string) =>
    setFilterValues(current => ({ ...current, [key]: value }));

  const isFilterApplied = (key: FilterKey) => filterValues[key] !== initialFilters[key];

  const getFilterLabel = (key: FilterKey, defaultLabel: string) => {
    const value = filterValues[key];
    if (key === 'period') {
      if (!isFilterApplied(key)) {
        return defaultLabel;
      }
      const [start, end] = value.split('|');
      return start === '전체' ? `~${end.slice(5)}` : `${start.slice(5)}~${end.slice(5)}`;
    }
    if (key === 'region' || key === 'species') {
      return value === '전체' ? defaultLabel : value.split(' > ')[1] ?? value;
    }
    return value === '전체' ? defaultLabel : value;
  };

  const renderAnimal = ({ item }: { item: AdoptionAnimal }) => (
    <Pressable
      accessibilityRole="button"
      onPress={() => navigation.navigate('AdoptionDetail', { animalId: item.id })}
      style={({ pressed }) => [
        styles.card,
        { height: cardWidth + 118, width: cardWidth },
        pressed && styles.pressed,
      ]}
    >
      <Image
        resizeMode="cover"
        source={item.image}
        style={[styles.cardImage, { height: cardWidth, width: cardWidth }]}
      />
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
          styles.statusBadge,
          { top: cardWidth - 24 },
          item.status === '종료' && styles.endedBadge,
        ]}
      >
        <Text style={styles.statusText}>{item.status}</Text>
      </View>
      <Text numberOfLines={1} style={styles.cardTitle}>
        {item.name}
      </Text>
      <View style={styles.cardMetaRow}>
        <MapIcon />
        <Text numberOfLines={1} style={styles.cardMeta}>
          {item.location}
        </Text>
      </View>
      <View style={styles.cardMetaRow}>
        <DateIcon />
        <Text style={styles.cardMeta}>{item.date}</Text>
      </View>
      <Text numberOfLines={1} style={styles.cardSummary}>
        {item.summary}
      </Text>
      <Text numberOfLines={1} style={styles.cardDescription}>
        {item.description}
      </Text>
    </Pressable>
  );

  const openMockAdoption = () => setSelectedTab('mockAdoption');

  const navigationBar = (
    <View style={[styles.navigationWrap, { bottom: insets.bottom + 16 }]}>
      <BottomNavigation
        centerAction={{
          accessibilityLabel: '등록',
          icon: <PlusIcon height={24} width={24} />,
          onPress: () => {
            Keyboard.dismiss();
            setRegistrationMenuOpen(open => !open);
          },
          selected: registrationMenuOpen,
        }}
        items={navigationItems}
        onSelect={key => {
          Keyboard.dismiss();
          setRegistrationMenuOpen(false);
          if (key === 'adoption') {
            resetAdoptionListHeader();
          }
          setSelectedTab(key);
        }}
        selectedKey={selectedTab}
      />
    </View>
  );

  if (selectedTab === 'mockAdoption' || selectedTab === 'chat') {
    return (
      <SafeAreaView edges={['top']} style={styles.safeArea}>
        {selectedTab === 'chat' ? (
          <ChatListScreen onOpenRoom={roomId => navigation.navigate('ChatRoom', { roomId })} />
        ) : (
          <MockAdoptionOverviewScreen
            onBack={() => setSelectedTab('mypage')}
            onStart={() => navigation.navigate('MockAdoptionSelection')}
            onResume={petId => navigation.navigate('MockAdoptionActivity', { petId })}
          />
        )}
        {selectedTab === 'chat' ? (
          <MockAdoptionShortcut
            bottom={insets.bottom + 104}
            onOpen={petId => navigation.navigate('MockAdoptionActivity', { petId })}
            session={shortcutSession}
          />
        ) : null}
        {navigationBar}
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      {selectedTab === 'mypage' ? (
        <MyPageScreen
          onOpenMockAdoption={openMockAdoption}
          onOpenMyPosts={() => navigation.navigate('MyPosts')}
          onOpenAdoptionApplications={() => navigation.navigate('AdoptionApplicationList')}
          onOpenPets={() => navigation.navigate('PetList')}
          onOpenProfileEdit={() => navigation.navigate('ProfileEdit')}
          onOpenNotices={() => navigation.navigate('NoticeList')}
          onOpenSettings={() => navigation.navigate('Settings')}
          onOpenSavedPosts={() => navigation.navigate('SavedPosts')}
        />
      ) : selectedTab === 'search' ? (
        <FoundReportListContent bottomInset={insets.bottom} navigation={navigation} />
      ) : (
        <>
          <View style={styles.header}>
            <Pressable
              accessibilityLabel="입양 목록 상단으로 이동"
              accessibilityRole="button"
              hitSlop={8}
              onPress={scrollAdoptionToTop}
            >
              <Image
                resizeMode="contain"
                source={require('../../assets/images/logo.png')}
                style={styles.logo}
              />
            </Pressable>
            <Pressable
              accessibilityLabel="알림"
              accessibilityRole="button"
              onPress={() => navigation.navigate('Notifications')}
              style={styles.headerIcon}
            >
              <NotificationIcon />
            </Pressable>
          </View>
          <View style={styles.listArea}>
            <FlatList
              columnWrapperStyle={styles.column}
              contentContainerStyle={[
                styles.listContent,
                { paddingBottom: 104 + insets.bottom, paddingTop: SEARCH_FILTERS_HEIGHT },
              ]}
              data={animals}
              decelerationRate="fast"
              key={listKey}
              keyExtractor={item => item.id}
              keyboardDismissMode="on-drag"
              numColumns={2}
              onScroll={handleListScroll}
              ref={listRef}
              renderItem={renderAnimal}
              showsVerticalScrollIndicator={false}
              scrollEventThrottle={16}
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
              <View style={styles.searchBox}>
                <SearchInputIcon />
                <TextInput
                  onChangeText={setQuery}
                  placeholder="특징으로 검색해보세요"
                  placeholderTextColor={colors.gray400}
                  style={styles.searchInput}
                  value={query}
                />
              </View>
              <ScrollView
                contentContainerStyle={styles.filters}
                horizontal
                showsHorizontalScrollIndicator={false}
                style={[styles.filterScroll, activeFilter && styles.filterScrollAboveSheet]}
              >
                {filterItems.map(filter => (
                  <Pressable
                    accessibilityLabel={`${filter.label} 필터`}
                    accessibilityRole="button"
                    key={filter.key}
                    onPress={() =>
                      setActiveFilter(current => (current === filter.key ? null : filter.key))
                    }
                    style={[
                      styles.filterChip,
                      activeFilter === filter.key && styles.filterChipOpen,
                      isFilterApplied(filter.key) && styles.filterChipApplied,
                    ]}
                  >
                    <Text
                      style={[
                        styles.filterText,
                        isFilterApplied(filter.key) && styles.filterTextApplied,
                      ]}
                    >
                      {getFilterLabel(filter.key, filter.label)}
                    </Text>
                    <FilterArrowIcon />
                  </Pressable>
                ))}
              </ScrollView>
            </Animated.View>
          </View>
        </>
      )}
      {!activeFilter && !registrationMenuOpen ? (
        <MockAdoptionShortcut
          bottom={insets.bottom + 104}
          onOpen={petId => navigation.navigate('MockAdoptionActivity', { petId })}
          session={shortcutSession}
        />
      ) : null}
      {activeFilter ? null : navigationBar}
      {registrationMenuOpen && !activeFilter ? (
        <RegistrationMenu
          bottom={insets.bottom + 84}
          onClose={() => setRegistrationMenuOpen(false)}
          onSelectFound={() => {
            setRegistrationMenuOpen(false);
            navigation.navigate('PetRegistration', { flow: 'found-report' });
          }}
          onSelectLost={() => {
            setRegistrationMenuOpen(false);
            navigation.navigate('LostReportPetSelection');
          }}
        />
      ) : null}
      {selectedTab === 'adoption' ? (
        <AdoptionFilterSheet
          activeFilter={activeFilter}
          filters={filterValues}
          onChange={updateFilter}
          onClose={() => setActiveFilter(null)}
        />
      ) : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  header: { alignItems: 'center', flexDirection: 'row', height: 56, justifyContent: 'center' },
  logo: { height: 34, width: 88 },
  headerIcon: { position: 'absolute', right: 20 },
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
  searchBox: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    height: 50,
    marginHorizontal: 20,
    marginTop: 16,
    paddingHorizontal: 14,
  },
  searchInput: {
    ...typography.bodyRegular16,
    color: colors.gray700,
    flex: 1,
    marginLeft: 4,
    paddingVertical: 0,
  },
  filterScroll: { flexGrow: 0, height: 54 },
  filterScrollAboveSheet: { position: 'relative', zIndex: 21 },
  filters: { gap: 6, paddingHorizontal: 20, paddingVertical: 14 },
  filterChip: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.pill,
    borderWidth: 1,
    height: 26,
    flexDirection: 'row',
    gap: 2,
    justifyContent: 'center',
    paddingLeft: 10,
    paddingRight: 5,
  },
  filterChipOpen: { backgroundColor: '#EBEAE5', borderColor: colors.gray500 },
  filterChipApplied: { backgroundColor: '#ECFDF3', borderColor: colors.green400 },
  filterText: { ...typography.bodyRegular14, color: colors.gray500 },
  filterTextApplied: { color: colors.green400 },
  listContent: { flexGrow: 0, justifyContent: 'flex-start', paddingHorizontal: 20 },
  column: { gap: 12 },
  card: { marginBottom: 18 },
  cardImage: { borderRadius: radius.sm },
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
  statusBadge: {
    alignSelf: 'flex-start',
    backgroundColor: colors.green100,
    borderBottomLeftRadius: radius.xs,
    borderTopRightRadius: radius.xs,
    height: 24,
    justifyContent: 'center',
    left: 0,
    paddingHorizontal: 10,
    position: 'absolute',
    zIndex: 1,
  },
  endedBadge: { backgroundColor: colors.gray400 },
  statusText: { ...typography.captionSemiBold12, color: colors.white },
  cardTitle: { ...typography.titleBold18, color: colors.black, marginTop: 8 },
  cardMeta: {
    ...typography.bodyRegular14,
    color: colors.gray700,
    flex: 1,
    marginTop: 3,
    minWidth: 0,
  },
  cardMetaRow: { alignItems: 'center', flexDirection: 'row', gap: 4, width: '100%' },
  cardSummary: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 3 },
  cardDescription: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 3 },
  navigationWrap: { left: 25, position: 'absolute', right: 25, zIndex: 31 },
  pressed: { opacity: 0.75 },
});
