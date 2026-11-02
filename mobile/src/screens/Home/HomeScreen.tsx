import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMemo, useState } from 'react';
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
import BookmarkIcon from '../../assets/icons/common/bookmark.svg';
import DateIcon from '../../assets/icons/common/date.svg';
import FilterArrowIcon from '../../assets/icons/common/filter-arrow.svg';
import MapIcon from '../../assets/icons/common/map.svg';
import NotificationIcon from '../../assets/icons/common/notification.svg';
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
import { colors, radius, typography } from '../../theme';
import { adoptionAnimals, type AdoptionAnimal } from '../adoption/data';

type Props = NativeStackScreenProps<RootStackParamList, 'Home'>;
const filters = ['기간', '지역', '축종', '상태', '성별', '중성화여부', '최신순'];
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
  const [bookmarkedIds, setBookmarkedIds] = useState<Set<string>>(() => new Set());
  const [selectedTab, setSelectedTab] =
    useState<(typeof navigationItems)[number]['key']>('adoption');
  const animals = useMemo(
    () =>
      adoptionAnimals.filter(item =>
        `${item.name} ${item.location} ${item.summary}`.toLowerCase().includes(query.toLowerCase()),
      ),
    [query],
  );
  const toggleBookmark = (animalId: string) => {
    setBookmarkedIds(current => {
      const next = new Set(current);
      if (next.has(animalId)) {
        next.delete(animalId);
      } else {
        next.add(animalId);
      }
      return next;
    });
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
        accessibilityLabel={`${item.name} 북마크`}
        accessibilityRole="button"
        accessibilityState={{ selected: bookmarkedIds.has(item.id) }}
        hitSlop={8}
        onPress={event => {
          event.stopPropagation();
          toggleBookmark(item.id);
        }}
        style={({ pressed }) => [styles.bookmarkButton, pressed && styles.pressed]}
      >
        {bookmarkedIds.has(item.id) ? (
          <BookmarkActiveIcon height={24} width={24} />
        ) : (
          <BookmarkIcon height={24} width={24} />
        )}
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

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <View style={styles.header}>
        <Image
          resizeMode="contain"
          source={require('../../assets/images/logo.png')}
          style={styles.logo}
        />
        <Pressable accessibilityLabel="알림" accessibilityRole="button" style={styles.headerIcon}>
          <NotificationIcon />
        </Pressable>
      </View>
      <View style={styles.searchBox}>
        <Text style={styles.searchIcon}>⌕</Text>
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
      >
        {filters.map(filter => (
          <Pressable key={filter} style={styles.filterChip}>
            <Text style={styles.filterText}>{filter}</Text>
            <FilterArrowIcon />
          </Pressable>
        ))}
      </ScrollView>
      <FlatList
        columnWrapperStyle={styles.column}
        contentContainerStyle={[styles.listContent, { paddingBottom: 104 + insets.bottom }]}
        data={animals}
        keyExtractor={item => item.id}
        numColumns={2}
        renderItem={renderAnimal}
        showsVerticalScrollIndicator={false}
      />
      <View style={[styles.navigationWrap, { bottom: insets.bottom + 16 }]}>
        <BottomNavigation
          centerAction={{
            accessibilityLabel: '등록',
            icon: <PlusIcon height={24} width={24} />,
            onPress: () => undefined,
          }}
          items={navigationItems}
          onSelect={setSelectedTab}
          selectedKey={selectedTab}
        />
      </View>
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
  searchIcon: { color: colors.green100, fontSize: 28, marginRight: 4 },
  searchInput: { ...typography.bodyRegular16, color: colors.gray700, flex: 1, paddingVertical: 0 },
  filters: { gap: 6, paddingHorizontal: 20, paddingVertical: 12 },
  filterChip: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.pill,
    borderWidth: 1,
    height: 28,
    flexDirection: 'row',
    gap: 2,
    justifyContent: 'center',
    paddingHorizontal: 10,
  },
  filterText: { ...typography.bodyRegular14, color: colors.gray500 },
  listContent: { paddingHorizontal: 20 },
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
