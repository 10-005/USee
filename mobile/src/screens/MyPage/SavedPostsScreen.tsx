import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMemo, useState } from 'react';
import {
  FlatList,
  Image,
  Pressable,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
  type ImageSourcePropType,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import BookmarkActiveIcon from '../../assets/icons/common/bookmark-active.svg';
import DateIcon from '../../assets/icons/common/date.svg';
import MapIcon from '../../assets/icons/common/map.svg';
import { AppHeader } from '../../components/common';
import { useAdoptionBookmarkStore } from '../../stores/adoptionBookmarkStore';
import { useFoundReportEditStore } from '../../stores/foundReportEditStore';
import { useLostReportEditStore } from '../../stores/lostReportEditStore';
import { useReportStatusStore } from '../../stores/reportStatusStore';
import { colors, radius, typography } from '../../theme';
import { adoptionAnimals } from '../adoption/data';
import { foundReports } from '../found-report/data';
import { lostReports } from '../lost-report/data';

type Props = NativeStackScreenProps<RootStackParamList, 'SavedPosts'>;
type SavedPostTab = 'adoption' | 'found' | 'lost';

type SavedPost = {
  date: string;
  description: string;
  id: string;
  image: ImageSourcePropType;
  location: string;
  name: string;
  status: string;
  summary: string;
};

const tabs: Array<{ key: SavedPostTab; label: string }> = [
  { key: 'adoption', label: '입양' },
  { key: 'found', label: '보호' },
  { key: 'lost', label: '실종' },
];

export function SavedPostsScreen({ navigation }: Props) {
  const { width } = useWindowDimensions();
  const cardWidth = (width - 52) / 2;
  const [activeTab, setActiveTab] = useState<SavedPostTab>('adoption');
  const bookmarkedIds = useAdoptionBookmarkStore(state => state.bookmarkedIds);
  const toggleBookmark = useAdoptionBookmarkStore(state => state.toggleBookmark);
  const deletedFoundIds = useFoundReportEditStore(state => state.deletedIds);
  const foundEdits = useFoundReportEditStore(state => state.editsById);
  const deletedLostIds = useLostReportEditStore(state => state.deletedIds);
  const lostEdits = useLostReportEditStore(state => state.editsById);
  const statusById = useReportStatusStore(state => state.statusById);

  const posts = useMemo<SavedPost[]>(() => {
    if (activeTab === 'adoption') {
      return adoptionAnimals
        .filter(animal => bookmarkedIds.includes(animal.id))
        .map(animal => ({
          date: animal.date,
          description: animal.description,
          id: animal.id,
          image: animal.image,
          location: animal.location,
          name: animal.name,
          status: animal.status,
          summary: animal.summary,
        }));
    }

    const isFound = activeTab === 'found';
    const source = isFound ? foundReports : lostReports;
    const deletedIds = isFound ? deletedFoundIds : deletedLostIds;
    const edits = isFound ? foundEdits : lostEdits;

    return source
      .filter(report => bookmarkedIds.includes(report.id) && !deletedIds.includes(report.id))
      .map(report => {
        const current = { ...report, ...edits[report.id] };
        const completed =
          statusById[report.id] === 'completed' ||
          (!statusById[report.id] && (current.status === '완료' || current.status === '종료'));
        return {
          date: current.date,
          description: current.description,
          id: current.id,
          image: current.images[0],
          location: current.location,
          name: current.name,
          status: completed ? '종료' : isFound ? '보호' : '실종',
          summary: `${current.age} | ${current.gender} | ${current.color} | ${current.weight}`,
        };
      });
  }, [
    activeTab,
    bookmarkedIds,
    deletedFoundIds,
    deletedLostIds,
    foundEdits,
    lostEdits,
    statusById,
  ]);

  const openPost = (postId: string) => {
    if (activeTab === 'adoption') {
      navigation.navigate('AdoptionDetail', { animalId: postId });
      return;
    }
    navigation.navigate(activeTab === 'found' ? 'FoundReportDetail' : 'LostReportDetail', {
      reportId: postId,
    });
  };

  const renderPost = ({ item }: { item: SavedPost }) => (
    <Pressable
      accessibilityLabel={`${item.name} 저장한 게시글 상세`}
      accessibilityRole="button"
      onPress={() => openPost(item.id)}
      style={({ pressed }) => [
        styles.card,
        { height: cardWidth + 118, width: cardWidth },
        pressed && styles.pressed,
      ]}
    >
      <Image source={item.image} style={[styles.cardImage, { height: cardWidth }]} />
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
      <View style={styles.metaRow}>
        <MapIcon height={15} width={15} />
        <Text numberOfLines={1} style={styles.metaText}>
          {item.location}
        </Text>
      </View>
      <View style={styles.metaRow}>
        <DateIcon height={15} width={15} />
        <Text style={styles.metaText}>{item.date}</Text>
      </View>
      <Text numberOfLines={1} style={styles.summary}>
        {item.summary}
      </Text>
      <Text numberOfLines={1} style={styles.description}>
        {item.description}
      </Text>
    </Pressable>
  );

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <AppHeader onBackPress={navigation.goBack} title="저장한 게시글" />
      <View accessibilityRole="tablist" style={styles.tabs}>
        {tabs.map(tab => {
          const selected = tab.key === activeTab;
          return (
            <Pressable
              accessibilityRole="tab"
              accessibilityState={{ selected }}
              key={tab.key}
              onPress={() => setActiveTab(tab.key)}
              style={styles.tab}
            >
              <Text style={selected ? styles.activeTabText : styles.tabText}>{tab.label}</Text>
              <View style={selected ? styles.activeIndicator : styles.indicator} />
            </Pressable>
          );
        })}
      </View>
      <FlatList
        ListEmptyComponent={
          <View style={styles.empty}>
            <Text style={styles.emptyTitle}>저장한 게시글이 없어요</Text>
            <Text style={styles.emptyBody}>관심 있는 게시글을 북마크해 보세요.</Text>
          </View>
        }
        columnWrapperStyle={posts.length ? styles.columns : undefined}
        contentContainerStyle={[styles.list, !posts.length && styles.emptyList]}
        data={posts}
        decelerationRate="fast"
        key={activeTab}
        keyExtractor={item => item.id}
        numColumns={2}
        renderItem={renderPost}
        showsVerticalScrollIndicator={false}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  tabs: { flexDirection: 'row', height: 60 },
  tab: { alignItems: 'center', flex: 1, justifyContent: 'space-between', paddingTop: 20 },
  activeTabText: { ...typography.bodyRegular16, color: colors.green400 },
  tabText: { ...typography.bodyRegular16, color: colors.gray600 },
  activeIndicator: { backgroundColor: colors.green400, height: 4, width: '100%' },
  indicator: { backgroundColor: '#EBEAE5', height: 2, width: '100%' },
  list: { paddingHorizontal: 20, paddingTop: 27 },
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
  statusBadge: {
    backgroundColor: colors.green100,
    borderBottomLeftRadius: radius.xs,
    borderTopRightRadius: radius.xs,
    height: 24,
    justifyContent: 'center',
    left: 0,
    paddingHorizontal: 10,
    position: 'absolute',
  },
  endedBadge: { backgroundColor: colors.gray400 },
  statusText: { ...typography.captionSemiBold12, color: colors.white },
  cardTitle: { ...typography.titleBold18, color: colors.black, marginTop: 8 },
  metaRow: { alignItems: 'center', flexDirection: 'row', gap: 4, width: '100%' },
  metaText: { ...typography.bodyRegular14, color: colors.gray700, flex: 1, minWidth: 0 },
  summary: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 3 },
  description: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 3 },
  emptyList: { flexGrow: 1 },
  empty: { alignItems: 'center', flex: 1, justifyContent: 'center', paddingBottom: 100 },
  emptyTitle: { ...typography.titleSemiBold18, color: colors.gray600 },
  emptyBody: { ...typography.bodyRegular14, color: colors.gray400, marginTop: 8 },
  pressed: { opacity: 0.7 },
});
