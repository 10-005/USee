import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useMemo, useState } from 'react';
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
import DateIcon from '../../assets/icons/common/date.svg';
import MapIcon from '../../assets/icons/common/map.svg';
import { AppHeader, SkeletonBlock, SkeletonGroup } from '../../components/common';
import type { BookmarkItem, BookmarkTargetType } from '../../services/bookmarks';
import { useAdoptionBookmarkStore } from '../../stores/adoptionBookmarkStore';
import { colors, radius, typography } from '../../theme';
import { formatKoreanAddress } from '../../utils/address';

import { PostTabPager } from './PostTabPager';

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
  { key: 'found', label: '제보' },
  { key: 'lost', label: '실종' },
];

const targetTypeByTab: Record<SavedPostTab, BookmarkTargetType> = {
  adoption: 'SHELTER_ANIMAL',
  found: 'FOUND',
  lost: 'MISSING',
};

const fallbackImage = require('../../assets/images/mypage/img_pet_card.png');

const statusLabel = (item: BookmarkItem) => {
  if (item.targetType === 'MISSING') {
    return item.postSummary.status === 'CLOSED' ? '종료' : '실종';
  }
  if (item.targetType === 'FOUND') {
    return item.postSummary.status === 'CLOSED' ? '종료' : '제보';
  }
  return item.postSummary.status === 'PROTECTING' ? '보호중' : '종료';
};

const mapSavedPost = (item: BookmarkItem): SavedPost => {
  const summary = item.postSummary;
  const gender = summary.gender === 'MALE' ? '수컷' : summary.gender === 'FEMALE' ? '암컷' : '모름';
  return {
    date: summary.occurredDttm?.slice(0, 10) ?? '-',
    description: '',
    id: String(item.targetSeq),
    image: summary.representativeImageUrl ? { uri: summary.representativeImageUrl } : fallbackImage,
    location: formatKoreanAddress(summary.address),
    name: summary.classification?.breedName ?? '동물 정보 없음',
    status: statusLabel(item),
    summary: [
      summary.age ?? '나이 모름',
      gender,
      summary.classification?.colorName ?? '털색 모름',
    ].join(' | '),
  };
};

export function SavedPostsScreen({ navigation }: Props) {
  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <AppHeader onBackPress={navigation.goBack} title="저장한 게시글" />
      <PostTabPager
        tabs={tabs}
        tabStyle={styles.tabs}
        renderTab={(label, selected, select) => (
          <Pressable
            accessibilityRole="tab"
            accessibilityState={{ selected }}
            onPress={select}
            style={styles.tab}
          >
            <Text style={selected ? styles.activeTabText : styles.tabText}>{label}</Text>
            <View style={selected ? styles.activeIndicator : styles.indicator} />
          </Pressable>
        )}
        renderPage={tab => <SavedPostsPage navigation={navigation} tab={tab} />}
      />
    </SafeAreaView>
  );
}

function SavedPostsPage({ navigation, tab }: Pick<Props, 'navigation'> & { tab: SavedPostTab }) {
  const { width } = useWindowDimensions();
  const cardWidth = (width - 52) / 2;
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState('');
  const items = useAdoptionBookmarkStore(state => state.items);

  const loadBookmarks = useAdoptionBookmarkStore(state => state.loadBookmarks);
  const pendingBookmarkKeys = useAdoptionBookmarkStore(state => state.pendingKeys);
  const toggleBookmark = useAdoptionBookmarkStore(state => state.toggleBookmark);
  const targetType = targetTypeByTab[tab];

  const posts = useMemo<SavedPost[]>(() => {
    return items.filter(item => item.targetType === targetType).map(mapSavedPost);
  }, [items, targetType]);

  const loadPosts = useCallback(async () => {
    setError('');
    try {
      await loadBookmarks(targetType);
      setLoaded(true);
    } catch {
      setError(useAdoptionBookmarkStore.getState().error ?? '저장한 게시글을 불러오지 못했어요.');
    }
  }, [loadBookmarks, targetType]);
  useFocusEffect(
    useCallback(() => {
      loadPosts();
    }, [loadPosts]),
  );

  const openPost = (postId: string) => {
    if (tab === 'adoption') {
      navigation.navigate('AdoptionDetail', { animalId: postId });
      return;
    }
    navigation.navigate(tab === 'found' ? 'FoundReportDetail' : 'LostReportDetail', {
      reportId: postId,
    });
  };

  const renderPost = ({ item }: { item: SavedPost }) => {
    const bookmarkPending = pendingBookmarkKeys.includes(`${targetType}:${item.id}`);
    return (
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
        <SavedPostImage size={cardWidth} source={item.image} />
        <Pressable
          accessibilityLabel={`${item.name} 북마크 해제`}
          accessibilityRole="button"
          accessibilityState={{ busy: bookmarkPending, disabled: bookmarkPending, selected: true }}
          disabled={bookmarkPending}
          hitSlop={8}
          onPress={event => {
            event.stopPropagation();
            toggleBookmark(targetType, item.id).catch(() =>
              Alert.alert('북마크 해제 실패', '잠시 후 다시 시도해 주세요.'),
            );
          }}
          style={({ pressed }) => [styles.bookmarkButton, pressed && styles.pressed]}
        >
          {bookmarkPending ? (
            <ActivityIndicator color={colors.green400} size="small" />
          ) : (
            <BookmarkActiveIcon />
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
    <View style={styles.tabContent}>
      {!loaded && !error ? (
        <SavedPostsSkeleton cardWidth={cardWidth} />
      ) : error ? (
        <View style={styles.empty}>
          <Text style={styles.emptyTitle}>{error}</Text>
          <Pressable onPress={loadPosts} style={styles.retryButton}>
            <Text style={styles.retryText}>다시 시도</Text>
          </Pressable>
        </View>
      ) : (
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
          keyExtractor={item => item.id}
          numColumns={2}
          renderItem={renderPost}
          showsVerticalScrollIndicator={false}
        />
      )}
    </View>
  );
}

function SavedPostsSkeleton({ cardWidth }: { cardWidth: number }) {
  return (
    <SkeletonGroup accessibilityLabel="저장한 게시글 불러오는 중" style={styles.skeletonList}>
      {[0, 1, 2, 3].map(index => (
        <View key={index} style={[styles.skeletonCard, { width: cardWidth }]}>
          <SkeletonBlock style={{ height: cardWidth, width: cardWidth }} />
          <SkeletonBlock style={styles.skeletonTitle} />
          <SkeletonBlock style={styles.skeletonLine} />
          <SkeletonBlock style={styles.skeletonLine} />
          <SkeletonBlock style={styles.skeletonShortLine} />
        </View>
      ))}
    </SkeletonGroup>
  );
}

function SavedPostImage({ size, source }: { size: number; source: ImageSourcePropType }) {
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
  tabContent: { flex: 1 },
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
  retryButton: { marginTop: 14, padding: 12 },
  retryText: { ...typography.bodyRegular14, color: colors.green400 },
  skeletonList: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
    paddingHorizontal: 20,
    paddingTop: 27,
  },
  skeletonCard: { marginBottom: 18 },
  skeletonTitle: { height: 20, marginTop: 8, width: '68%' },
  skeletonLine: { height: 14, marginTop: 6, width: '100%' },
  skeletonShortLine: { height: 14, marginTop: 6, width: '74%' },
  pressed: { opacity: 0.7 },
});
