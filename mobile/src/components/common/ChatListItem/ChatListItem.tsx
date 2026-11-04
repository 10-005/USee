import { Image, Pressable, StyleSheet, Text, View, type ImageSourcePropType } from 'react-native';

import { colors, radius, spacing, typography } from '../../../theme';

type ChatListItemProps = {
  avatarSource: ImageSourcePropType;
  lastMessage: string;
  name: string;
  onPress?: () => void;
  time: string;
  unreadCount?: number;
};

export function ChatListItem({
  avatarSource,
  lastMessage,
  name,
  onPress,
  time,
  unreadCount = 0,
}: ChatListItemProps) {
  const accessibilityLabel = [
    name,
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
          <Text numberOfLines={1} style={styles.name}>
            {name}
          </Text>
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
    gap: spacing[8],
    minHeight: 76,
    paddingVertical: spacing[4],
  },
  avatar: {
    backgroundColor: colors.gray200,
    borderRadius: 30,
    height: 60,
    width: 60,
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
  name: {
    ...typography.titleSemiBold18,
    color: colors.gray600,
    flex: 1,
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
    ...typography.bodyRegular16,
    color: colors.gray500,
    flex: 1,
  },
  unreadBadge: {
    alignItems: 'center',
    borderColor: colors.green100,
    borderRadius: radius.pill,
    borderWidth: 1,
    justifyContent: 'center',
    minHeight: 20,
    minWidth: 20,
    paddingHorizontal: spacing[2],
  },
  unreadLabel: {
    ...typography.captionRegular10,
    color: colors.green100,
  },
  pressed: {
    opacity: 0.75,
  },
});
