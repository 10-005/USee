import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect, useRef, useState, type ComponentRef } from 'react';
import {
  ActivityIndicator,
  Animated,
  Alert,
  FlatList,
  Image,
  Keyboard,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  type TextInput,
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
import ReportIcon from '../../assets/icons/common/report.svg';
import ReportCloseIcon from '../../assets/icons/common/report-close.svg';
import StatusPawIcon from '../../assets/icons/common/status-paw.svg';
import { AppHeader, Button, DetailScreenSkeleton } from '../../components/common';
import {
  ContentReportModal,
  type ContentReportTarget,
} from '../../components/report/ContentReportModal';
import { useChatStore } from '../../stores/chatStore';
import { useAdoptionBookmarkStore } from '../../stores/adoptionBookmarkStore';
import { bookmarkKey } from '../../services/bookmarks';
import { useFoundReportDraftStore } from '../../stores/foundReportDraftStore';
import { useFoundReportEditStore } from '../../stores/foundReportEditStore';
import { useFoundReportStore } from '../../stores/foundReportStore';
import { useLostReportDraftStore } from '../../stores/lostReportDraftStore';
import { useLostReportEditStore } from '../../stores/lostReportEditStore';
import { useMissingReportStore } from '../../stores/missingReportStore';
import { type ReportProgressStatus, useReportStatusStore } from '../../stores/reportStatusStore';
import { colors, radius, typography } from '../../theme';
import ChatIcon from '../../assets/icons/common/chat-white.svg';
import type { Asset } from 'react-native-image-picker';
import { CommentComposer, FoundReportCommentList } from './FoundReportCommentList';
import { useInitialCommentScroll } from './useInitialCommentScroll';
import { getFoundReport } from './data';
import { splitReportDescription } from './reportDescription';
import { getLostReport } from '../lost-report/data';

type Props = NativeStackScreenProps<RootStackParamList, 'FoundReportDetail' | 'LostReportDetail'>;

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.infoRow}>
      <Text style={styles.infoLabel}>{label}</Text>
      <Text style={styles.infoValue}>{value}</Text>
    </View>
  );
}

export function getContactDisplayValue(contact: string | null | undefined, isPublic: boolean | undefined) {
  return isPublic && contact ? contact : '비공개';
}

import { getPublicProfile } from '../../services/users/userApi';

export function FoundReportDetailScreen({ navigation, route }: Props) {
  const isLostReport = route.name === 'LostReportDetail';
  const apiFoundReport = useFoundReportStore(state => state.reportById[route.params.reportId]);
  const apiFoundDetail = useFoundReportStore(state => state.detailsById[route.params.reportId]);
  const fetchFoundReport = useFoundReportStore(state => state.fetchReport);
  const deleteApiFoundReport = useFoundReportStore(state => state.deleteReport);
  const setApiFoundReportStatus = useFoundReportStore(state => state.setStatus);
  const foundReportDetailLoading = useFoundReportStore(state => state.isDetailLoading);
  const foundReportDetailError = useFoundReportStore(state => state.error);
  const apiLostReport = useMissingReportStore(state => state.reportById[route.params.reportId]);
  const apiLostDetail = useMissingReportStore(state => state.detailsById[route.params.reportId]);
  const fetchLostReport = useMissingReportStore(state => state.fetchReport);
  const deleteApiLostReport = useMissingReportStore(state => state.deleteReport);
  const setApiLostReportStatus = useMissingReportStore(state => state.setStatus);
  const lostReportDetailLoading = useMissingReportStore(state => state.isDetailLoading);
  const lostReportDetailError = useMissingReportStore(state => state.error);
  const highlightCommentId =
    'highlightCommentId' in route.params ? route.params.highlightCommentId : undefined;
  const baseReport = isLostReport
    ? apiLostReport ?? getLostReport(route.params.reportId)
    : apiFoundReport ?? getFoundReport(route.params.reportId);
  const foundReportEdit = useFoundReportEditStore(state => state.editsById[route.params.reportId]);
  const lostReportEdit = useLostReportEditStore(state => state.editsById[route.params.reportId]);
  const report = {
    ...baseReport,
    ...(isLostReport ? lostReportEdit : foundReportEdit),
  };
  const updateFoundReportDraft = useFoundReportDraftStore(state => state.updateDraft);
  const selectManualLostReport = useLostReportDraftStore(state => state.selectManual);
  const updateLostReportAnimal = useLostReportDraftStore(state => state.updateAnimal);
  const updateLostReportDetails = useLostReportDraftStore(state => state.updateDetails);
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const [commentsVisible, setCommentsVisible] = useState(false);
  const sectionY = useRef(Infinity);
  const viewport = useRef({ y: 0, height: 0 });
  const transition = useRef(new Animated.Value(0)).current;
  const submitComment = useRef<((content: string, image?: Asset) => Promise<boolean>) | null>(null);
  const composerInput = useRef<ComponentRef<typeof TextInput>>(null);
  const replyY = useRef<number | null>(null);
  const draftEmpty = useRef(true);
  const resetReply = useRef<(() => void) | null>(null);
  const clearEmptyReply = () => {
    if (!draftEmpty.current) {
      return;
    }
    resetReply.current?.();
    setReplyName(undefined);
    replyY.current = null;
  };
  const [replyName, setReplyName] = useState<string>();
  const [sendingComment, setSendingComment] = useState(false);
  const [floatingPhotoMenu, setFloatingPhotoMenu] = useState(false);
  useEffect(() => {
    const animation = Animated.timing(transition, { toValue: commentsVisible ? 1 : 0, duration: 220, useNativeDriver: true });
    animation.start();
    return () => animation.stop();
  }, [commentsVisible, transition]);
  const updateCommentVisibility = () => {
    setCommentsVisible(viewport.current.y + viewport.current.height > sectionY.current);
  };
  const screenRef = useRef<ComponentRef<typeof View>>(null);
  const keyboardTop = useRef<number | null>(null);
  const [keyboardOverlap, setKeyboardOverlap] = useState(0);
  const updateKeyboardOverlap = () => {
    screenRef.current?.measureInWindow((_x, y, _width, height) => {
      setKeyboardOverlap(keyboardTop.current === null ? 0 : Math.max(0, y + height - keyboardTop.current));
    });
  };
  const [keyboardVisible, setKeyboardVisible] = useState(false);
  const [keyboardHeight, setKeyboardHeight] = useState(0);
  const [commentPhotoMenuVisible, setCommentPhotoMenuVisible] = useState(false);
  const [menuVisible, setMenuVisible] = useState(false);
  const [deleteVisible, setDeleteVisible] = useState(false);
  const [viewerMenuVisible, setViewerMenuVisible] = useState(false);
  const [reportTarget, setReportTarget] = useState<ContentReportTarget | null>(null);
  const [page, setPage] = useState(0);
  const [pendingStatus, setPendingStatus] = useState<ReportProgressStatus | null>(null);
  const [selectedStatus, setSelectedStatus] = useState<ReportProgressStatus>('active');
  const [statusSheetVisible, setStatusSheetVisible] = useState(false);
  const listRef = useRef<FlatList>(null);
  const scrollRef = useRef<ScrollViewInstance>(null);
  const commentScroll = useInitialCommentScroll(
    scrollRef,
    route.params.reportId,
    highlightCommentId,
  );
  const focusedCommentInputRef = useRef<number | null>(null);
  const bookmarkType = isLostReport ? 'MISSING' : 'FOUND';
  const bookmarked = useAdoptionBookmarkStore(state =>
    state.bookmarkKeys.includes(bookmarkKey(bookmarkType, report.id)),
  );
  const loadBookmarks = useAdoptionBookmarkStore(state => state.loadBookmarks);
  const bookmarkPending = useAdoptionBookmarkStore(state =>
    state.pendingKeys.includes(bookmarkKey(bookmarkType, report.id)),
  );
  const toggleBookmark = useAdoptionBookmarkStore(state => state.toggleBookmark);
  useEffect(() => {
    loadBookmarks(bookmarkType).catch(() => undefined);
  }, [bookmarkType, loadBookmarks]);
  const storedStatus = useReportStatusStore(state => state.statusById[report.id]);

  useEffect(() => {
    if (isLostReport) {
      fetchLostReport(route.params.reportId).catch(() => undefined);
    } else {
      fetchFoundReport(route.params.reportId).catch(() => undefined);
    }
  }, [fetchFoundReport, fetchLostReport, isLostReport, route.params.reportId]);
  const progressStatus =
    storedStatus ?? (report.status === '완료' || report.status === '종료' ? 'completed' : 'active');
  const isCompleted = progressStatus === 'completed';
  const displayStatus = isLostReport
    ? isCompleted
      ? '종료'
      : '실종'
    : isCompleted
    ? '종료'
    : '제보';

  const foundFields = isLostReport ? null : splitReportDescription(report.description);
  const reporterName =
    (isLostReport ? apiLostDetail?.user?.displayName : apiFoundDetail?.reporter?.displayName) ??
    (report.isMine ? '내 닉네임' : '사용자');
  const reporterId = String(
    (isLostReport ? apiLostDetail?.user?.userSeq : apiFoundDetail?.reporter?.userSeq) ?? '',
  );
  const userSeq = Number(reporterId);
  const reporterImage = isLostReport ? null : apiFoundDetail?.reporter?.profileImage;
  const detailPhoto = typeof reporterImage === 'string' ? reporterImage : reporterImage?.imageUrl;
  const [profile, setProfile] = useState<{ id: number; uri?: string }>();
  useEffect(() => {
    if (!userSeq || detailPhoto) {return;}
    let active = true;
    getPublicProfile(userSeq)
      .then(result => {
        if (active) {setProfile({ id: userSeq, uri: result.profileImage?.imageUrl });}
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [userSeq, detailPhoto]);
  const reporterPhoto = detailPhoto || (profile?.id === userSeq ? profile?.uri : undefined);
  const openChat = async (name: string, authorId: string) => {
    try {
      const params = await useChatStore
        .getState()
        .openPostRoom(isLostReport ? 'lost' : 'found', report.id, name, authorId);
      if (navigation.isFocused()) {
        navigation.navigate('ChatRoom', params);
      }
    } catch {
      Alert.alert('채팅', '채팅방을 열 수 없어요. 다시 시도해 주세요.');
    }
  };

  const onMomentumEnd = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    setPage(Math.round(event.nativeEvent.contentOffset.x / width));
  };

  useEffect(() => {
    const showSubscription = Keyboard.addListener('keyboardDidShow', event => {
      Keyboard.scheduleLayoutAnimation(event);
      keyboardTop.current = event.endCoordinates.screenY;
      updateKeyboardOverlap();
      setKeyboardVisible(true);
      setKeyboardHeight(event.endCoordinates.height);
      requestAnimationFrame(() => {
        if (replyY.current !== null) {
          scrollRef.current?.scrollTo({ y: Math.max(0, replyY.current - 24), animated: true });
        } else {
          scrollRef.current?.scrollTo({ y: viewport.current.y + event.endCoordinates.height, animated: true });
        }
      });
      const inputHandle = focusedCommentInputRef.current;
      if (inputHandle !== null) {
        setTimeout(() => {
          scrollRef.current
            ?.getScrollResponder()
            ?.scrollResponderScrollNativeHandleToKeyboard(inputHandle, 120, true);
        }, 50);
      }
    });
    const hideSubscription = Keyboard.addListener('keyboardDidHide', () => {
      keyboardTop.current = null;
      setKeyboardOverlap(0);
      setKeyboardVisible(false);
      setKeyboardHeight(0);
      focusedCommentInputRef.current = null;
    });

    return () => {
      showSubscription.remove();
      hideSubscription.remove();
    };
  }, []);

  const revealCommentInput = (inputHandle: number) => {
    focusedCommentInputRef.current = inputHandle;
    setTimeout(() => {
      scrollRef.current
        ?.getScrollResponder()
        ?.scrollResponderScrollNativeHandleToKeyboard(inputHandle, 120, true);
    }, 100);
  };

  const requestStatusChange = () => {
    setStatusSheetVisible(false);
    if (selectedStatus === progressStatus) {
      return;
    }
    setPendingStatus(selectedStatus);
  };

  const confirmStatusChange = async () => {
    if (pendingStatus) {
      try {
        if (isLostReport) {
          await setApiLostReportStatus(report.id, pendingStatus);
        } else {
          await setApiFoundReportStatus(report.id, pendingStatus);
        }
      } catch {
        Alert.alert('상태 변경 실패', '잠시 후 다시 시도해 주세요.');
      }
    }
    setPendingStatus(null);
  };

  if (isLostReport && !apiLostReport && (lostReportDetailLoading || !lostReportDetailError)) {
    return (
      <SafeAreaView edges={['top']} style={styles.safeArea}>
        <AppHeader onBackPress={navigation.goBack} title="실종 신고 상세" />
        <DetailScreenSkeleton accessibilityLabel="실종 신고 상세 불러오는 중" />
      </SafeAreaView>
    );
  }

  if (!isLostReport && !apiFoundReport && (foundReportDetailLoading || !foundReportDetailError)) {
    return (
      <SafeAreaView edges={['top']} style={styles.safeArea}>
        <AppHeader onBackPress={navigation.goBack} title="발견 신고 상세" />
        <DetailScreenSkeleton accessibilityLabel="발견 신고 상세 불러오는 중" />
      </SafeAreaView>
    );
  }

  if (!isLostReport && !apiFoundReport && foundReportDetailError) {
    return (
      <SafeAreaView edges={['top']} style={styles.safeArea}>
        <AppHeader onBackPress={navigation.goBack} title="발견 신고 상세" />
        <View style={styles.loadError}>
          <Text style={styles.loadErrorText}>{foundReportDetailError}</Text>
          <Button onPress={() => fetchFoundReport(route.params.reportId)}>다시 시도</Button>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView onTouchStart={clearEmptyReply} ref={screenRef} onLayout={updateKeyboardOverlap} edges={['top']} style={styles.safeArea}>
      <AppHeader
        onBackPress={navigation.goBack}
        rightAction={
          <Pressable
            accessibilityLabel="게시글 메뉴"
            accessibilityRole="button"
            hitSlop={10}
            onPress={() => {
              if (report.isMine) {
                setMenuVisible(current => !current);
                setViewerMenuVisible(false);
              } else {
                setViewerMenuVisible(current => !current);
                setMenuVisible(false);
              }
            }}
            style={styles.headerMenuButton}
          >
            <DotsIcon height={24} width={24} />
          </Pressable>
        }
        title="게시글"
      />
      <View style={styles.contentBody}>
        <ScrollView
          key={`${route.params.reportId}:${highlightCommentId ?? ''}`}
          style={commentScroll.waiting ? styles.hiddenContent : undefined}
          pointerEvents={commentScroll.waiting ? 'none' : 'auto'}
          accessibilityElementsHidden={commentScroll.waiting}
          importantForAccessibility={commentScroll.waiting ? 'no-hide-descendants' : 'auto'}
          onLayout={event => {
            commentScroll.onLayout(event);
            viewport.current.height = event.nativeEvent.layout.height;
            updateCommentVisibility();
          }}
          onContentSizeChange={commentScroll.onContentSizeChange}
          onScroll={event => {
            commentScroll.onScroll(event);
            viewport.current.y = event.nativeEvent.contentOffset.y;
            updateCommentVisibility();
          }}
          onTouchStart={clearEmptyReply}
          onScrollBeginDrag={clearEmptyReply}
          scrollEventThrottle={16}
          contentContainerStyle={{ paddingBottom: 120 + insets.bottom + keyboardHeight }}
          decelerationRate="fast"
          keyboardDismissMode="on-drag"
          keyboardShouldPersistTaps="handled"
          ref={scrollRef}
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.reporter}>
            <Image
              source={
                reporterPhoto
                  ? { uri: reporterPhoto }
                  : require('../../assets/images/mypage/profile-default.png')
              }
              style={styles.profileImage}
            />
            <Text style={[styles.reporterName, report.isMine && styles.myReporterName]}>
              {reporterName}
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
              <Text style={styles.badgeText}>{displayStatus}</Text>
            </View>
            <Text style={styles.title}>{report.name}</Text>
            <Pressable
              accessibilityLabel="북마크"
              accessibilityRole="button"
              accessibilityState={{
                busy: bookmarkPending,
                disabled: bookmarkPending,
                selected: bookmarked,
              }}
              disabled={bookmarkPending}
              hitSlop={10}
              onPress={() =>
                toggleBookmark(bookmarkType, report.id).catch(() =>
                  Alert.alert('북마크 변경 실패', '잠시 후 다시 시도해 주세요.'),
                )
              }
            >
              {bookmarkPending ? (
                <ActivityIndicator color={colors.green400} size="small" />
              ) : bookmarked ? (
                <BookmarkActiveIcon />
              ) : (
                <BookmarkIcon />
              )}
            </Pressable>
          </View>
          <View style={styles.section}>
            <InfoRow
              label="성별"
              value={`${report.gender} (중성화 ${
                report.neutered === null ? '모름' : report.neutered ? 'O' : 'X'
              })`}
            />
            <InfoRow label="나이" value={report.age} />
            <InfoRow label="몸무게" value={report.weight} />
            <InfoRow label="털색" value={report.color} />
            <InfoRow label="특징" value={(foundFields?.description ?? report.description).trim() || '-'} />
          </View>
          <View style={styles.section}>
            <InfoRow label={isLostReport ? '실종일' : '발견일'} value={report.date} />
            <InfoRow label={isLostReport ? '실종장소' : '발견장소'} value={report.location} />
            {foundFields && (
              <InfoRow label="발견 상황" value={foundFields.foundSituation.trim() || '-'} />
            )}
            <InfoRow
              label="연락처"
              value={
                isLostReport
                  ? getContactDisplayValue(apiLostDetail?.contactPhone, apiLostDetail?.isPhonePublic)
                  : getContactDisplayValue(apiFoundDetail?.reporterPhone, apiFoundDetail?.isPhonePublic)
              }
            />
          </View>
          <FoundReportCommentList
            onReplySelect={(name, y) => {
              setReplyName(name);
              replyY.current = y;
              scrollRef.current?.scrollTo({ y: Math.max(0, y - 24), animated: true });
              composerInput.current?.focus();
            }}
            resetReplyRef={resetReply}
            floatingComposer
            submitRef={submitComment}
            onSectionLayout={y => {
              sectionY.current = y;
              updateCommentVisibility();
            }}
            onTargetLayout={commentScroll.onTargetLayout}
            onTargetUnavailable={commentScroll.onTargetUnavailable}
            onChat={openChat}
            onEditFocus={revealCommentInput}
            onPhotoMenuVisibilityChange={setCommentPhotoMenuVisible}
            reportId={report.id}
            targetType={isLostReport ? 'MISSING' : 'FOUND'}
            targetCommentId={highlightCommentId}
          />
        </ScrollView>
        {commentScroll.waiting ? (
          <View style={StyleSheet.absoluteFill}>
            <DetailScreenSkeleton accessibilityLabel="알림 댓글 위치 불러오는 중" />
          </View>
        ) : null}
      </View>
      {!commentScroll.waiting && !commentPhotoMenuVisible ? (
        <View onTouchStart={event => event.stopPropagation()} style={[styles.buttonWrap, styles.floatingRow, keyboardVisible ? [styles.keyboardDock, { bottom: keyboardOverlap }] : { bottom: Math.max(insets.bottom, 12) }]}>
          <Animated.View pointerEvents={commentsVisible ? 'auto' : 'none'} style={{
            width: width - 52,
            opacity: transition,
            transform: [
              { translateY: transition.interpolate({ inputRange: [0, 1], outputRange: [16, 0] }) },
              { scale: transition.interpolate({ inputRange: [0, 1], outputRange: [0.96, 1] }) },
            ],
          }}>
            <CommentComposer
              floating
              inputRef={composerInput}
              replyName={replyName}
              onDraftEmptyChange={empty => { draftEmpty.current = empty; }}
              isSubmitting={sendingComment}
              onDismissMenus={() => setFloatingPhotoMenu(false)}
              onPhotoMenuChange={setFloatingPhotoMenu}
              onStartWriting={() => {}}
              photoMenuOpen={floatingPhotoMenu}
              onSubmit={async (content, image) => {
                setSendingComment(true);
                try {
                  const sent = await submitComment.current?.(content, image) ?? false;
                  if (sent) { setReplyName(undefined); replyY.current = null; }
                  return sent;
                }
                finally { setSendingComment(false); }
              }}
            />
          </Animated.View>
          {!isCompleted && !keyboardVisible ? (
            <>
              <Animated.View pointerEvents={commentsVisible ? 'none' : 'auto'} style={[styles.fullChat, {
                opacity: transition.interpolate({ inputRange: [0, 0.5, 1], outputRange: [1, 0, 0] }),
                transform: [{ scaleX: transition.interpolate({ inputRange: [0, 1], outputRange: [1, 48 / (width - 52)] }) }],
              }]}>
                <Pressable accessibilityLabel="채팅 보내기" onPress={() => openChat(reporterName, reporterId)} style={[styles.chatButton, styles.floatingChat]}>
                  <Text style={{ color: colors.white }}>채팅 보내기</Text>
                </Pressable>
              </Animated.View>
              <Animated.View pointerEvents={commentsVisible ? 'auto' : 'none'} style={[styles.raisedChat, {
                opacity: transition,
                transform: [{ translateY: transition.interpolate({ inputRange: [0, 1], outputRange: [68, 0] }) }],
              }]}>
                <Pressable accessibilityLabel="채팅 보내기" onPress={() => openChat(reporterName, reporterId)} style={[styles.chatButton, styles.floatingChat]}>
                  <ChatIcon width={24} height={24} color={colors.white} />
                </Pressable>
              </Animated.View>
            </>
          ) : null}
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
            {!isCompleted && (
              <Pressable
                onPress={() => {
                  setMenuVisible(false);
                  if (isLostReport) {
                    selectManualLostReport();
                    updateLostReportAnimal({
                      age: Number.isNaN(Number.parseFloat(report.age))
                        ? null
                        : Number.parseFloat(report.age),
                      breed: report.name,
                      color: report.color,
                      combinationSeq: apiLostDetail?.classification?.combinationSeq ?? null,
                      description: report.description,
                      gender: report.gender,
                      images: [...report.images],
                      imageSeqs: apiLostDetail?.images.map(image => image.imageSeq) ?? [],
                      microchipNumber: apiLostDetail?.microchipNumber ?? '',
                      name: report.name,
                      neutered: report.neutered,
                      sourcePetId: null,
                      species: report.species === '개' ? '강아지' : report.species,
                      weight: Number.isNaN(Number.parseFloat(report.weight))
                        ? null
                        : Number.parseFloat(report.weight),
                    });
                    updateLostReportDetails({
                      contact: apiLostDetail?.contactPhone ?? report.contact ?? '',
                      detailAddress: '',
                      exposeContact: apiLostDetail?.isPhonePublic ?? false,
                      latitude: apiLostDetail?.latitude ?? null,
                      location: report.location,
                      longitude: apiLostDetail?.longitude ?? null,
                      lostAt: apiLostDetail?.occurredDttm ?? '',
                    });
                    navigation.navigate('PetRegistration', {
                      flow: 'lost-report',
                      reportId: report.id,
                    });
                    return;
                  }
                  updateFoundReportDraft({
                    ...splitReportDescription(report.description),
                    age: apiFoundDetail?.age?.trim() === '모름' ? '' : apiFoundDetail?.age ?? '',
                    breed: report.name,
                    color: report.color,
                    combinationSeq: apiFoundDetail?.classification?.combinationSeq ?? null,
                    contact: apiFoundDetail?.reporterPhone ?? '',
                    exposeContact: apiFoundDetail?.isPhonePublic ?? false,
                    foundAt: apiFoundDetail?.occurredDttm ?? '',
                    gender:
                      report.gender === '수컷'
                        ? 'male'
                        : report.gender === '암컷'
                        ? 'female'
                        : 'unknown',
                    images: report.images.map(image => ({
                      type: 'image/jpeg',
                      uri: Image.resolveAssetSource(image)?.uri ?? '',
                    })),
                    imageSeqs: apiFoundDetail?.images.map(image => image.imageSeq) ?? [],
                    latitude: apiFoundDetail?.latitude ?? null,
                    location: report.location,
                    longitude: apiFoundDetail?.longitude ?? null,
                    microchipNumber: apiFoundDetail?.microchipNumber ?? '',
                    microchipStatus: apiFoundDetail?.microchipNumber?.trim() ? 'done' : 'not-done',
                    neutered:
                      report.neutered === null ? 'unknown' : report.neutered ? 'done' : 'not-done',
                    species: report.species,
                    terrain: apiFoundDetail?.landmarkDescription ?? '',
                    weight: apiFoundDetail?.weight?.trim() === '모름' ? '' : apiFoundDetail?.weight ?? '',
                  });
                  navigation.navigate('PetRegistration', {
                    flow: 'found-report',
                    reportId: report.id,
                  });
                }}
                style={({ pressed }) => [styles.postMenuItem, pressed && styles.pressed]}
              >
                <PostEditIcon />
                <Text style={styles.postMenuText}>수정하기</Text>
              </Pressable>
            )}
            <Pressable
              onPress={() => {
                setMenuVisible(false);
                setSelectedStatus(progressStatus);
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
                setDeleteVisible(true);
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
        animationType="fade"
        onRequestClose={() => setDeleteVisible(false)}
        transparent
        visible={deleteVisible}
      >
        <View style={styles.deleteModalBackdrop}>
          <View style={styles.deleteModal}>
            <Text style={styles.deleteModalTitle}>
              {isLostReport ? '실종 신고를 삭제하시겠습니까?' : '발견 제보를 삭제하시겠습니까?'}
            </Text>
            <Text style={styles.deleteModalDescription}>
              삭제 후에는 해당 정보를 복구할 수 없습니다.
            </Text>
            <View style={styles.deleteModalActions}>
              <Pressable
                onPress={() => setDeleteVisible(false)}
                style={[styles.deleteModalButton, styles.deleteCancelButton]}
              >
                <Text style={styles.deleteModalButtonText}>취소</Text>
              </Pressable>
              <Pressable
                accessibilityLabel={`${isLostReport ? '실종 신고' : '발견 제보'} 삭제 확인`}
                onPress={async () => {
                  if (isLostReport) {
                    try {
                      await deleteApiLostReport(report.id);
                    } catch {
                      Alert.alert('실종 신고 삭제 실패', '잠시 후 다시 시도해 주세요.');
                      return;
                    }
                  } else {
                    try {
                      await deleteApiFoundReport(report.id);
                    } catch {
                      Alert.alert('발견 신고 삭제 실패', '잠시 후 다시 시도해 주세요.');
                      return;
                    }
                  }
                  setDeleteVisible(false);
                  navigation.goBack();
                }}
                style={[styles.deleteModalButton, styles.deleteConfirmButton]}
              >
                <Text style={styles.deleteModalButtonText}>삭제하기</Text>
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>
      {viewerMenuVisible ? (
        <>
          <Pressable
            accessibilityLabel="게시글 메뉴 닫기"
            onPress={() => setViewerMenuVisible(false)}
            style={styles.menuBackdrop}
          />
          <View style={styles.viewerPostMenu}>
            <Pressable
              onPress={() => {
                setViewerMenuVisible(false);
                setReportTarget({
                  id: `post:${bookmarkType}:${report.id}`,
                  targetSeq: Number(report.id),
                  targetType: bookmarkType,
                  type: 'post',
                });
              }}
              style={({ pressed }) => [styles.postMenuItem, pressed && styles.pressed]}
            >
              <ReportIcon height={22} width={22} />
              <Text style={styles.reportMenuText}>신고하기</Text>
            </Pressable>
          </View>
        </>
      ) : null}
      <ContentReportModal onClose={() => setReportTarget(null)} target={reportTarget} />
      <Modal
        animationType="fade"
        onRequestClose={() => setStatusSheetVisible(false)}
        transparent
        visible={statusSheetVisible}
      >
        <Pressable onPress={() => setStatusSheetVisible(false)} style={styles.statusModalBackdrop}>
          <Pressable
            accessibilityViewIsModal
            onPress={event => event.stopPropagation()}
            style={styles.statusModal}
          >
            <View style={styles.statusModalHeader}>
              <Text style={styles.statusModalTitle}>상태 변경</Text>
              <Pressable
                accessibilityLabel="상태 변경 창 닫기"
                hitSlop={10}
                onPress={() => setStatusSheetVisible(false)}
              >
                <ReportCloseIcon height={16} width={16} />
              </Pressable>
            </View>
            <Text style={styles.statusModalGuide}>변경할 상태를 선택해주세요</Text>
            <StatusOption
              active={selectedStatus === 'active'}
              label={isLostReport ? '실종' : '제보'}
              onPress={() => setSelectedStatus('active')}
            />
            <StatusOption
              active={selectedStatus === 'completed'}
              label="종료"
              onPress={() => setSelectedStatus('completed')}
            />
            <Pressable
              disabled={selectedStatus === progressStatus}
              onPress={requestStatusChange}
              style={[
                styles.statusChangeButton,
                selectedStatus === progressStatus && styles.statusChangeButtonDisabled,
              ]}
            >
              <Text style={styles.statusChangeButtonText}>변경하기</Text>
            </Pressable>
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
              {`상태를 ${
                pendingStatus === 'completed' ? '종료' : isLostReport ? '실종' : '제보'
              }로 변경하시겠습니까?`}
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
      style={({ pressed }) => [
        styles.statusOption,
        active && styles.statusOptionActive,
        pressed && styles.pressed,
      ]}
    >
      <Text style={[styles.statusOptionText, active && styles.statusOptionActiveText]}>
        {label}
      </Text>
      {active ? <StatusPawIcon /> : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  contentBody: { flex: 1 },
  hiddenContent: { opacity: 0 },
  loadError: {
    alignItems: 'center',
    flex: 1,
    gap: 16,
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  loadErrorText: {
    ...typography.bodyRegular14,
    color: colors.gray600,
    textAlign: 'center',
  },
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
  profileImage: { borderRadius: radius.pill, height: 40, width: 40 },
  reporterName: { ...typography.bodySemiBold16, color: colors.gray600, marginLeft: 12 },
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
  fullChat: { position: 'absolute', left: 26, right: 26, bottom: 12 },
  raisedChat: { width: 48, position: 'absolute', right: 26, bottom: '100%', marginBottom: 20 },
  keyboardDock: { bottom: 0, paddingBottom: 0 },
  floatingRow: { alignItems: 'flex-end' },
  floatingChat: { height: 48, borderRadius: 24, backgroundColor: colors.green400, alignItems: 'center', justifyContent: 'center' },
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
    right: 12,
    top: 80,
    width: 160,
    zIndex: 9,
  },
  viewerPostMenu: {
    backgroundColor: colors.white,
    borderColor: '#D9DBE3',
    borderRadius: 10,
    borderWidth: 1,
    elevation: 7,
    overflow: 'hidden',
    position: 'absolute',
    right: 12,
    top: 80,
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
  reportMenuText: { ...typography.bodyRegular16, color: '#FA705B' },
  deleteMenuText: { ...typography.bodyRegular16, color: '#FA705B' },
  deleteModalBackdrop: {
    alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,0.3)',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  deleteModal: { backgroundColor: colors.white, borderRadius: 18, padding: 24, width: '100%' },
  deleteModalTitle: { ...typography.titleBold18, color: colors.gray800 },
  deleteModalDescription: {
    ...typography.bodyRegular14,
    color: colors.gray500,
    marginTop: 12,
  },
  deleteModalActions: { flexDirection: 'row', gap: 14, marginTop: 16 },
  deleteModalButton: {
    alignItems: 'center',
    borderRadius: 10,
    flex: 1,
    height: 40,
    justifyContent: 'center',
  },
  deleteCancelButton: { backgroundColor: colors.gray200 },
  deleteConfirmButton: { backgroundColor: '#FA705B' },
  deleteModalButtonText: { ...typography.bodySemiBold16, color: colors.white },
  statusModalBackdrop: {
    alignItems: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.3)',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  statusModal: {
    backgroundColor: colors.white,
    borderRadius: 18,
    elevation: 12,
    padding: 24,
    shadowColor: colors.black,
    shadowOffset: { height: 0, width: 0 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    width: '100%',
  },
  statusModalHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  statusModalTitle: {
    ...typography.titleSemiBold18,
    color: colors.gray800,
    fontSize: 22,
    lineHeight: 28,
  },
  statusModalGuide: {
    ...typography.bodyRegular14,
    color: colors.gray500,
    marginBottom: 14,
    marginTop: 16,
  },
  statusOption: {
    alignItems: 'center',
    backgroundColor: colors.gray10,
    borderRadius: 8,
    flexDirection: 'row',
    height: 44,
    justifyContent: 'space-between',
    marginBottom: 8,
    paddingHorizontal: 12,
  },
  statusOptionText: { ...typography.bodyRegular14, color: colors.gray600 },
  statusOptionActive: { backgroundColor: 'rgba(0, 157, 88, 0.1)' },
  statusOptionActiveText: { color: colors.green400 },
  statusChangeButton: {
    alignItems: 'center',
    backgroundColor: colors.green400,
    borderRadius: 8,
    height: 48,
    justifyContent: 'center',
    marginTop: 8,
  },
  statusChangeButtonDisabled: { backgroundColor: colors.gray200 },
  statusChangeButtonText: { ...typography.bodySemiBold16, color: colors.white },
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
