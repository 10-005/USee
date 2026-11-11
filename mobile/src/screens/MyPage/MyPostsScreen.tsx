import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
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
import { AppHeader, SkeletonBlock, SkeletonGroup } from '../../components/common';
import { getApiErrorDisplayMessage } from '../../services/api';
import { bookmarkKey, type BookmarkTargetType } from '../../services/bookmarks';
import {
  getMyFoundReports,
  type FoundReportSummary,
} from '../../services/foundReports/foundReportApi';
import {
  getMyMissingReports,
  type MissingReportSummary,
} from '../../services/missingReports';
import { useAdoptionBookmarkStore } from '../../stores/adoptionBookmarkStore';
import { colors, radius, typography } from '../../theme';
import { formatKoreanAddress } from '../../utils/address';

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

const fallbackImage = require('../../assets/images/mypage/img_pet_card.png');

const genderLabel = (gender: FoundReportSummary['gender']) =>
  gender === 'MALE' ? '수컷' : gender === 'FEMALE' ? '암컷' : '모름';

const summaryText = (report: FoundReportSummary | MissingReportSummary) =>
  [
    report.age ?? '나이 모름',
    genderLabel(report.gender),
    report.classification?.colorName ?? '털색 모름',
    report.weight ?? '몸무게 모름',
  ].join(' | ');

export const mapMyFoundPost = (report: FoundReportSummary): MyPost => ({
  date: report.occurredDttm.slice(0, 10),
  description: '',
  id: String(report.foundReportSeq),
  image: report.representativeImageUrl ? { uri: report.representativeImageUrl } : fallbackImage,
  location: formatKoreanAddress(report.address),
  name: report.classification?.breedName ?? '발견 동물',
  status: report.status === 'CLOSED' ? '종료' : '제보',
  summary: summaryText(report),
});

export const mapMyMissingPost = (report: MissingReportSummary): MyPost => ({
  date: report.occurredDttm.slice(0, 10),
  description: '',
  id: String(report.missingReportSeq),
  image: report.representativeImageUrl ? { uri: report.representativeImageUrl } : fallbackImage,
  location: formatKoreanAddress(report.address),
  name: report.classification?.breedName ?? '실종 동물',
  status: report.status === 'CLOSED' ? '종료' : '실종',
  summary: summaryText(report),
});

export function MyPostsScreen({ navigation }: Props) {
  const { width } = useWindowDimensions();
  const cardWidth = (width - 52) / 2;
  const [activeTab, setActiveTab] = useState<MyPostTab>('found');
  const [postsByTab, setPostsByTab] = useState<Record<MyPostTab, MyPost[]>>({
    found: [],
    lost: [],
  });
  const [loadedTabs, setLoadedTabs] = useState<Record<MyPostTab, boolean>>({
    found: false,
    lost: false,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const bookmarkKeys = useAdoptionBookmarkStore(state => state.bookmarkKeys);
  const loadBookmarks = useAdoptionBookmarkStore(state => state.loadBookmarks);
  const pendingBookmarkKeys = useAdoptionBookmarkStore(state => state.pendingKeys);
  const toggleBookmark = useAdoptionBookmarkStore(state => state.toggleBookmark);
  const bookmarkType: BookmarkTargetType = activeTab === 'found' ? 'FOUND' : 'MISSING';
  const posts = postsByTab[activeTab];

  const loadPosts = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const nextPosts =
        activeTab === 'found'
          ? (await getMyFoundReports()).map(mapMyFoundPost)
          : (await getMyMissingReports()).map(mapMyMissingPost);
      setPostsByTab(current => ({ ...current, [activeTab]: nextPosts }));
      setLoadedTabs(current => ({ ...current, [activeTab]: true }));
    } catch (loadError) {
      setError(getApiErrorDisplayMessage(loadError, '내 게시글을 불러오지 못했어요.'));
    } finally {
      setLoading(false);
    }
  }, [activeTab]);

  useFocusEffect(
    useCallback(() => {
      loadPosts().catch(() => undefined);
    }, [loadPosts]),
  );

  useFocusEffect(
    useCallback(() => {
      loadBookmarks(bookmarkType).catch(() => undefined);
    }, [bookmarkType, loadBookmarks]),
  );

  const renderPost = ({ item }: { item: MyPost }) => {
    const bookmarked = bookmarkKeys.includes(bookmarkKey(bookmarkType, item.id));
    const bookmarkPending = pendingBookmarkKeys.includes(bookmarkKey(bookmarkType, item.id));
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
        <MyPostImage size={cardWidth} source={item.image} />
        <Pressable
          accessibilityLabel={`${item.name} 북마크 ${bookmarked ? '해제' : '등록'}`}
          accessibilityRole="button"
          accessibilityState={{ busy: bookmarkPending, disabled: bookmarkPending, selected: bookmarked }}
          disabled={bookmarkPending}
          hitSlop={8}
          onPress={event => {
            event.stopPropagation();
            toggleBookmark(bookmarkType, item.id).catch(() =>
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
        {item.description ? (
          <Text numberOfLines={1} style={styles.description}>
            {item.description}
          </Text>
        ) : null}
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
      {loading && !loadedTabs[activeTab] ? (
        <MyPostsSkeleton cardWidth={cardWidth} />
      ) : error ? (
        <View style={styles.empty}>
          <Text style={styles.emptyTitle}>{error}</Text>
          <Pressable accessibilityRole="button" onPress={loadPosts} style={styles.retryButton}>
            <Text style={styles.retryText}>다시 시도</Text>
          </Pressable>
        </View>
      ) : (
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
      )}
    </SafeAreaView>
  );
}

function MyPostsSkeleton({ cardWidth }: { cardWidth: number }) {
  return (
    <SkeletonGroup accessibilityLabel="내 게시글 불러오는 중" style={styles.skeletonList}>
      {[0, 1, 2, 3].map(index => (
        <View key={index} style={{ width: cardWidth }}>
          <SkeletonBlock style={{ height: cardWidth, width: cardWidth }} />
          <SkeletonBlock style={styles.skeletonTitle} />
          <SkeletonBlock style={styles.skeletonLine} />
          <SkeletonBlock style={styles.skeletonShortLine} />
        </View>
      ))}
    </SkeletonGroup>
  );
}

function MyPostImage({ size, source }: { size: number; source: ImageSourcePropType }) {
  const remote = typeof source === 'object' && source !== null && 'uri' in source;
  const [loading, setLoading] = useState(remote);

  return (
    <View style={[styles.cardImage, { height: size }]}>
      <Image
        onError={() => setLoading(false)}
        onLoadEnd={() => setLoading(false)}
        onLoadStart={() => remote && setLoading(true)}
        resizeMode="cover"
        source={source}
        style={styles.cardImageContent}
      />
      {loading ? <SkeletonBlock style={styles.cardImageSkeleton} /> : null}
    </View>
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
  cardImage: { borderRadius: radius.sm, overflow: 'hidden', width: '100%' },
  cardImageContent: { height: '100%', width: '100%' },
  cardImageSkeleton: {
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
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
  retryButton: { marginTop: 16, paddingHorizontal: 20, paddingVertical: 10 },
  retryText: {
    ...typography.bodyRegular14,
    color: colors.green400,
    fontFamily: 'Pretendard-SemiBold',
  },
  skeletonList: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
    paddingHorizontal: 20,
    paddingTop: 21,
  },
  skeletonTitle: { height: 20, marginTop: 8, width: '70%' },
  skeletonLine: { height: 14, marginTop: 8, width: '100%' },
  skeletonShortLine: { height: 14, marginTop: 6, width: '76%' },
  pressed: { opacity: 0.7 },
});
