import { Image, Pressable, StyleSheet, Text, View, type ImageSourcePropType } from 'react-native';

import { colors, radius, spacing, typography } from '../../../theme';
import BadgeIcon from '../../../assets/icons/common/badge.svg';

type ChatListItemProps = {
  avatarSource: ImageSourcePropType;
  lastMessage: string;
  name: string;
  isShelter?: boolean;
  onPress?: () => void;
  time: string;
  unreadCount?: number;
};

export function ChatListItem({
  avatarSource,
  lastMessage,
  name,
  isShelter = false,
  onPress,
  time,
  unreadCount = 0,
}: ChatListItemProps) {
  const accessibilityLabel = [
    name,
    isShelter ? '보호소' : null,
    lastMessage,
    time,
    unreadCount > 0 ? `읽지 않은 메시지 ${unreadCount}개` : null,
  ]
    .filter(Boolean)
    .join(', ');

  const content = (
    <>
      <Image source={avatarSource} style={styles.avatar} />
      <View style={styles.content}>
        <View style={styles.header}>
          <View style={styles.nameRow}>
            <Text numberOfLines={1} style={styles.name}>
              {name}
            </Text>
            {isShelter && <BadgeIcon height={18} width={18} />}
          </View>
          <Text style={styles.time}>{time}</Text>
        </View>
        <View style={styles.messageRow}>
          <Text numberOfLines={1} style={styles.message}>
            {lastMessage}
          </Text>
          {unreadCount > 0 ? (
            <View style={styles.unreadBadge}>
              <Text style={styles.unreadLabel}>{unreadCount > 99 ? '99+' : unreadCount}</Text>
            </View>
          ) : null}
        </View>
      </View>
    </>
  );

  return onPress ? (
    <Pressable
      accessibilityLabel={accessibilityLabel}
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.container, pressed && styles.pressed]}
    >
      {content}
    </Pressable>
  ) : (
    <View accessible accessibilityLabel={accessibilityLabel} style={styles.container}>
      {content}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[6],
    minHeight: 64,
    paddingVertical: spacing[4],
  },
  avatar: {
    backgroundColor: colors.gray200,
    borderRadius: 24,
    height: 48,
    width: 48,
  },
  content: {
    flex: 1,
    gap: spacing[2],
  },
  header: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[4],
  },
  nameRow: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 4 },
  name: {
    ...typography.bodySemiBold16,
    color: colors.gray600,
    flexShrink: 1,
  },
  time: {
    ...typography.captionRegular10,
    color: colors.gray300,
  },
  messageRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[4],
  },
  message: {
    ...typography.bodyRegular14,
    color: colors.gray500,
    flex: 1,
  },
  unreadBadge: {
    alignItems: 'center',
    backgroundColor: colors.gray10,
    borderColor: colors.green100,
    borderRadius: radius.pill,
    borderWidth: 1,
    flexShrink: 0,
    height: 20,
    justifyContent: 'center',
    minWidth: 22,
    paddingHorizontal: spacing[3],
  },
  unreadLabel: {
    ...typography.bodyRegular14,
    color: colors.green100,
    includeFontPadding: false,
    textAlign: 'center',
  },
  pressed: {
    opacity: 0.75,
  },
});
