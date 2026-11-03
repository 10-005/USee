import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  FlatList,
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  useWindowDimensions,
  View,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import BookmarkActiveIcon from '../../assets/icons/common/bookmark-active.svg';
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
import { BottomNavigation } from '../../components/common';
import { useAdoptionBookmarkStore } from '../../stores/adoptionBookmarkStore';
import { colors, radius, typography } from '../../theme';
import { adoptionAnimals, type AdoptionAnimal } from '../adoption/data';
import { MockAdoptionScreen } from '../MockAdoption/MockAdoptionScreen';
import { MyPageScreen } from '../MyPage/MyPageScreen';
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

export function HomeScreen({ navigation }: Props) {
  const insets = useSafeAreaInsets();
  const { width: screenWidth } = useWindowDimensions();
  const cardWidth = (screenWidth - 52) / 2;
  const [query, setQuery] = useState('');
  const listRef = useRef<FlatList<AdoptionAnimal>>(null);
  const [activeFilter, setActiveFilter] = useState<FilterKey | null>(null);
  const [filterValues, setFilterValues] = useState(initialFilters);
  const bookmarkedIds = useAdoptionBookmarkStore(state => state.bookmarkedIds);
  const toggleBookmark = useAdoptionBookmarkStore(state => state.toggleBookmark);
  const [selectedTab, setSelectedTab] = useState<
    (typeof navigationItems)[number]['key'] | 'mockAdoption'
  >('adoption');
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
      {bookmarkedIds.includes(item.id) ? (
        <Pressable
          accessibilityLabel={`${item.name} 북마크 해제`}
          accessibilityRole="button"
          accessibilityState={{ selected: true }}
          hitSlop={8}
          onPress={event => {
            event.stopPropagation();
            toggleBookmark(item.id);
          }}
          style={({ pressed }) => [styles.bookmarkButton, pressed && styles.pressed]}
        >
          <BookmarkActiveIcon />
        </Pressable>
      ) : null}
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

  const navigationBar = (
    <View style={[styles.navigationWrap, { bottom: insets.bottom + 16 }]}>
      <BottomNavigation
        centerAction={{
          accessibilityLabel: '모의입양',
          icon: <PlusIcon height={24} width={24} />,
          onPress: () => setSelectedTab('mockAdoption'),
          selected: selectedTab === 'mockAdoption',
        }}
        items={navigationItems}
        onSelect={setSelectedTab}
        selectedKey={selectedTab}
      />
    </View>
  );

  if (selectedTab === 'mockAdoption') {
    return (
      <SafeAreaView edges={['top']} style={styles.safeArea}>
        <MockAdoptionScreen />
        {navigationBar}
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      {selectedTab === 'mypage' ? (
        <MyPageScreen
          onOpenAdoptionApplications={() => navigation.navigate('AdoptionApplicationList')}
          onOpenPets={() => navigation.navigate('PetList')}
        />
      ) : (
        <>
          <View style={styles.header}>
            <Image
              resizeMode="contain"
              source={require('../../assets/images/logo.png')}
              style={styles.logo}
            />
            <Pressable
              accessibilityLabel="알림"
              accessibilityRole="button"
              style={styles.headerIcon}
            >
              <NotificationIcon />
            </Pressable>
          </View>
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
            style={styles.filterScroll}
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
          <FlatList
            columnWrapperStyle={styles.column}
            contentContainerStyle={[styles.listContent, { paddingBottom: 104 + insets.bottom }]}
            data={animals}
            key={listKey}
            keyExtractor={item => item.id}
            numColumns={2}
            ref={listRef}
            renderItem={renderAnimal}
            showsVerticalScrollIndicator={false}
          />
        </>
      )}
      {navigationBar}
      {selectedTab !== 'mypage' ? (
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
  searchBox: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    height: 50,
    marginHorizontal: 20,
    paddingHorizontal: 14,
  },
  searchInput: {
    ...typography.bodyRegular16,
    color: colors.gray700,
    flex: 1,
    marginLeft: 4,
    paddingVertical: 0,
  },
  filterScroll: { flexGrow: 0, height: 50 },
  filters: { gap: 6, paddingHorizontal: 20, paddingVertical: 12 },
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
    height: 24,
    position: 'absolute',
    right: 8,
    top: 8,
    width: 24,
    zIndex: 1,
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
  cardMeta: { ...typography.bodyRegular14, color: colors.gray700, marginTop: 3 },
  cardMetaRow: { alignItems: 'center', flexDirection: 'row', gap: 4 },
  cardSummary: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 3 },
  cardDescription: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 3 },
  navigationWrap: { left: 25, position: 'absolute', right: 25 },
  pressed: { opacity: 0.75 },
});
