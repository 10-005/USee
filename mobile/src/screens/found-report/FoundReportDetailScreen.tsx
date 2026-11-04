import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect, useRef, useState } from 'react';
import {
  Alert,
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
  type ScrollViewInstance,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import BookmarkActiveIcon from '../../assets/icons/common/bookmark-active.svg';
import BookmarkIcon from '../../assets/icons/common/bookmark.svg';
import DotsIcon from '../../assets/icons/common/dots-vertical.svg';
import { AppHeader, Button } from '../../components/common';
import { useAdoptionBookmarkStore } from '../../stores/adoptionBookmarkStore';
import { colors, radius, typography } from '../../theme';
import { FoundReportCommentList } from './FoundReportCommentList';
import { getFoundReport } from './data';

type Props = NativeStackScreenProps<RootStackParamList, 'FoundReportDetail'>;

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.infoRow}>
      <Text style={styles.infoLabel}>{label}</Text>
      <Text style={styles.infoValue}>{value}</Text>
    </View>
  );
}

export function getContactDisplayValue(contact?: string) {
  return contact ?? '비공개';
}

export function FoundReportDetailScreen({ navigation, route }: Props) {
  const report = getFoundReport(route.params.reportId);
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const [keyboardVisible, setKeyboardVisible] = useState(false);
  const [page, setPage] = useState(0);
  const listRef = useRef<FlatList>(null);
  const scrollRef = useRef<ScrollViewInstance>(null);
  const bookmarked = useAdoptionBookmarkStore(state => state.bookmarkedIds.includes(report.id));
  const toggleBookmark = useAdoptionBookmarkStore(state => state.toggleBookmark);

  const onMomentumEnd = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    setPage(Math.round(event.nativeEvent.contentOffset.x / width));
  };

  useEffect(() => {
    const showSubscription = Keyboard.addListener('keyboardDidShow', () =>
      setKeyboardVisible(true),
    );
    const hideSubscription = Keyboard.addListener('keyboardDidHide', () =>
      setKeyboardVisible(false),
    );

    return () => {
      showSubscription.remove();
      hideSubscription.remove();
    };
  }, []);

  const revealCommentInput = (inputHandle: number) => {
    setTimeout(() => {
      scrollRef.current
        ?.getScrollResponder()
        ?.scrollResponderScrollNativeHandleToKeyboard(inputHandle, 100, true);
    }, 100);
  };

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <AppHeader
        onBackPress={navigation.goBack}
        rightAction={
          <Pressable
            accessibilityLabel="게시글 메뉴"
            accessibilityRole="button"
            hitSlop={10}
            onPress={() =>
              Alert.alert('게시글 메뉴', '신고 및 차단 기능은 추후 연결될 예정입니다.')
            }
            style={styles.headerMenuButton}
          >
            <DotsIcon height={24} width={24} />
          </Pressable>
        }
        title="게시글"
      />
      <ScrollView
        contentContainerStyle={{ paddingBottom: 120 + insets.bottom }}
        keyboardDismissMode="on-drag"
        keyboardShouldPersistTaps="handled"
        ref={scrollRef}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.reporter}>
          <Image
            source={require('../../assets/images/found-report/reporter-profile.png')}
            style={styles.profileImage}
          />
          <Text style={styles.reporterName}>시샤</Text>
          <Text style={styles.reportMeta}>1시간 전</Text>
        </View>
        <View>
          <FlatList
            data={report.images}
            horizontal
            keyExtractor={(_item, index) => `${report.id}-image-${index}`}
            onMomentumScrollEnd={onMomentumEnd}
            pagingEnabled
            ref={listRef}
            renderItem={({ item }) => (
              <Image resizeMode="cover" source={item} style={{ height: width, width }} />
            )}
            showsHorizontalScrollIndicator={false}
          />
          {report.images.length > 1 ? (
            <View style={styles.indicators}>
              {report.images.map((_image, index) => (
                <View
                  key={index}
                  style={[styles.indicator, index === page && styles.activeIndicator]}
                />
              ))}
            </View>
          ) : null}
        </View>
        <View style={styles.titleRow}>
          <View style={styles.badge}>
            <Text style={styles.badgeText}>제보</Text>
          </View>
          <Text style={styles.title}>{report.name}</Text>
          <Pressable
            accessibilityLabel="북마크"
            accessibilityRole="button"
            accessibilityState={{ selected: bookmarked }}
            hitSlop={10}
            onPress={() => toggleBookmark(report.id)}
          >
            {bookmarked ? <BookmarkActiveIcon /> : <BookmarkIcon />}
          </Pressable>
        </View>
        <View style={styles.section}>
          <InfoRow
            label="성별"
            value={`${report.gender} (중성화 ${report.neutered ? 'O' : 'X'})`}
          />
          <InfoRow label="나이" value={report.age} />
          <InfoRow label="몸무게" value={report.weight} />
          <InfoRow label="털색" value={report.color} />
        </View>
        <View style={styles.section}>
          <InfoRow label="발견일" value={report.date} />
          <InfoRow label="발견장소" value={report.location} />
          <InfoRow label="연락처" value={getContactDisplayValue(report.contact)} />
        </View>
        <View style={styles.descriptionSection}>
          <Text style={styles.descriptionLabel}>특징 및 상세내용</Text>
          <Text style={styles.description}>{report.description}</Text>
        </View>
        <FoundReportCommentList onEditFocus={revealCommentInput} />
      </ScrollView>
      {!keyboardVisible ? (
        <View style={[styles.buttonWrap, { bottom: Math.max(insets.bottom, 12) }]}>
          <Button
            onPress={() => Alert.alert('채팅', '채팅 화면은 추후 연결될 예정입니다.')}
            style={styles.chatButton}
          >
            채팅 보내기
          </Button>
        </View>
      ) : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  headerMenuButton: {
    alignItems: 'center',
    alignSelf: 'flex-end',
    height: 40,
    justifyContent: 'center',
    transform: [{ translateX: 6 }],
    width: 40,
  },
  reporter: { alignItems: 'center', flexDirection: 'row', height: 62, paddingHorizontal: 20 },
  profileImage: { borderRadius: radius.pill, height: 48, width: 48 },
  reporterName: { ...typography.titleSemiBold18, color: colors.gray600, marginLeft: 16 },
  reportMeta: { ...typography.captionRegular10, color: colors.gray300, marginLeft: 'auto' },
  indicators: {
    bottom: 12,
    flexDirection: 'row',
    gap: 5,
    justifyContent: 'center',
    left: 0,
    position: 'absolute',
    right: 0,
  },
  indicator: { backgroundColor: colors.black, borderRadius: 2, height: 4, width: 4 },
  activeIndicator: { width: 12 },
  titleRow: {
    alignItems: 'center',
    borderBottomColor: colors.gray200,
    borderBottomWidth: 1,
    flexDirection: 'row',
    gap: 6,
    paddingHorizontal: 22,
    paddingVertical: 16,
  },
  badge: {
    backgroundColor: colors.blue,
    borderRadius: radius.pill,
    paddingHorizontal: 8,
    paddingVertical: 3,
  },
  badgeText: { ...typography.bodyRegular14, color: colors.white },
  title: { ...typography.titleSemiBold18, color: colors.black, flex: 1 },
  section: {
    borderBottomColor: colors.gray200,
    borderBottomWidth: 1,
    gap: 10,
    paddingHorizontal: 22,
    paddingVertical: 20,
  },
  infoRow: { flexDirection: 'row' },
  infoLabel: { ...typography.bodyRegular14, color: colors.gray400, width: 110 },
  infoValue: { ...typography.bodyRegular14, color: colors.black, flex: 1 },
  descriptionSection: {
    borderBottomColor: colors.gray200,
    borderBottomWidth: 1,
    paddingHorizontal: 22,
    paddingVertical: 20,
  },
  descriptionLabel: { ...typography.bodyRegular14, color: colors.gray400 },
  description: { ...typography.bodyRegular14, color: colors.black, marginTop: 10 },
  buttonWrap: {
    left: 0,
    paddingHorizontal: 26,
    paddingVertical: 12,
    position: 'absolute',
    right: 0,
  },
  chatButton: {
    boxShadow: '0px 0px 15px rgba(0, 0, 0, 0.25)',
    elevation: 8,
  },
});
