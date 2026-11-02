import { Pressable, StyleSheet, Text, View } from 'react-native';

import { colors, spacing, typography } from '../../../theme';

type AppHeaderProps = {
  onBackPress?: () => void;
  title: string;
};

export function AppHeader({ onBackPress, title }: AppHeaderProps) {
  return (
    <View style={styles.container}>
      <View style={styles.side}>
        {onBackPress ? (
          <Pressable
            accessibilityLabel="뒤로 가기"
            accessibilityRole="button"
            hitSlop={10}
            onPress={onBackPress}
            style={({ pressed }) => [styles.backButton, pressed && styles.pressed]}
          >
            <View style={styles.chevron} />
          </Pressable>
        ) : null}
      </View>
      <Text numberOfLines={1} style={styles.title}>
        {title}
      </Text>
      <View style={styles.side} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    flexDirection: 'row',
    height: 56,
    paddingHorizontal: spacing[4],
  },
  side: {
    alignItems: 'center',
    justifyContent: 'center',
    width: 40,
  },
  backButton: {
    alignItems: 'center',
    height: 40,
    justifyContent: 'center',
    width: 40,
  },
  chevron: {
    borderBottomColor: colors.black,
    borderBottomWidth: 2,
    borderLeftColor: colors.black,
    borderLeftWidth: 2,
    height: 10,
    transform: [{ rotate: '45deg' }],
    width: 10,
  },
  pressed: {
    opacity: 0.5,
  },
  title: {
    ...typography.titleSemiBold18,
    color: colors.black,
    flex: 1,
    textAlign: 'center',
  },
});
