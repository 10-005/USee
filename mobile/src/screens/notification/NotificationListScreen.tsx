import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Image,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import { AppHeader } from '../../components/common';
import { getApiErrorDisplayMessage } from '../../services/api';
import {
  getMyNotifications,
  markNotificationAsRead,
  type NotificationListQuery,
} from '../../services/notifications/notificationApi';
import {
  toNotificationListEntry,
  type NotificationListEntry,
} from '../../services/notifications/notificationListItem';
import { resolveNotificationDestination } from '../../services/notifications/pushDestination';
import type { NotificationCategory } from '../../stores/notificationStore';
import { colors, radius, typography } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'Notifications'>;
type NotificationFilter = 'all' | 'unread' | 'aiMatching';
type DeleteTarget = NotificationListEntry | 'all' | null;

const filters: Array<{ key: NotificationFilter; label: string }> = [
  { key: 'all', label: '전체' },
  { key: 'unread', label: '읽지 않음' },
  { key: 'aiMatching', label: 'AI 매칭' },
];

export function NotificationListScreen({ navigation }: Props) {
  const [notifications, setNotifications] = useState<NotificationListEntry[]>([]);
  const [filter, setFilter] = useState<NotificationFilter>('all');
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadNotifications = useCallback(() => {
    setIsLoading(true);
    getMyNotifications(queryFor(filter))
      .then(page => {
        setNotifications(
          page.content.flatMap(item => {
            const entry = toNotificationListEntry(item);
            return entry ? [entry] : [];
          }),
        );
        setError(null);
      })
      .catch(loadError => {
        setError(getApiErrorDisplayMessage(loadError, '알림을 불러오지 못했어요.'));
      })
      .finally(() => setIsLoading(false));
  }, [filter]);

  useFocusEffect(
    useCallback(() => {
      loadNotifications();
    }, [loadNotifications]),
  );

  const visibleNotifications = useMemo(
    () =>
      filter === 'unread'
        ? notifications.filter(notification => !notification.isRead)
        : notifications,
    [filter, notifications],
  );

  const openNotification = (notification: NotificationListEntry) => {
    openNotificationDestination(navigation, notification);
    if (notification.isRead) {
      return;
    }
    markNotificationAsRead(Number(notification.id))
      .then(() => {
        setNotifications(current =>
          current.map(item => (item.id === notification.id ? { ...item, isRead: true } : item)),
        );
      })
      .catch(() => undefined);
  };

  const confirmDelete = () => {
    if (deleteTarget === 'all') {
      setNotifications([]);
    } else if (deleteTarget) {
      setNotifications(current => current.filter(item => item.id !== deleteTarget.id));
    }
    setDeleteTarget(null);
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <AppHeader onBackPress={navigation.goBack} title="알림" />
      <View style={styles.filterBar}>
        <View style={styles.filters}>
          {filters.map(item => {
            const selected = item.key === filter;
            return (
              <Pressable
                accessibilityRole="button"
                accessibilityState={{ selected }}
                key={item.key}
                onPress={() => setFilter(item.key)}
                style={[styles.filter, selected && styles.selectedFilter]}
              >
                <Text style={[styles.filterText, selected && styles.selectedFilterText]}>
                  {item.label}
                </Text>
              </Pressable>
            );
          })}
        </View>
        <Pressable
          accessibilityRole="button"
          disabled={visibleNotifications.length === 0}
          hitSlop={8}
          onPress={() => setDeleteTarget('all')}
        >
          <Text
            style={[
              styles.deleteAll,
              visibleNotifications.length === 0 && styles.disabledDeleteAll,
            ]}
          >
            전체 삭제
          </Text>
        </Pressable>
      </View>

      <FlatList
        decelerationRate="fast"
        contentContainerStyle={[
          styles.listContent,
          visibleNotifications.length === 0 && styles.emptyListContent,
        ]}
        data={visibleNotifications}
        keyExtractor={item => item.id}
        ListEmptyComponent={
          isLoading ? (
            <ActivityIndicator style={styles.loading} />
          ) : error ? (
            <NotificationErrorState message={error} onRetry={loadNotifications} />
          ) : (
            <NotificationEmptyState />
          )
        }
        renderItem={({ item }) => (
          <NotificationItem
            notification={item}
            onLongPress={() => setDeleteTarget(item)}
            onPress={() => openNotification(item)}
          />
        )}
        showsVerticalScrollIndicator={false}
      />

      <DeleteNotificationModal
        all={deleteTarget === 'all'}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={confirmDelete}
        visible={deleteTarget !== null}
      />
    </SafeAreaView>
  );
}

function queryFor(filter: NotificationFilter): NotificationListQuery {
  if (filter === 'unread') {
    return { isRead: false };
  }
  if (filter === 'aiMatching') {
    return { notificationType: 'AI_MATCH' };
  }
  return {};
}

function openNotificationDestination(
  navigation: Props['navigation'],
  notification: NotificationListEntry,
) {
  const destination = resolveNotificationDestination({
    body: notification.body,
    kind: 'server',
    notificationSeq: notification.id,
    notificationType: notification.notificationType,
    title: notification.title,
    ...(notification.targetCommentId ? { commentSeq: notification.targetCommentId } : {}),
    ...(notification.targetId ? { targetSeq: notification.targetId } : {}),
    ...(notification.targetType ? { targetType: notification.targetType } : {}),
  });
  if (!destination) {
    return;
  }
  switch (destination.name) {
    case 'AdoptionApplicationHistory':
      navigation.navigate('AdoptionApplicationHistory', { animalId: destination.animalId });
      return;
    case 'FoundReportDetail':
      navigation.navigate('FoundReportDetail', {
        highlightCommentId: destination.highlightCommentId,
        reportId: destination.reportId,
      });
      return;
    case 'Home':
      navigation.navigate('Home', { initialTab: destination.initialTab });
      return;
    case 'LostReportDetail':
      navigation.navigate('LostReportDetail', {
        highlightCommentId: destination.highlightCommentId,
        reportId: destination.reportId,
      });
      return;
    case 'Simulation':
      navigation.navigate('MockAdoptionOverview');
      return;
  }
}

function NotificationItem({
  notification,
  onLongPress,
  onPress,
}: {
  notification: NotificationListEntry;
  onLongPress: () => void;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityHint="길게 누르면 알림을 삭제할 수 있습니다"
      accessibilityRole="button"
      delayLongPress={450}
      onLongPress={onLongPress}
      onPress={onPress}
      style={({ pressed }) => [styles.item, pressed && styles.pressedItem]}
    >
      <NotificationTypeIcon category={notification.category} />
      <View style={styles.itemContent}>
        <View style={styles.itemHeader}>
          <Text numberOfLines={1} style={styles.itemTitle}>
            {notification.title}
          </Text>
          <View style={styles.timeRow}>
            <Text style={styles.time}>{notification.createdAtLabel}</Text>
            {!notification.isRead ? (
              <View accessibilityLabel="읽지 않음" style={styles.unreadDot} />
            ) : null}
          </View>
        </View>
        <Text numberOfLines={1} style={styles.itemBody}>
          {notification.body}
        </Text>
      </View>
    </Pressable>
  );
}

function NotificationTypeIcon({ category }: { category: NotificationCategory }) {
  const iconCategory = category === 'aiMatching' ? 'report' : category;
  return (
    <View style={styles.iconViewport}>
      <Image
        resizeMode="stretch"
        source={require('../../assets/images/notification/notification-type-sprite.png')}
        style={[
          styles.iconSprite,
          iconCategory === 'reply' && styles.replyIcon,
          iconCategory === 'adoption' && styles.adoptionIcon,
        ]}
      />
    </View>
  );
}

function NotificationErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <View style={styles.emptyState}>
      <Text style={styles.emptyText}>{message}</Text>
      <Pressable accessibilityRole="button" onPress={onRetry}>
        <Text style={styles.retryText}>다시 시도</Text>
      </Pressable>
    </View>
  );
}

function NotificationEmptyState() {
  return (
    <View style={styles.emptyState}>
      <View style={styles.emptyImageViewport}>
        <Image
          source={require('../../assets/images/notification/notification-empty-sprite.png')}
          style={styles.emptyImageSprite}
        />
      </View>
      <Text style={styles.emptyText}>알림 내역이 없습니다</Text>
    </View>
  );
}

function DeleteNotificationModal({
  all,
  onCancel,
  onConfirm,
  visible,
}: {
  all: boolean;
  onCancel: () => void;
  onConfirm: () => void;
  visible: boolean;
}) {
  return (
    <Modal animationType="fade" onRequestClose={onCancel} transparent visible={visible}>
      <View style={styles.modalOverlay}>
        <View accessibilityViewIsModal style={styles.modalCard}>
          <Text style={styles.modalTitle}>
            {all ? '모든 알림을 삭제할까요?' : '이 알림을 삭제할까요?'}
          </Text>
          <Text style={styles.modalDescription}>삭제된 알림은 복구되지 않습니다.</Text>
          <View style={styles.modalActions}>
            <Pressable onPress={onCancel} style={[styles.modalButton, styles.cancelButton]}>
              <Text style={styles.modalButtonText}>취소</Text>
            </Pressable>
            <Pressable onPress={onConfirm} style={[styles.modalButton, styles.deleteButton]}>
              <Text style={styles.modalButtonText}>삭제하기</Text>
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  filterBar: {
    alignItems: 'center',
    flexDirection: 'row',
    height: 62,
    justifyContent: 'space-between',
    paddingHorizontal: 26,
  },
  filters: { flexDirection: 'row', gap: 8 },
  filter: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.pill,
    borderWidth: 1,
    height: 26,
    justifyContent: 'center',
    paddingHorizontal: 12,
  },
  selectedFilter: { borderColor: colors.green100 },
  filterText: { ...typography.bodyRegular14, color: colors.gray500 },
  selectedFilterText: { color: colors.green400 },
  deleteAll: { ...typography.bodyRegular14, color: '#FA705B' },
  disabledDeleteAll: { opacity: 0.45 },
  listContent: { gap: 18, paddingBottom: 28, paddingHorizontal: 26 },
  emptyListContent: { flexGrow: 1 },
  item: {
    alignItems: 'center',
    backgroundColor: '#F5F7FA',
    borderRadius: 16,
    flexDirection: 'row',
    height: 77,
    paddingHorizontal: 10,
  },
  pressedItem: { opacity: 0.7 },
  iconViewport: { height: 36, overflow: 'hidden', width: 36 },
  iconSprite: { height: 46, left: -7, position: 'absolute', top: -5, width: 136 },
  replyIcon: { left: -50 },
  adoptionIcon: { left: -92 },
  itemContent: { flex: 1, marginLeft: 10 },
  itemHeader: { alignItems: 'center', flexDirection: 'row' },
  itemTitle: {
    color: colors.black,
    flex: 1,
    fontFamily: 'Pretendard-SemiBold',
    fontSize: 14,
    lineHeight: 18,
  },
  timeRow: { alignItems: 'center', flexDirection: 'row', gap: 2, marginLeft: 6 },
  time: { color: '#777671', fontFamily: 'Pretendard-Regular', fontSize: 12, lineHeight: 15 },
  unreadDot: {
    backgroundColor: '#FA705B',
    borderRadius: 3,
    height: 6,
    transform: [{ translateY: -4 }],
    width: 6,
  },
  itemBody: {
    color: '#777671',
    fontFamily: 'Pretendard-Regular',
    fontSize: 12,
    lineHeight: 15,
    marginTop: 12,
  },
  emptyState: { alignItems: 'center', flex: 1, paddingTop: 184 },
  emptyImageViewport: { height: 160, overflow: 'hidden', width: 160 },
  emptyImageSprite: { height: 333, left: -68, position: 'absolute', top: -174, width: 667 },
  emptyText: { ...typography.bodyRegular14, color: '#777671', marginTop: 18 },
  loading: { marginTop: 48 },
  retryText: { ...typography.bodySemiBold16, color: colors.green400, marginTop: 12 },
  modalOverlay: {
    alignItems: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.3)',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  modalCard: { backgroundColor: colors.white, borderRadius: 18, padding: 24, width: '100%' },
  modalTitle: {
    ...typography.titleSemiBold18,
    color: colors.gray800,
  },
  modalDescription: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 16 },
  modalActions: { flexDirection: 'row', gap: 14, marginTop: 20 },
  modalButton: {
    alignItems: 'center',
    borderRadius: radius.md,
    flex: 1,
    height: 48,
    justifyContent: 'center',
  },
  cancelButton: { backgroundColor: colors.gray200 },
  deleteButton: { backgroundColor: '#FA705B' },
  modalButtonText: { ...typography.bodySemiBold16, color: colors.white },
});
