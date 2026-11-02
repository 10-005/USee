import { Fragment, type ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { colors, radius, shadows, spacing, typography } from '../../../theme';

export type BottomNavigationItem<Key extends string = string> = {
  icon: (selected: boolean) => ReactNode;
  key: Key;
  label: string;
};

type BottomNavigationProps<Key extends string> = {
  centerAction?: {
    accessibilityLabel: string;
    icon: ReactNode;
    onPress: () => void;
  };
  items: readonly BottomNavigationItem<Key>[];
  onSelect: (key: Key) => void;
  selectedKey: Key;
};

export function BottomNavigation<Key extends string>({
  centerAction,
  items,
  onSelect,
  selectedKey,
}: BottomNavigationProps<Key>) {
  const centerIndex = Math.ceil(items.length / 2);

  return (
    <View accessibilityRole="tablist" style={styles.container}>
      {items.map((item, index) => {
        const selected = item.key === selectedKey;

        return (
          <Fragment key={item.key}>
            {centerAction && index === centerIndex ? (
              <View style={styles.itemSlot}>
                <Pressable
                  accessibilityLabel={centerAction.accessibilityLabel}
                  accessibilityRole="button"
                  onPress={centerAction.onPress}
                  style={({ pressed }) => [styles.centerButton, pressed && styles.pressed]}
                >
                  {centerAction.icon}
                </Pressable>
              </View>
            ) : null}
            <View style={styles.itemSlot}>
              <Pressable
                accessibilityLabel={item.label}
                accessibilityRole="tab"
                accessibilityState={{ selected }}
                onPress={() => onSelect(item.key)}
                style={({ pressed }) => [styles.item, pressed && styles.pressed]}
              >
                <View style={styles.icon}>{item.icon(selected)}</View>
                <Text style={[styles.label, selected && styles.selectedLabel]}>{item.label}</Text>
              </Pressable>
            </View>
          </Fragment>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    ...shadows.level2,
    alignItems: 'center',
    backgroundColor: 'rgba(247, 246, 242, 0.96)',
    borderRadius: radius.pill,
    flexDirection: 'row',
    height: 60,
    justifyContent: 'space-around',
    paddingHorizontal: spacing[8],
    paddingVertical: spacing[4],
  },
  itemSlot: {
    alignItems: 'center',
    flex: 1,
    justifyContent: 'center',
  },
  item: {
    alignItems: 'center',
    gap: spacing[1],
    justifyContent: 'center',
    minHeight: 44,
    minWidth: 56,
  },
  icon: {
    alignItems: 'center',
    height: 24,
    justifyContent: 'center',
    width: 24,
  },
  label: {
    ...typography.captionSemiBold12,
    color: colors.gray400,
  },
  selectedLabel: {
    color: colors.green100,
  },
  centerButton: {
    alignItems: 'center',
    backgroundColor: colors.green400,
    borderRadius: 25,
    height: 50,
    justifyContent: 'center',
    position: 'absolute',
    top: -35,
    width: 50,
    zIndex: 1,
  },
  pressed: {
    opacity: 0.7,
  },
});
