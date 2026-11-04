import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  FlatList,
  Image,
  Keyboard,
  Modal,
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
import PostDeleteIcon from '../../assets/icons/common/post-delete.svg';
import PostEditIcon from '../../assets/icons/common/post-edit.svg';
import PostStatusIcon from '../../assets/icons/common/post-status.svg';
import StatusPawIcon from '../../assets/icons/common/status-paw.svg';
import { AppHeader, Button } from '../../components/common';
import { useAdoptionBookmarkStore } from '../../stores/adoptionBookmarkStore';
import {
  type ReportProgressStatus,
  useReportStatusStore,
} from '../../stores/reportStatusStore';
import { colors, radius, typography } from '../../theme';
import { FoundReportCommentList } from './FoundReportCommentList';
import { getFoundReport } from './data';
import { getLostReport } from '../lost-report/data';

type Props = NativeStackScreenProps<
  RootStackParamList,
  'FoundReportDetail' | 'LostReportDetail'
>;

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
  const isLostReport = route.name === 'LostReportDetail';
  const report = isLostReport
    ? getLostReport(route.params.reportId)
    : getFoundReport(route.params.reportId);
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const [keyboardVisible, setKeyboardVisible] = useState(false);
  const [menuVisible, setMenuVisible] = useState(false);
  const [page, setPage] = useState(0);
  const [pendingStatus, setPendingStatus] = useState<ReportProgressStatus | null>(null);
  const [statusSheetVisible, setStatusSheetVisible] = useState(false);
  const listRef = useRef<FlatList>(null);
  const scrollRef = useRef<ScrollViewInstance>(null);
  const bookmarked = useAdoptionBookmarkStore(state => state.bookmarkedIds.includes(report.id));
  const toggleBookmark = useAdoptionBookmarkStore(state => state.toggleBookmark);
  const storedStatus = useReportStatusStore(state => state.statusById[report.id]);
  const setReportStatus = useReportStatusStore(state => state.setStatus);
  const progressStatus =
    storedStatus ?? (report.status === '완료' || report.status === '종료' ? 'completed' : 'active');
  const isCompleted = progressStatus === 'completed';
  const displayStatus = isLostReport
    ? isCompleted
      ? '종료'
      : '실종'
    : isCompleted
      ? '완료'
      : '제보';

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

  const requestStatusChange = (nextStatus: ReportProgressStatus) => {
    setStatusSheetVisible(false);
    if (nextStatus === progressStatus) {
      return;
    }
    setPendingStatus(nextStatus);
  };

  const confirmStatusChange = () => {
    if (pendingStatus) {
      setReportStatus(report.id, pendingStatus);
    }
    setPendingStatus(null);
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
              report.isMine
                ? setMenuVisible(current => !current)
                : Alert.alert('게시글 메뉴', '신고 및 차단 기능은 추후 연결될 예정입니다.')
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
          <Text style={[styles.reporterName, report.isMine && styles.myReporterName]}>
            {report.isMine ? '내 닉네임' : '시샤'}
          </Text>
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
          <View
            style={[
              styles.badge,
              isLostReport && styles.lostBadge,
              isCompleted && styles.closedBadge,
            ]}
          >
            <Text style={styles.badgeText}>
              {displayStatus}
            </Text>
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
          <InfoRow label={isLostReport ? '실종일' : '발견일'} value={report.date} />
          <InfoRow label={isLostReport ? '실종장소' : '발견장소'} value={report.location} />
          <InfoRow label="연락처" value={getContactDisplayValue(report.contact)} />
        </View>
        <View style={styles.descriptionSection}>
          <Text style={styles.descriptionLabel}>특징 및 상세내용</Text>
          <Text style={styles.description}>{report.description}</Text>
        </View>
        <FoundReportCommentList onEditFocus={revealCommentInput} />
      </ScrollView>
      {!keyboardVisible && !isCompleted ? (
        <View style={[styles.buttonWrap, { bottom: Math.max(insets.bottom, 12) }]}>
          <Button
            onPress={() => Alert.alert('채팅', '채팅 화면은 추후 연결될 예정입니다.')}
            style={styles.chatButton}
          >
            채팅 보내기
          </Button>
        </View>
      ) : null}
      {menuVisible ? (
        <>
          <Pressable
            accessibilityLabel="게시글 메뉴 닫기"
            onPress={() => setMenuVisible(false)}
            style={styles.menuBackdrop}
          />
          <View style={styles.postMenu}>
            <Pressable
              onPress={() => {
                setMenuVisible(false);
                Alert.alert('수정하기', '게시글 수정 화면은 등록·수정 작업에서 연결됩니다.');
              }}
              style={({ pressed }) => [styles.postMenuItem, pressed && styles.pressed]}
            >
              <PostEditIcon />
              <Text style={styles.postMenuText}>수정하기</Text>
            </Pressable>
            <Pressable
              onPress={() => {
                setMenuVisible(false);
                setStatusSheetVisible(true);
              }}
              style={({ pressed }) => [styles.postMenuItem, pressed && styles.pressed]}
            >
              <PostStatusIcon />
              <Text style={styles.postMenuText}>상태 변경하기</Text>
            </Pressable>
            <Pressable
              onPress={() => {
                setMenuVisible(false);
                Alert.alert('삭제하기', '게시글 삭제 기능은 등록·수정 작업에서 연결됩니다.');
              }}
              style={({ pressed }) => [styles.postMenuItem, pressed && styles.pressed]}
            >
              <PostDeleteIcon />
              <Text style={styles.deleteMenuText}>삭제하기</Text>
            </Pressable>
          </View>
        </>
      ) : null}
      <Modal
        animationType="slide"
        onRequestClose={() => setStatusSheetVisible(false)}
        transparent
        visible={statusSheetVisible}
      >
        <Pressable onPress={() => setStatusSheetVisible(false)} style={styles.sheetBackdrop}>
          <Pressable
            accessibilityViewIsModal
            onPress={event => event.stopPropagation()}
            style={[styles.statusSheet, { paddingBottom: Math.max(insets.bottom, 24) }]}
          >
            <Text style={styles.statusSheetTitle}>상태</Text>
            <View style={styles.statusDivider} />
            <StatusOption
              active={progressStatus === 'active'}
              label="진행 중"
              onPress={() => requestStatusChange('active')}
            />
            <StatusOption
              active={progressStatus === 'completed'}
              label="완료"
              onPress={() => requestStatusChange('completed')}
            />
          </Pressable>
        </Pressable>
      </Modal>
      <Modal
        animationType="fade"
        onRequestClose={() => setPendingStatus(null)}
        transparent
        visible={pendingStatus !== null}
      >
        <View style={styles.confirmBackdrop}>
          <View style={styles.confirmCard}>
            <Text style={styles.confirmTitle}>
              {`상태를 ${pendingStatus === 'completed' ? '완료' : '진행 중'}로 변경하시겠습니까?`}
            </Text>
            <Text style={styles.confirmDescription}>
              상태 변경 결과는 목록과 상세 화면에 바로 반영됩니다.
            </Text>
            <View style={styles.confirmActions}>
              <Pressable
                onPress={() => setPendingStatus(null)}
                style={[styles.confirmButton, styles.confirmCancelButton]}
              >
                <Text style={styles.confirmButtonText}>취소하기</Text>
              </Pressable>
              <Pressable
                onPress={confirmStatusChange}
                style={[styles.confirmButton, styles.confirmChangeButton]}
              >
                <Text style={styles.confirmButtonText}>변경하기</Text>
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

function StatusOption({
  active,
  label,
  onPress,
}: {
  active: boolean;
  label: string;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityState={{ checked: active }}
      onPress={onPress}
      style={({ pressed }) => [styles.statusOption, pressed && styles.pressed]}
    >
      <Text style={[styles.statusOptionText, active && styles.statusOptionActiveText]}>{label}</Text>
      {active ? <StatusPawIcon /> : null}
    </Pressable>
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
  myReporterName: { color: colors.green400 },
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
  lostBadge: { backgroundColor: '#FA705B' },
  closedBadge: { backgroundColor: colors.gray400 },
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
  menuBackdrop: {
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
    zIndex: 8,
  },
  postMenu: {
    backgroundColor: colors.white,
    borderColor: '#D9DBE3',
    borderRadius: 10,
    borderWidth: 1,
    elevation: 7,
    overflow: 'hidden',
    position: 'absolute',
    right: 31,
    top: 48,
    width: 160,
    zIndex: 9,
  },
  postMenuItem: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 10,
    height: 48,
    paddingHorizontal: 16,
  },
  postMenuText: { ...typography.bodyRegular16, color: '#262B33' },
  deleteMenuText: { ...typography.bodyRegular16, color: '#FA705B' },
  sheetBackdrop: { flex: 1, justifyContent: 'flex-end' },
  statusSheet: {
    backgroundColor: colors.white,
    borderTopLeftRadius: 10,
    borderTopRightRadius: 10,
    elevation: 12,
    minHeight: 185,
    paddingBottom: 24,
    paddingHorizontal: 22,
    shadowColor: colors.black,
    shadowOffset: { height: 0, width: 0 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
  },
  statusSheetTitle: {
    ...typography.bodySemiBold16,
    color: colors.black,
    paddingBottom: 16,
    paddingTop: 16,
    textAlign: 'center',
  },
  statusDivider: { backgroundColor: colors.gray200, height: 1 },
  statusOption: { alignItems: 'center', flexDirection: 'row', gap: 10, height: 42, paddingLeft: 8 },
  statusOptionText: { ...typography.bodyRegular16, color: colors.gray700 },
  statusOptionActiveText: { color: colors.green400 },
  confirmBackdrop: {
    alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,0.3)',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  confirmCard: { backgroundColor: colors.white, borderRadius: 18, padding: 24, width: '100%' },
  confirmTitle: { ...typography.titleSemiBold18, color: '#222222' },
  confirmDescription: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 16 },
  confirmActions: { flexDirection: 'row', gap: 14, marginTop: 16 },
  confirmButton: {
    alignItems: 'center',
    borderRadius: 10,
    flex: 1,
    height: 40,
    justifyContent: 'center',
  },
  confirmCancelButton: { backgroundColor: colors.gray200 },
  confirmChangeButton: { backgroundColor: colors.green400 },
  confirmButtonText: { ...typography.bodySemiBold16, color: colors.white },
  pressed: { opacity: 0.65 },
});
