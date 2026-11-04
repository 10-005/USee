import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  FlatList,
  Image,
  Keyboard,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  useWindowDimensions,
  View,
} from 'react-native';
import { launchImageLibrary } from 'react-native-image-picker';
import { SafeAreaView } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';
import type { RootStackParamList } from '../../app/navigation/types';
import { AppHeader } from '../../components/common';
import { useChatStore } from '../../stores/chatStore';
import {
  saveChatPhoto,
  removeChatPhoto,
  chatPhotoUri,
} from '../../services/storage/chatPhotoStorage';
import { colors, typography } from '../../theme';
import { adoptionAnimals } from '../adoption/data';
import { ChatActions, type ChatAction } from './ChatActions';
import {
  ContentReportModal,
  type ContentReportTarget,
} from '../../components/report/ContentReportModal';
import { useContentReportStore } from '../../stores/contentReportStore';
import DotsIcon from '../../assets/icons/common/dots-vertical.svg';
import PlusIcon from '../../assets/icons/common/plus.svg';

type Props = NativeStackScreenProps<RootStackParamList, 'ChatRoom'>;
const dateFormat = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
});
const timeFormat = new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit' });

export function ChatRoomScreen({ route, navigation }: Props) {
  const { roomId } = route.params;
  const room = useChatStore(state => state.rooms.find(item => item.id === roomId));
  const draft = useChatStore(state => state.drafts[roomId] ?? '');
  const { setDraft, send, markRead, leave } = useChatStore.getState();
  const list = useRef<FlatList>(null);
  const picking = useRef(false);
  const [action, setAction] = useState<ChatAction>();
  const nickname = room?.name;
  const reportTarget = useMemo<ContentReportTarget | null>(
    () =>
      action === 'report' && nickname
        ? { id: 'chat-user:' + roomId, nickname, type: 'user' }
        : null,
    [action, roomId, nickname],
  );
  const reported = useContentReportStore(state =>
    state.reports.some(item => item.targetId === reportTarget?.id),
  );
  const { width } = useWindowDimensions();
  const photoMax = Math.min(260, width * 0.63);
  const animal = room?.animalId ? adoptionAnimals.find(a => a.id === room.animalId) : undefined;
  const newestFirst = useMemo(() => room?.messages.slice().reverse() ?? [], [room?.messages]);
  const latestId = newestFirst[0]?.id;
  useEffect(() => {
    // In an inverted list the newest message is always at offset zero,
    // even before the new bubble or keyboard layout has been measured.
    list.current?.scrollToOffset({ offset: 0, animated: false });
  }, [roomId, latestId]);
  useEffect(() => {
    markRead(roomId).catch(() => Alert.alert('채팅', '대화 내역을 불러올 수 없어요.'));
  }, [roomId, markRead]);
  const pickPhoto = async () => {
    if (picking.current) {
      return;
    }
    picking.current = true;
    Keyboard.dismiss();
    try {
      const result = await launchImageLibrary({
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
      const filename = await saveChatPhoto(asset.uri, asset.type!);
      try {
        await send(roomId, { filename, width: asset.width || 1, height: asset.height || 1 });
      } catch (error) {
        await removeChatPhoto(filename).catch(() => {});
        throw error;
      }
    } catch {
      Alert.alert('사진 첨부', '사진을 저장할 수 없어요. 다시 시도해 주세요.');
    } finally {
      picking.current = false;
    }
  };
  return (
    <SafeAreaView style={s.screen}>
      <AppHeader
        title={room?.name ?? '채팅'}
        onBackPress={() => navigation.goBack()}
        rightAction={
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="채팅방 메뉴"
            accessibilityState={{ expanded: action === 'menu' }}
            onPress={() => {
              Keyboard.dismiss();
              setAction('menu');
            }}
            style={s.icon}
          >
            <DotsIcon width={24} height={24} />
          </Pressable>
        }
      />
      {room ? (
        <KeyboardAvoidingView
          style={s.screen}
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        >
          <FlatList
            ref={list}
            inverted
            data={newestFirst}
            contentContainerStyle={s.history}
            keyExtractor={item => item.id}
            keyboardShouldPersistTaps="handled"
            renderItem={({ item, index }) => {
              const previous = newestFirst[index + 1];
              const date = dateFormat.format(item.sentAt);
              const newDay = !previous || dateFormat.format(previous.sentAt) !== date;
              const first = newDay || previous?.mine !== item.mine;
              const meta = (
                <View style={s.meta}>
                  {item.mine && <Text style={s.unread}>안읽음</Text>}
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
                  {index === newestFirst.length - 1 && animal && (
                    <Pressable
                      accessibilityRole="button"
                      onPress={() => navigation.navigate('AdoptionDetail', { animalId: animal.id })}
                      style={s.post}
                    >
                      <Image source={animal.image} style={s.thumb} />
                      <View style={s.postContent}>
                        <View style={s.postHeading}>
                          <Text style={s.badge}>{animal.status}</Text>
                          <Text style={s.postTitle}>{animal.name}</Text>
                        </View>
                        <Text numberOfLines={2} style={s.postText}>
                          {animal.description}
                        </Text>
                      </View>
                    </Pressable>
                  )}
                  <View style={[s.row, first && s.group, item.mine && s.myRow]}>
                    {!item.mine && (
                      <View style={s.avatarSlot}>
                        {first && (
                          <Image
                            source={require('../../assets/images/chat/avatar.png')}
                            style={s.avatar}
                          />
                        )}
                      </View>
                    )}
                    {item.mine && meta}
                    {item.kind === 'photo' ? (
                      <Image
                        accessibilityLabel="채팅 사진"
                        accessible
                        source={{ uri: chatPhotoUri(item.photo.filename) }}
                        resizeMode="contain"
                        style={[
                          s.photo,
                          {
                            width: photoMax * Math.min(1, item.photo.width / item.photo.height),
                            height: photoMax * Math.min(1, item.photo.height / item.photo.width),
                          },
                        ]}
                      />
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
          <View style={s.composer}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="사진 첨부"
              onPress={pickPhoto}
              style={s.icon}
            >
              <PlusIcon width={20} height={20} />
            </Pressable>
            <TextInput
              accessibilityLabel="메시지"
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
              onPress={() =>
                send(roomId).catch(() => Alert.alert('채팅', '메시지를 저장할 수 없어요.'))
              }
              style={s.icon}
            >
              <Svg width={20} height={20} viewBox="0 0 20 20">
                <Path
                  d="M17.6 2.4 8.7 11.3M2.7 6.9l13.9-4.8c.9-.3 1.7.5 1.4 1.4l-4.9 13.8c-.3 1-1.6 1-2 .1l-2.3-5.8-6.1-2.7c-.9-.4-.9-1.6 0-2Z"
                  stroke={draft.trim() ? colors.green400 : '#797770'}
                  fill="none"
                  strokeLinecap="round"
                  strokeWidth={1.8}
                />
              </Svg>
            </Pressable>
          </View>
        </KeyboardAvoidingView>
      ) : (
        <Text>채팅방을 찾을 수 없어요.</Text>
      )}
      <ContentReportModal
        target={reportTarget}
        alreadyReported={reported}
        onClose={() => setAction(undefined)}
        onSubmit={reason => {
          if (reportTarget) {
            useContentReportStore
              .getState()
              .addReport({ reason, targetId: reportTarget.id, targetType: reportTarget.type });
          }
          setAction(undefined);
        }}
      />
      {action && action !== 'report' && (
        <ChatActions
          action={action}
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
  unread: { ...typography.captionRegular10, color: colors.gray200 },
  post: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    marginHorizontal: 26,
    marginTop: 6,
    padding: 10,
    backgroundColor: 'white',
    borderRadius: 10,
    boxShadow: '0 1px 5px #0003',
  },
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
  icon: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
});
