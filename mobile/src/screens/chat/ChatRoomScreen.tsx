import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  AppState,
  BackHandler,
  FlatList,
  Image,
  Keyboard,
  KeyboardAvoidingView,
  PermissionsAndroid,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  useWindowDimensions,
  View,
} from 'react-native';
import { launchCamera, launchImageLibrary } from 'react-native-image-picker';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useIsFocused } from '@react-navigation/native';
import { chatSocket, getChatRoom } from '../../services/chat';
import SendIcon from '../../assets/icons/common/chat-send.svg';
import type { RootStackParamList } from '../../app/navigation/types';
import { AppHeader } from '../../components/common';
import { type PostRef, samePost, useChatStore } from '../../stores/chatStore';
import { colors, typography } from '../../theme';
import { getShelterAnimal } from '../../services/adoption';
import { getFoundReport } from '../../services/foundReports';
import { getMissingReport } from '../../services/missingReports';
import { trackVisibleChatRoom } from '../../services/notifications/visibleChatRoom';
import { ChatActions, type ChatAction } from './ChatActions';
import {
  ContentReportModal,
  type ContentReportTarget,
} from '../../components/report/ContentReportModal';
import DotsIcon from '../../assets/icons/common/dots-vertical.svg';
import PlusIcon from '../../assets/icons/common/plus.svg';
import BadgeIcon from '../../assets/icons/common/badge.svg';

import CameraIcon from '../../assets/icons/common/photo-camera.svg';
import LibraryIcon from '../../assets/icons/common/photo-library.svg';

type Props = NativeStackScreenProps<RootStackParamList, 'ChatRoom'>;
const dateFormat = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
});
const timeFormat = new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit' });

function MessagePhoto({
  photo,
  maxSize,
}: {
  photo?: { uri: string; width: number; height: number };
  maxSize: number;
}) {
  const [ratio, setRatio] = useState(() =>
    photo && photo.width > 0 && photo.height > 0 ? photo.width / photo.height : 1,
  );
  if (!photo) {
    return null;
  }
  return (
    <Image
      accessible
      accessibilityLabel="채팅 사진"
      source={{ uri: photo.uri }}
      resizeMode="contain"
      onLoad={({ nativeEvent: { source } }) => {
        if (source.width > 0 && source.height > 0) {
          setRatio(source.width / source.height);
        }
      }}
      style={[
        s.photo,
        { width: maxSize * Math.min(1, ratio), height: maxSize * Math.min(1, 1 / ratio) },
      ]}
    />
  );
}

function PostCard({
  reference,
  navigation,
}: {
  reference: PostRef;
  navigation: Props['navigation'];
}) {
  const [card, setCard] = useState<{
    badge: string;
    imageUrl: string | null;
    summary: string;
    title: string;
  }>();
  useEffect(() => {
    let active = true;
    const load = async () => {
      const seq = Number(reference.id);
      if (!Number.isFinite(seq)) {
        return;
      }
      const detail =
        reference.type === 'adoption'
          ? await getShelterAnimal(seq)
          : reference.type === 'lost'
          ? await getMissingReport(seq)
          : await getFoundReport(seq);
      if (!active) {
        return;
      }
      const gender =
        detail.gender === 'MALE' ? '수컷' : detail.gender === 'FEMALE' ? '암컷' : '모름';
      const classification = detail.classification;
      setCard({
        badge: reference.type === 'adoption' ? '보호' : reference.type === 'lost' ? '실종' : '제보',
        imageUrl: detail.representativeImageUrl,
        summary: [
          detail.age ?? '나이 모름',
          gender,
          classification?.colorName ?? '털색 모름',
          detail.weight ?? '몸무게 모름',
        ].join(' | '),
        title: classification
          ? `[${classification.speciesName}] ${classification.breedName}`
          : '동물 정보 없음',
      });
    };
    load().catch(() => undefined);
    return () => {
      active = false;
    };
  }, [reference.id, reference.type]);
  const openPost = () => {
    if (reference.type === 'adoption') {
      navigation.navigate('AdoptionDetail', { animalId: reference.id });
    } else {
      navigation.navigate(reference.type === 'lost' ? 'LostReportDetail' : 'FoundReportDetail', {
        reportId: reference.id,
      });
    }
  };
  if (!card) {
    return null;
  }
  return (
    <Pressable accessibilityRole="button" onPress={openPost} style={s.post}>
      <Image
        source={
          card.imageUrl
            ? { uri: card.imageUrl }
            : require('../../assets/images/mypage/img_pet_card.png')
        }
        style={s.thumb}
      />
      <View style={s.postContent}>
        <View style={s.postHeading}>
          <Text
            style={[
              s.badge,
              reference.type === 'lost' && s.lostBadge,
              reference.type === 'found' && s.foundBadge,
            ]}
          >
            {card.badge}
          </Text>
          <Text numberOfLines={1} style={s.postTitle}>
            {card.title}
          </Text>
        </View>
        <Text numberOfLines={2} style={s.postText}>
          {card.summary}
        </Text>
      </View>
    </Pressable>
  );
}

export function ChatRoomScreen({ route, navigation }: Props) {
  const { roomId, preview } = route.params;
  useFocusEffect(useCallback(() => trackVisibleChatRoom(roomId), [roomId]));
  const room = useChatStore(state => state.rooms.find(item => item.id === roomId));
  const draft = useChatStore(state => state.drafts[roomId] ?? '');
  const { setDraft, send, markRead, leave, ensureRoom, loadMoreMessages } = useChatStore.getState();
  const focused = useIsFocused();
  const [appState, setAppState] = useState(AppState.currentState);
  const active = focused && appState === 'active';
  const [receipt, setReceipt] = useState<{ roomId: string; cursor: number | null }>();
  useEffect(() => {
    const subscription = AppState.addEventListener('change', setAppState);
    return () => subscription.remove();
  }, []);
  const list = useRef<FlatList>(null);
  const picking = useRef(false);
  const [action, setAction] = useState<ChatAction>();
  const [roomLoading, setRoomLoading] = useState(true);
  const [photoMenuOpen, setPhotoMenuOpen] = useState(false);
  useEffect(() => {
    if (!photoMenuOpen) {
      return;
    }
    const back = BackHandler.addEventListener('hardwareBackPress', () => {
      setPhotoMenuOpen(false);
      return true;
    });
    return () => back.remove();
  }, [photoMenuOpen]);
  const nickname = room?.name ?? preview?.name;
  const reportTarget = useMemo<ContentReportTarget | null>(() => {
    const userSeq = room?.counterpart.userSeq;
    return action === 'report' && nickname && typeof userSeq === 'number'
      ? {
          id: 'chat-user:' + userSeq,
          nickname,
          targetSeq: userSeq,
          targetType: 'USER',
          type: 'user',
        }
      : null;
  }, [action, room, nickname]);
  useEffect(() => {
    if (!active) {
      return;
    }
    let current = true;
    const apply = (cursor: number | null) => {
      if (!current || cursor === undefined) {
        return;
      }
      setReceipt(previous => {
        if (previous?.roomId === roomId && (previous.cursor ?? 0) >= (cursor ?? 0)) {
          return previous;
        }
        return { roomId, cursor };
      });
    };
    const unsubscribe = chatSocket.observeReadReceipts(
      (seq, cursor) => {
        if (String(seq) === roomId) {
          apply(cursor);
        }
      },
      () => {
        getChatRoom(Number(roomId))
          .then(detail => apply(detail.counterpartLastReadMessageSeq))
          .catch(() => undefined);
      },
    );
    ensureRoom(roomId)
      .then(cursor => {
        apply(cursor);
        if (current) {
          return markRead(roomId);
        }
      })
      .catch(() => {
        if (current) {
          Alert.alert('채팅', '대화 내역을 불러올 수 없어요.');
        }
      })
      .finally(() => {
        if (current) {
          setRoomLoading(false);
        }
      });
    return () => {
      current = false;
      unsubscribe();
    };
  }, [active, ensureRoom, markRead, roomId]);
  useEffect(() => {
    if (action === 'report' && nickname && !reportTarget) {
      Alert.alert('사용자를 신고할 수 없어요', '상대 회원 정보를 확인할 수 없습니다.');
      setAction(undefined);
    }
  }, [action, nickname, reportTarget]);
  const { width } = useWindowDimensions();
  const photoMax = Math.min(260, width * 0.63);
  const newestFirst = useMemo(() => room?.messages.slice().reverse() ?? [], [room?.messages]);
  const pendingPost =
    preview?.post && !samePost(preview.post, newestFirst[0]?.post) ? preview.post : undefined;
  const sendingPreview = useRef(false);
  const sendFromRoom = async (photo?: Parameters<typeof send>[1]) => {
    if (sendingPreview.current) {
      return;
    }
    sendingPreview.current = Boolean(preview);
    try {
      await send(roomId, photo, pendingPost ? preview : undefined);
      if (preview) {
        navigation.setParams({ preview: undefined });
      }
    } finally {
      sendingPreview.current = false;
    }
  };
  const latestId = newestFirst[0]?.id;
  useEffect(() => {
    // In an inverted list the newest message is always at offset zero,
    // even before the new bubble or keyboard layout has been measured.
    list.current?.scrollToOffset({ offset: 0, animated: false });
  }, [roomId, latestId]);
  useEffect(() => {
    if (active) {
      markRead(roomId).catch(() => undefined);
    }
  }, [active, latestId, roomId, markRead]);
  const pickPhoto = async (source: 'library' | 'camera') => {
    setPhotoMenuOpen(false);
    if (picking.current) {
      return;
    }
    picking.current = true;
    Keyboard.dismiss();
    try {
      if (source === 'camera' && Platform.OS === 'android') {
        const permission = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.CAMERA);
        if (permission !== PermissionsAndroid.RESULTS.GRANTED) {
          Alert.alert('카메라 권한이 필요해요', '설정에서 카메라 권한을 허용해주세요.');
          return;
        }
      }
      const result =
        source === 'camera'
          ? await launchCamera({ mediaType: 'photo', saveToPhotos: false })
          : await launchImageLibrary({
              mediaType: 'photo',
              selectionLimit: 1,
              restrictMimeTypes: ['image/jpeg', 'image/png'],
            });
      if (result.didCancel) {
        return;
      }
      const asset = result.assets?.[0];
      if (result.errorCode || !asset?.uri) {
        throw new Error('picker');
      }
      if (
        !['image/jpeg', 'image/png'].includes(asset.type ?? '') ||
        !asset.fileSize ||
        asset.fileSize > 10 * 1024 * 1024
      ) {
        Alert.alert('사진 첨부', 'JPG 또는 PNG 사진을 10MB 이하로 선택해 주세요.');
        return;
      }
      await sendFromRoom({
        byteSize: asset.fileSize,
        contentType: asset.type as 'image/jpeg' | 'image/png',
        fileName: asset.fileName ?? `chat-${Date.now()}.jpg`,
        height: asset.height || 1,
        uri: asset.uri,
        width: asset.width || 1,
      });
    } catch {
      Alert.alert('사진 첨부', '사진을 저장할 수 없어요. 다시 시도해 주세요.');
    } finally {
      picking.current = false;
    }
  };
  return (
    <SafeAreaView style={s.screen}>
      <AppHeader
        title={nickname ?? '채팅'}
        titleAccessory={
          room?.counterpart.type === 'SHELTER' ? <BadgeIcon height={18} width={18} /> : undefined
        }
        onBackPress={() => navigation.goBack()}
        rightAction={
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="채팅방 메뉴"
            accessibilityState={{ expanded: action === 'menu' }}
            onPress={() => {
              Keyboard.dismiss();
              setPhotoMenuOpen(false);
              setAction('menu');
            }}
            hitSlop={10}
            style={s.headerMenuButton}
          >
            <DotsIcon width={24} height={24} />
          </Pressable>
        }
      />
      {room || preview ? (
        <KeyboardAvoidingView
          style={s.screen}
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        >
          <View style={s.historyArea}>
            <FlatList
              ref={list}
              decelerationRate="fast"
              inverted
              data={newestFirst}
              contentContainerStyle={s.history}
              keyExtractor={item => item.id}
              keyboardShouldPersistTaps="handled"
              onEndReached={() => loadMoreMessages(roomId).catch(() => undefined)}
              onEndReachedThreshold={0.4}
              ListHeaderComponentStyle={pendingPost ? s.previewContainer : undefined}
              ListHeaderComponent={
                pendingPost ? (
                  <PostCard reference={pendingPost} navigation={navigation} />
                ) : undefined
              }
              renderItem={({ item, index }) => {
                const previous = newestFirst[index + 1];
                const date = dateFormat.format(item.sentAt);
                const newDay = !previous || dateFormat.format(previous.sentAt) !== date;
                const showCard = item.post && !samePost(item.post, previous?.post);
                const first = newDay || showCard || previous?.mine !== item.mine;
                const meta = (
                  <View style={s.meta}>
                    {item.mine &&
                      !item.pending &&
                      receipt?.roomId === roomId &&
                      Number(item.id) > (receipt.cursor ?? 0) && (
                        <Text style={s.sendStatus}>미읽음</Text>
                      )}
                    {item.pending && <Text style={s.sendStatus}>전송 중</Text>}
                    <Text style={s.time}>{timeFormat.format(item.sentAt)}</Text>
                  </View>
                );
                return (
                  <View>
                    {newDay && (
                      <View style={s.date}>
                        <View style={s.rule} />
                        <Text style={s.dateText}>{date}</Text>
                        <View style={s.rule} />
                      </View>
                    )}
                    {showCard && item.post && (
                      <PostCard reference={item.post} navigation={navigation} />
                    )}
                    <View style={[s.row, first && s.group, item.mine && s.myRow]}>
                      {!item.mine && (
                        <View style={s.avatarSlot}>
                          {first && (
                            <Image
                              source={
                                room?.counterpart.profileImage?.imageUrl
                                  ? { uri: room.counterpart.profileImage.imageUrl }
                                  : require('../../assets/images/mypage/profile-default.png')
                              }
                              style={s.avatar}
                            />
                          )}
                        </View>
                      )}
                      {item.mine && meta}
                      {item.kind === 'photo' ? (
                        <MessagePhoto key={item.photo?.uri} photo={item.photo} maxSize={photoMax} />
                      ) : (
                        <View style={[s.bubble, item.mine && s.myBubble]}>
                          <Text selectable style={[s.text, item.mine && s.myText]}>
                            {item.text}
                          </Text>
                        </View>
                      )}
                      {!item.mine && meta}
                    </View>
                  </View>
                );
              }}
            />
            {photoMenuOpen && (
              <>
                <Pressable
                  accessibilityLabel="사진 첨부 메뉴 닫기"
                  style={StyleSheet.absoluteFill}
                  onPress={() => setPhotoMenuOpen(false)}
                />
                <View style={s.photoMenu}>
                  <Pressable
                    accessibilityRole="button"
                    style={s.photoMenuItem}
                    onPress={() => pickPhoto('library')}
                  >
                    <LibraryIcon height={22} width={22} />
                    <Text style={s.photoMenuText}>사진 보관함</Text>
                  </Pressable>
                  <Pressable
                    accessibilityRole="button"
                    style={s.photoMenuItem}
                    onPress={() => pickPhoto('camera')}
                  >
                    <CameraIcon height={22} width={22} />
                    <Text style={s.photoMenuText}>사진 찍기</Text>
                  </Pressable>
                </View>
              </>
            )}
          </View>
          <View style={s.composer}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="사진 첨부"
              accessibilityState={{ expanded: photoMenuOpen }}
              onPress={() => setPhotoMenuOpen(open => !open)}
              style={s.icon}
            >
              <PlusIcon width={20} height={20} />
            </Pressable>
            <TextInput
              accessibilityLabel="메시지"
              onFocus={() => setPhotoMenuOpen(false)}
              multiline
              onChangeText={value => setDraft(roomId, value)}
              placeholder="메시지를 입력하세요"
              placeholderTextColor={colors.gray300}
              style={s.input}
              value={draft}
            />
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="메시지 보내기"
              disabled={!draft.trim()}
              accessibilityState={{ disabled: !draft.trim() }}
              onPress={() => {
                setPhotoMenuOpen(false);
                sendFromRoom().catch(error =>
                  Alert.alert(
                    '메시지를 보내지 못했어요',
                    error instanceof Error ? error.message : '잠시 후 다시 시도해 주세요.',
                  ),
                );
              }}
              style={s.icon}
            >
              <SendIcon width={20} height={20} color={draft.trim() ? colors.green400 : '#797770'} />
            </Pressable>
          </View>
        </KeyboardAvoidingView>
      ) : roomLoading ? (
        <View style={s.loading}>
          <ActivityIndicator color={colors.green400} />
        </View>
      ) : (
        <Text>채팅방을 찾을 수 없어요.</Text>
      )}
      <ContentReportModal target={reportTarget} onClose={() => setAction(undefined)} />
      {action && action !== 'report' && (
        <ChatActions
          action={action}
          canReport={room?.counterpart.type !== 'SHELTER'}
          onChoose={setAction}
          onClose={() => setAction(undefined)}
          onLeave={async () => {
            setAction(undefined);
            try {
              await leave(roomId);
              navigation.goBack();
            } catch {
              Alert.alert('채팅', '채팅방을 나갈 수 없어요. 다시 시도해 주세요.');
            }
          }}
        />
      )}
    </SafeAreaView>
  );
}
const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: 'white' },
  loading: { alignItems: 'center', flex: 1, justifyContent: 'center' },
  history: { flexGrow: 1, justifyContent: 'flex-end' },
  date: { flexDirection: 'row', alignItems: 'center', gap: 8, marginVertical: 10 },
  dateText: { ...typography.bodyRegular14, fontSize: 12, color: colors.gray300 },
  rule: { height: StyleSheet.hairlineWidth, backgroundColor: colors.gray300, flex: 1 },
  row: { flexDirection: 'row', alignItems: 'flex-end', gap: 8, marginHorizontal: 18, marginTop: 6 },
  group: { marginTop: 14 },
  myRow: { justifyContent: 'flex-end' },
  avatarSlot: { width: 40, alignSelf: 'flex-start' },
  avatar: { width: 40, height: 40, borderRadius: 20 },
  bubble: {
    maxWidth: '68%',
    backgroundColor: '#70B5A3',
    borderRadius: 16,
    borderTopLeftRadius: 0,
    borderBottomLeftRadius: 6,
    paddingVertical: 10,
    paddingHorizontal: 16,
    flexShrink: 1,
  },
  myBubble: {
    maxWidth: '78%',
    backgroundColor: '#EBEAE5',
    borderTopLeftRadius: 16,
    borderBottomLeftRadius: 16,
    borderBottomRightRadius: 0,
  },
  text: { ...typography.bodyRegular16, color: 'white' },
  myText: { color: colors.gray700 },
  meta: { alignItems: 'flex-end' },
  time: { ...typography.captionRegular10, color: colors.gray500 },
  sendStatus: { ...typography.captionRegular10, color: colors.gray500 },
  previewContainer: { paddingBottom: 8 },
  post: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    marginHorizontal: 26,
    marginTop: 14,
    padding: 10,
    backgroundColor: 'white',
    borderRadius: 10,
    boxShadow: '0 1px 5px #0003',
  },
  foundBadge: { backgroundColor: colors.blue },
  lostBadge: { backgroundColor: '#FF715B' },
  thumb: { width: 60, height: 60, borderRadius: 6 },
  postContent: { flex: 1, gap: 6 },
  postTitle: { ...typography.bodySemiBold16, color: colors.black },
  postHeading: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  badge: {
    ...typography.captionSemiBold12,
    color: 'white',
    backgroundColor: colors.green400,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  photo: { borderRadius: 10 },
  postText: { ...typography.bodyRegular14, color: colors.gray500 },
  historyArea: { flex: 1 },
  photoMenu: {
    position: 'absolute',
    bottom: 0,
    left: 26,
    backgroundColor: colors.white,
    borderColor: colors.gray200,
    borderRadius: 10,
    borderWidth: 1,
    paddingVertical: 6,
    width: 154,
  },
  photoMenuItem: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 12,
    height: 44,
    paddingHorizontal: 14,
  },
  photoMenuText: { ...typography.bodyRegular14, color: colors.gray700 },
  composer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F1F1F1',
    borderRadius: 8,
    minHeight: 48,
    marginHorizontal: 26,
    marginTop: 8,
    marginBottom: 16,
    paddingHorizontal: 3,
  },
  input: {
    ...typography.bodyRegular16,
    color: colors.black,
    flex: 1,
    maxHeight: 110,
    paddingVertical: 10,
  },
  headerMenuButton: {
    width: 40,
    height: 40,
    alignSelf: 'flex-end',
    alignItems: 'center',
    justifyContent: 'center',
  },
  icon: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
});
