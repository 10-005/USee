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
import BookmarkIcon from '../../assets/icons/common/bookmark.svg';
import DateIcon from '../../assets/icons/common/date.svg';
import MapIcon from '../../assets/icons/common/map.svg';
import { AppHeader } from '../../components/common';
import { useAdoptionBookmarkStore } from '../../stores/adoptionBookmarkStore';
import { useFoundReportEditStore } from '../../stores/foundReportEditStore';
import { useLostReportEditStore } from '../../stores/lostReportEditStore';
import { useReportStatusStore } from '../../stores/reportStatusStore';
import { colors, radius, typography } from '../../theme';
import { foundReports } from '../found-report/data';
import { lostReports } from '../lost-report/data';

type Props = NativeStackScreenProps<RootStackParamList, 'MyPosts'>;
type MyPostTab = 'found' | 'lost';

type MyPost = {
  date: string;
  description: string;
  id: string;
  image: ImageSourcePropType;
  location: string;
  name: string;
  status: '제보' | '실종' | '종료';
  summary: string;
};

const tabs: Array<{ key: MyPostTab; label: string }> = [
  { key: 'found', label: '제보' },
  { key: 'lost', label: '실종' },
];

export function MyPostsScreen({ navigation }: Props) {
  const { width } = useWindowDimensions();
  const cardWidth = (width - 52) / 2;
  const [activeTab, setActiveTab] = useState<MyPostTab>('found');
  const bookmarkedIds = useAdoptionBookmarkStore(state => state.bookmarkedIds);
  const toggleBookmark = useAdoptionBookmarkStore(state => state.toggleBookmark);
  const deletedFoundIds = useFoundReportEditStore(state => state.deletedIds);
  const foundEdits = useFoundReportEditStore(state => state.editsById);
  const deletedLostIds = useLostReportEditStore(state => state.deletedIds);
  const lostEdits = useLostReportEditStore(state => state.editsById);
  const statusById = useReportStatusStore(state => state.statusById);

  const posts = useMemo<MyPost[]>(() => {
    const isFound = activeTab === 'found';
    const source = isFound ? foundReports : lostReports;
    const deletedIds = isFound ? deletedFoundIds : deletedLostIds;
    const edits = isFound ? foundEdits : lostEdits;

    return source
      .filter(report => report.isMine && !deletedIds.includes(report.id))
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
          status: completed ? '종료' : isFound ? '제보' : '실종',
          summary: `${current.age} | ${current.gender} | ${current.color} | ${current.weight}`,
        };
      });
  }, [activeTab, deletedFoundIds, deletedLostIds, foundEdits, lostEdits, statusById]);

  const renderPost = ({ item }: { item: MyPost }) => {
    const bookmarked = bookmarkedIds.includes(item.id);
    return (
      <Pressable
        accessibilityLabel={`${item.name} 내 게시글 상세`}
        accessibilityRole="button"
        onPress={() =>
          navigation.navigate(activeTab === 'found' ? 'FoundReportDetail' : 'LostReportDetail', {
            reportId: item.id,
          })
        }
        style={({ pressed }) => [
          styles.card,
          { height: cardWidth + 118, width: cardWidth },
          pressed && styles.pressed,
        ]}
      >
        <Image source={item.image} style={[styles.cardImage, { height: cardWidth }]} />
        <Pressable
          accessibilityLabel={`${item.name} 북마크 ${bookmarked ? '해제' : '등록'}`}
          accessibilityRole="button"
          accessibilityState={{ selected: bookmarked }}
          hitSlop={8}
          onPress={event => {
            event.stopPropagation();
            toggleBookmark(item.id);
          }}
          style={({ pressed }) => [styles.bookmarkButton, pressed && styles.pressed]}
        >
          {bookmarked ? <BookmarkActiveIcon /> : <BookmarkIcon />}
        </Pressable>
        <View
          style={[
            styles.statusBadge,
            { top: cardWidth - 24 },
            item.status === '제보' && styles.foundBadge,
            item.status === '실종' && styles.lostBadge,
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
  };

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <AppHeader onBackPress={navigation.goBack} title="내 게시글" />
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
            <Text style={styles.emptyTitle}>작성한 게시글이 없어요</Text>
            <Text style={styles.emptyBody}>등록한 신고가 이곳에 표시됩니다.</Text>
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
  activeTabText: { ...typography.bodySemiBold16, color: colors.green400, height: 20 },
  tabText: { ...typography.bodyRegular16, color: colors.gray600, height: 20 },
  activeIndicator: { backgroundColor: colors.green400, height: 4, width: '100%' },
  indicator: { backgroundColor: '#EBEAE5', height: 2, width: '100%' },
  list: { paddingHorizontal: 20, paddingTop: 21 },
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
    borderBottomLeftRadius: radius.xs,
    borderTopRightRadius: radius.xs,
    height: 24,
    justifyContent: 'center',
    left: 0,
    paddingHorizontal: 10,
    position: 'absolute',
  },
  foundBadge: { backgroundColor: colors.blue },
  lostBadge: { backgroundColor: '#FA705B' },
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
