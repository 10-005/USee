import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useIsFocused } from '@react-navigation/native';
import { startTransition, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
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

import PullRefreshContainer from '../../components/common/PullRefreshContainer';
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
import {
  BottomNavigation,
  RegistrationMenu,
  SkeletonBlock,
  SkeletonGroup,
} from '../../components/common';
import { useAdoptionBookmarkStore } from '../../stores/adoptionBookmarkStore';
import { bookmarkKey } from '../../services/bookmarks';
import { useAdoptionAnimalStore } from '../../stores/adoptionAnimalStore';
import { useCatalogStore } from '../../stores/catalogStore';
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
import { useProfileStore } from '../../stores/profileStore';
import { useFoundReportDraftStore } from '../../stores/foundReportDraftStore';
import { useLostReportDraftStore } from '../../stores/lostReportDraftStore';
import { MockAdoptionShortcut } from '../MockAdoption/MockAdoptionShortcut';
import { colors, radius, typography } from '../../theme';
import type { AdoptionAnimal } from '../adoption/data';
import { MyPageScreen } from '../MyPage/MyPageScreen';
import { FoundReportListContent } from '../found-report/FoundReportListContent';
import { ChatListScreen } from '../chat/ChatListScreen';
import {
  AdoptionFilterSheet,
  type AdoptionFilters,
  type FilterKey,
  type TwoColumnFilterOptions,
} from './AdoptionFilterSheet';
import { useUnreadNotificationBadge } from './useUnreadNotificationBadge';
import { RetainedTab } from './RetainedTab';

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
const initialFilters: AdoptionFilters = {
  gender: '전체',
  neutered: '전체',
  period: '전체',
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
    label: '내 정보',
    icon: (selected: boolean) =>
      selected ? (
        <MyPageActiveIcon height={24} width={24} />
      ) : (
        <MyPageIcon height={24} width={24} />
      ),
  },
] as const;

export function HomeScreen({ navigation, route }: Props) {
  const isFocused = useIsFocused();
  const insets = useSafeAreaInsets();
  const { width: screenWidth } = useWindowDimensions();
  const cardWidth = (screenWidth - 52) / 2;
  const [refreshingAnimals, setRefreshingAnimals] = useState(false);
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
  const [reportFilterOpen, setReportFilterOpen] = useState(false);
  const [filterValues, setFilterValues] = useState(initialFilters);
  const bookmarkKeys = useAdoptionBookmarkStore(state => state.bookmarkKeys);
  const loadBookmarks = useAdoptionBookmarkStore(state => state.loadBookmarks);
  const pendingBookmarkKeys = useAdoptionBookmarkStore(state => state.pendingKeys);
  const toggleBookmark = useAdoptionBookmarkStore(state => state.toggleBookmark);
  useEffect(() => {
    loadBookmarks('SHELTER_ANIMAL').catch(() => undefined);
  }, [loadBookmarks]);
  const apiAnimals = useAdoptionAnimalStore(state => state.animals);
  const fetchAnimals = useAdoptionAnimalStore(state => state.fetchAnimals);
  const animalsLoading = useAdoptionAnimalStore(state => state.isLoading);
  const animalsError = useAdoptionAnimalStore(state => state.error);
  const districts = useCatalogStore(state => state.districts);
  const speciesCatalog = useCatalogStore(state => state.species);
  const breedsBySpecies = useCatalogStore(state => state.breedsBySpecies);
  const loadDistricts = useCatalogStore(state => state.loadDistricts);
  const loadSpecies = useCatalogStore(state => state.loadSpecies);
  const loadBreeds = useCatalogStore(state => state.loadBreeds);
  const loadProfile = useProfileStore(state => state.loadProfile);
  const resetFoundReportDraft = useFoundReportDraftStore(state => state.resetDraft);
  const resetLostReportDraft = useLostReportDraftStore(state => state.resetDraft);
  const [selectedTab, setSelectedTab] = useState<(typeof navigationItems)[number]['key']>(
    route.params?.initialTab ?? 'adoption',
  );
  const hasUnreadNotification = useUnreadNotificationBadge();
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
    if (selectedTab === 'adoption') {
      loadDistricts().catch(() => undefined);
      loadSpecies().catch(() => undefined);
    }
  }, [loadDistricts, loadSpecies, selectedTab]);

  useEffect(() => {
    if (selectedTab === 'adoption' && speciesCatalog.length > 0) {
      Promise.all(speciesCatalog.map(item => loadBreeds(item.speciesSeq))).catch(() => undefined);
    }
  }, [loadBreeds, selectedTab, speciesCatalog]);

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

  const adoptionSearchParams = useMemo(() => {
    const [periodStart = '전체', periodEnd = ''] = filterValues.period.split('|');
    const [sidoName, sigunguName] = filterValues.region.split(' > ');
    const speciesName = filterValues.species.split(' > ')[0];
    const district = sigunguName
      ? districts.find(item => item.sidoName === sidoName && item.sigunguName === sigunguName)
      : undefined;
    const selectedSpecies = speciesCatalog.find(item => item.speciesName === speciesName);
    return {
      districtCode: district?.districtCode,
      gender:
        filterValues.gender === '수컷'
          ? ('MALE' as const)
          : filterValues.gender === '암컷'
          ? ('FEMALE' as const)
          : undefined,
      isNeutered:
        filterValues.neutered === '중성화 O'
          ? true
          : filterValues.neutered === '중성화 X'
          ? false
          : undefined,
      keyword: query.trim() || undefined,
      regFrom: periodStart === '전체' ? undefined : periodStart.replaceAll('.', '-'),
      regTo: periodEnd ? periodEnd.replaceAll('.', '-') : undefined,
      sort: filterValues.sort === '오래된 순' ? 'regDttm,asc' : 'regDttm,desc',
      speciesSeq: selectedSpecies?.speciesSeq,
    };
  }, [districts, filterValues, query, speciesCatalog]);

  useEffect(() => {
    if (selectedTab !== 'adoption') {
      return;
    }
    const timer = setTimeout(() => {
      fetchAnimals(adoptionSearchParams).catch(() => undefined);
    }, 250);
    return () => clearTimeout(timer);
  }, [adoptionSearchParams, fetchAnimals, selectedTab]);

  const animals = useMemo(() => {
    const [sidoName, sigunguName] = filterValues.region.split(' > ');
    const sidoCodes = new Set(
      districts.filter(item => item.sidoName === sidoName).map(item => item.districtCode),
    );
    const selectedBreed = filterValues.species.split(' > ')[1];
    return apiAnimals.filter(item => {
      const matchesRegion =
        sidoName === '전체' ||
        Boolean(sigunguName) ||
        Boolean(item.districtCode && sidoCodes.has(item.districtCode));
      const matchesBreed =
        !selectedBreed || selectedBreed === '전체' || item.name === selectedBreed;
      const matchesStatus = filterValues.status === '전체' || item.status === filterValues.status;
      return matchesRegion && matchesBreed && matchesStatus;
    });
  }, [apiAnimals, districts, filterValues.region, filterValues.species, filterValues.status]);
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

  const refreshList = useCallback(async () => {
    if (refreshingAnimals) {
      return;
    }
    setRefreshingAnimals(true);
    try {
      await fetchAnimals(adoptionSearchParams);
    } finally {
      setRefreshingAnimals(false);
    }
  }, [adoptionSearchParams, fetchAnimals, refreshingAnimals]);

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

  useEffect(() => {
    if (selectedTab === 'mypage') {
      loadProfile().catch(() => undefined);
    }
  }, [loadProfile, selectedTab]);

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

  const renderAnimal = useCallback(({ item }: { item: AdoptionAnimal }) => {
    const key = bookmarkKey('SHELTER_ANIMAL', item.id);
    const bookmarkPending = pendingBookmarkKeys.includes(key);
    const bookmarked = bookmarkKeys.includes(key);
    return (
      <Pressable
        accessibilityRole="button"
        onPress={() => navigation.navigate('AdoptionDetail', { animalId: item.id })}
        style={({ pressed }) => [
          styles.card,
          { height: cardWidth + 97, width: cardWidth },
          pressed && styles.pressed,
        ]}
      >
        <Image
          resizeMode="cover"
          source={item.image}
          style={[styles.cardImage, { height: cardWidth, width: cardWidth }]}
        />
        <Pressable
          accessibilityLabel={`[${item.species}] ${item.name} 북마크 ${
            bookmarked ? '해제' : '등록'
          }`}
          accessibilityRole="button"
          accessibilityState={{
            busy: bookmarkPending,
            disabled: bookmarkPending,
            selected: bookmarked,
          }}
          disabled={bookmarkPending}
          hitSlop={8}
          onPress={event => {
            event.stopPropagation();
            toggleBookmark('SHELTER_ANIMAL', item.id).catch(() =>
              Alert.alert('북마크 변경 실패', '잠시 후 다시 시도해 주세요.'),
            );
          }}
          style={({ pressed }) => [styles.bookmarkButton, pressed && styles.pressed]}
        >
          {bookmarkPending ? (
            <ActivityIndicator color={colors.green400} size="small" />
          ) : bookmarked ? (
            <BookmarkActiveIcon />
          ) : (
            <BookmarkIcon />
          )}
        </Pressable>
        <View
          style={[
            styles.statusBadge,
            { top: cardWidth - 24 },
            (item.status === '종료' || item.isAdoptionUnavailable) && styles.endedBadge,
          ]}
        >
          <Text style={styles.statusText}>{item.status}</Text>
        </View>
        <Text numberOfLines={1} style={styles.cardTitle}>
          [{item.species}] {item.name}
        </Text>
        <View style={styles.cardMetaRow}>
          <MapIcon height={14} width={14} />
          <Text numberOfLines={1} style={styles.cardMeta}>
            {item.location}
          </Text>
        </View>
        <View style={styles.cardMetaRow}>
          <DateIcon height={14} width={14} />
          <Text style={styles.cardMeta}>{item.date}</Text>
        </View>
        <Text numberOfLines={1} style={styles.cardSummary}>
          {item.summary}
        </Text>
      </Pressable>
    );
  }, [bookmarkKeys, cardWidth, navigation, pendingBookmarkKeys, toggleBookmark]);

  const navigationBar = (
    <View style={[styles.navigationWrap, { bottom: insets.bottom + 16 }]}>
      <BottomNavigation
        backgroundBlurEnabled={isFocused}
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
          startTransition(() => setSelectedTab(key));
        }}
        selectedKey={selectedTab}
      />
    </View>
  );

  const registrationMenu = registrationMenuOpen ? (
    <RegistrationMenu
      bottom={insets.bottom + 92}
      onClose={() => setRegistrationMenuOpen(false)}
      onSelectFound={() => {
        setRegistrationMenuOpen(false);
        resetFoundReportDraft();
        navigation.navigate('PetRegistration', { flow: 'found-report' });
      }}
      onSelectLost={() => {
        setRegistrationMenuOpen(false);
        resetLostReportDraft();
        navigation.navigate('LostReportPetSelection');
      }}
    />
  ) : null;


  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <RetainedTab active={selectedTab === 'chat'}>
        <ChatListScreen onOpenRoom={roomId => navigation.navigate('ChatRoom', { roomId })} />
      </RetainedTab>
      <RetainedTab active={selectedTab === 'mypage'}>
        <MyPageScreen
          onOpenMockAdoption={() => navigation.navigate('MockAdoptionOverview')}
          onOpenMyPosts={() => navigation.navigate('MyPosts')}
          onOpenAdoptionApplications={() => navigation.navigate('AdoptionApplicationList')}
          onOpenPets={() => navigation.navigate('PetList')}
          onOpenProfileEdit={() => navigation.navigate('ProfileEdit')}
          onOpenNotices={() => navigation.navigate('NoticeList')}
          onOpenSettings={() => navigation.navigate('Settings')}
          onOpenSavedPosts={() => navigation.navigate('SavedPosts')}
        />
      </RetainedTab>
      <RetainedTab active={selectedTab === 'search'}>
        <FoundReportListContent
          bottomInset={insets.bottom}
          initialTab={route.params?.initialReportTab}
          navigation={navigation}
          onFilterOpenChange={setReportFilterOpen}
        />
      </RetainedTab>
      <RetainedTab active={selectedTab === 'adoption'}>
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
              {hasUnreadNotification ? (
                <View accessibilityLabel="읽지 않은 알림" style={styles.notificationDot} />
              ) : null}
            </Pressable>
          </View>
          <View style={styles.listArea}>
            <PullRefreshContainer style={styles.pullList}>
              <FlatList
                ListEmptyComponent={
                  <View style={styles.emptyState}>
                    {animalsLoading ? (
                      <AdoptionListSkeleton cardWidth={cardWidth} />
                    ) : animalsError ? (
                      <>
                        <Text style={styles.emptyTitle}>{animalsError}</Text>
                        <Pressable
                          accessibilityRole="button"
                          onPress={() => fetchAnimals(adoptionSearchParams)}
                        >
                          <Text style={styles.retryText}>다시 시도</Text>
                        </Pressable>
                      </>
                    ) : (
                      <>
                        <Text style={styles.emptyTitle}>검색 결과가 없습니다</Text>
                        <Text style={styles.emptyDescription}>
                          다른 검색어나 필터 조건을 선택해보세요.
                        </Text>
                      </>
                    )}
                  </View>
                }
                columnWrapperStyle={styles.column}
                contentContainerStyle={[
                  styles.listContent,
                  { paddingBottom: 112 + insets.bottom, paddingTop: SEARCH_FILTERS_HEIGHT },
                ]}
                refreshing={refreshingAnimals}
                progressViewOffset={SEARCH_FILTERS_HEIGHT}
                onRefresh={refreshList}
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
            </PullRefreshContainer>
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
                    <FilterArrowIcon height={12} width={12} />
                  </Pressable>
                ))}
              </ScrollView>
            </Animated.View>
          </View>
        </>
      </RetainedTab>
      {!activeFilter && !reportFilterOpen && !registrationMenuOpen ? (
        <MockAdoptionShortcut
          bottom={insets.bottom + 104}
          onOpen={petId => navigation.navigate('MockAdoptionActivity', { petId })}
          session={shortcutSession}
        />
      ) : null}
      {activeFilter || reportFilterOpen ? null : navigationBar}
      {!activeFilter ? registrationMenu : null}
      {selectedTab === 'adoption' ? (
        <AdoptionFilterSheet
          activeFilter={activeFilter}
          filters={filterValues}
          onChange={updateFilter}
          onClose={() => setActiveFilter(null)}
          regionOptions={regionOptions}
          speciesOptions={speciesOptions}
        />
      ) : null}
    </SafeAreaView>
  );
}

function AdoptionListSkeleton({ cardWidth }: { cardWidth: number }) {
  return (
    <SkeletonGroup accessibilityLabel="보호동물 목록 불러오는 중" style={styles.gridSkeleton}>
      {[0, 1, 2, 3].map(index => (
        <View key={index} style={[styles.gridSkeletonCard, { width: cardWidth }]}>
          <SkeletonBlock style={styles.gridSkeletonImage} />
          <SkeletonBlock style={styles.gridSkeletonTitle} />
          <SkeletonBlock style={styles.gridSkeletonLine} />
        </View>
      ))}
    </SkeletonGroup>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  header: { alignItems: 'center', flexDirection: 'row', height: 56, justifyContent: 'center' },
  logo: { height: 34, width: 88 },
  headerIcon: { position: 'absolute', right: 20 },
  pullList: { flex: 1 },
  notificationDot: {
    backgroundColor: '#FA705B',
    borderColor: colors.white,
    borderRadius: 5,
    borderWidth: 1,
    height: 10,
    position: 'absolute',
    right: -1,
    top: -1,
    width: 10,
  },
  listArea: { flex: 1 },
  emptyState: { alignItems: 'center', flex: 1, justifyContent: 'center' },
  emptyTitle: { ...typography.bodySemiBold16, color: colors.gray700 },
  emptyDescription: { ...typography.bodyRegular14, color: colors.gray400, marginTop: 6 },
  retryText: { ...typography.bodySemiBold16, color: colors.green400, marginTop: 12 },
  gridSkeleton: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
    width: '100%',
  },
  gridSkeletonCard: { gap: 8 },
  gridSkeletonImage: { aspectRatio: 1, width: '100%' },
  gridSkeletonTitle: { height: 18, width: '70%' },
  gridSkeletonLine: { height: 14, width: '90%' },
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
  card: { marginBottom: 16 },
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
    includeFontPadding: false,
    minWidth: 0,
  },
  cardMetaRow: { alignItems: 'center', flexDirection: 'row', gap: 4, width: '100%' },
  cardSummary: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 3 },
  navigationWrap: { left: 25, position: 'absolute', right: 25, zIndex: 31 },
  pressed: { opacity: 0.75 },
});
