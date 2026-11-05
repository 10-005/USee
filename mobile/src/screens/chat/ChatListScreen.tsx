import { useEffect, useState } from 'react';
import { Alert, FlatList, PanResponder, Pressable, StyleSheet, Text, View } from 'react-native';
import TrashIcon from '../../assets/icons/common/chat-trash.svg';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ChatListItem } from '../../components/common';
import { ChatActions } from './ChatActions';
import { useChatStore } from '../../stores/chatStore';
import { colors, radius, typography } from '../../theme';

const avatarSource = require('../../assets/images/chat/avatar.png');
export function ChatListScreen({ onOpenRoom }: { onOpenRoom: (id: string) => void }) {
  const rooms = useChatStore(state => state.rooms);
  const loaded = useChatStore(state => state.loaded);
  useEffect(() => {
    const load = () =>
      useChatStore
        .getState()
        .load()
        .catch(() =>
          Alert.alert('채팅', '대화 내역을 불러올 수 없어요.', [
            { text: '다시 시도', onPress: load },
          ]),
        );
    load();
  }, []);
  const unreadRooms = rooms.filter(room => room.unreadCount > 0);
  const unreadTotal = unreadRooms.reduce((sum, room) => sum + room.unreadCount, 0);
  const [filter, setFilter] = useState<'전체' | '안 읽음'>('전체');
  const insets = useSafeAreaInsets();
  const [revealed, setRevealed] = useState<string>();
  const [leaving, setLeaving] = useState<string>();

  return (
    <View style={styles.screen}>
      <View style={styles.filters}>
        {(['전체', '안 읽음'] as const).map(label => (
          <Pressable
            accessibilityRole="tab"
            accessibilityState={{ selected: filter === label }}
            key={label}
            onPress={() => {
              setFilter(label);
              setRevealed(undefined);
            }}
            style={styles.chip}
          >
            <View style={styles.tabTitle}>
              <Text style={filter === label ? styles.selectedLabel : styles.label}>{label}</Text>
              {label === '안 읽음' && unreadTotal > 0 && (
                <Text style={styles.count}>{unreadTotal}</Text>
              )}
            </View>
            <View style={filter === label ? styles.activeIndicator : styles.inactiveIndicator} />
          </Pressable>
        ))}
      </View>
      <FlatList
        contentContainerStyle={[styles.list, { paddingBottom: 104 + insets.bottom }]}
        data={filter === '전체' ? rooms : unreadRooms}
        decelerationRate="fast"
        keyExtractor={room => room.id}
        ListEmptyComponent={
          <Text style={styles.empty}>
            {loaded ? '표시할 대화가 없습니다.' : '대화를 불러오는 중이에요.'}
          </Text>
        }
        renderItem={({ item }) => {
          const last = item.messages[item.messages.length - 1];
          const open = revealed === item.id;
          const swipe = PanResponder.create({
            onMoveShouldSetPanResponderCapture: (_, g) =>
              Math.abs(g.dx) > 12 && Math.abs(g.dx) > Math.abs(g.dy) * 1.5,
            onPanResponderRelease: (_, g) => {
              if (Math.abs(g.dx) > 24) {
                setRevealed(g.dx < 0 ? item.id : undefined);
              }
            },
          });
          return (
            <View {...swipe.panHandlers} style={styles.item}>
              {open && (
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel="채팅방 나가기"
                  onPress={() => setLeaving(item.id)}
                  style={styles.delete}
                >
                  <TrashIcon width={22} height={22} />
                </Pressable>
              )}
              <View style={open && styles.shift}>
                <ChatListItem
                  avatarSource={avatarSource}
                  name={item.name}
                  lastMessage={last?.kind === 'photo' ? '사진' : last?.text ?? ''}
                  time={last ? roomTime(last.sentAt) : ''}
                  unreadCount={item.unreadCount}
                  onPress={() => {
                    if (open) {
                      setRevealed(undefined);
                    } else {
                      onOpenRoom(item.id);
                    }
                  }}
                />
              </View>
            </View>
          );
        }}
      />
      {leaving && (
        <ChatActions
          action="leave"
          onClose={() => setLeaving(undefined)}
          onLeave={() => {
            if (leaving) {
              useChatStore
                .getState()
                .leave(leaving)
                .catch(() => Alert.alert('채팅', '채팅방을 나갈 수 없어요. 다시 시도해 주세요.'));
            }
            setLeaving(undefined);
            setRevealed(undefined);
          }}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.white },
  filters: { flexDirection: 'row', height: 56 },
  chip: { flex: 1, alignItems: 'center', justifyContent: 'flex-end' },
  tabTitle: { flexDirection: 'row', alignItems: 'center', gap: 4, marginBottom: 16 },
  label: { ...typography.bodyRegular16, color: colors.gray600 },
  selectedLabel: { ...typography.bodySemiBold16, color: colors.green400 },
  activeIndicator: { backgroundColor: colors.green400, height: 4, width: '100%' },
  inactiveIndicator: { backgroundColor: colors.gray200, height: 1, width: '100%' },
  count: {
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.green100,
    color: colors.green100,
    paddingHorizontal: 5,
  },
  list: { paddingHorizontal: 26, paddingTop: 8 },
  item: { justifyContent: 'center', overflow: 'hidden', minHeight: 84 },
  shift: { transform: [{ translateX: -54 }], backgroundColor: 'white' },
  delete: {
    position: 'absolute',
    right: 0,
    width: 45,
    height: 60,
    borderRadius: 4,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FA705E',
  },
  empty: { ...typography.bodyRegular16, color: colors.gray500, textAlign: 'center', marginTop: 40 },
});

function roomTime(sentAt: number) {
  const hours = Math.floor((Date.now() - sentAt) / 3600000);
  if (hours === 0) {
    return '방금 전';
  }
  if (hours < 24) {
    return `${hours}시간 전`;
  }
  return new Date(sentAt).toLocaleDateString('ko-KR');
}
