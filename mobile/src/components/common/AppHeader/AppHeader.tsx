import type { ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import LeftArrowIcon from '../../../assets/icons/common/left-arrow.svg';
import { colors, spacing, typography } from '../../../theme';

type AppHeaderProps = {
  onBackPress?: () => void;
  rightAction?: ReactNode;
  rightActionWidth?: number;
  title: string;
};

export function AppHeader({ onBackPress, rightAction, rightActionWidth, title }: AppHeaderProps) {
  return (
    <View style={styles.container}>
      <View
        style={[
          styles.side,
          rightAction ? styles.leftWideSide : null,
          rightAction && rightActionWidth ? { width: rightActionWidth } : null,
        ]}
      >
        {onBackPress ? (
          <Pressable
            accessibilityLabel="뒤로 가기"
            accessibilityRole="button"
            hitSlop={10}
            onPress={onBackPress}
            style={({ pressed }) => [styles.backButton, pressed && styles.pressed]}
          >
            <LeftArrowIcon height={24} width={24} />
          </Pressable>
        ) : null}
      </View>
      <Text numberOfLines={1} style={styles.title}>
        {title}
      </Text>
      <View
        style={[
          styles.side,
          rightAction ? styles.rightWideSide : null,
          rightAction && rightActionWidth ? { width: rightActionWidth } : null,
        ]}
      >
        {rightAction}
      </View>
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
  leftWideSide: {
    alignItems: 'flex-start',
    width: 72,
  },
  rightWideSide: {
    width: 72,
  },
  backButton: {
    alignItems: 'center',
    height: 40,
    justifyContent: 'center',
    width: 40,
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
