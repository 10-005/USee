import { Fragment, type ReactNode, useEffect, useRef } from 'react';
import { Animated, Pressable, StyleSheet, Text, View } from 'react-native';

import { colors, spacing, typography } from '../../../theme';

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
    label?: string;
    selected?: boolean;
  };
  items: readonly BottomNavigationItem<Key>[];
  onSelect: (key: Key) => void;
  selectedKey: Key;
};

function AnimatedSelectionIcon({
  children,
  selected,
}: {
  children: ReactNode;
  selected: boolean;
}) {
  const progress = useRef(new Animated.Value(1)).current;
  const mounted = useRef(false);

  useEffect(() => {
    if (!mounted.current) {
      mounted.current = true;
      return;
    }

    if (!selected) {
      return;
    }

    progress.stopAnimation();
    progress.setValue(0);
    Animated.spring(progress, {
      toValue: 1,
      damping: 8,
      stiffness: 280,
      mass: 0.55,
      useNativeDriver: true,
    }).start();
  }, [progress, selected]);

  const scale = progress.interpolate({
    inputRange: [0, 0.55, 1],
    outputRange: [0.82, 1.08, 1],
    extrapolate: 'clamp',
  });
  const translateY = progress.interpolate({
    inputRange: [0, 0.55, 1],
    outputRange: [2, -1, 0],
    extrapolate: 'clamp',
  });

  return (
    <Animated.View
      style={[styles.animatedIcon, { transform: [{ translateY }, { scale }] }]}
    >
      {children}
    </Animated.View>
  );
}

function AnimatedSelectionBackground({ selected }: { selected: boolean }) {
  const progress = useRef(new Animated.Value(selected ? 1 : 0)).current;

  useEffect(() => {
    const animation = selected
      ? Animated.spring(progress, {
          toValue: 1,
          damping: 10,
          stiffness: 240,
          mass: 0.55,
          useNativeDriver: true,
        })
      : Animated.timing(progress, {
          toValue: 0,
          duration: 120,
          useNativeDriver: true,
        });

    animation.start();
  }, [progress, selected]);

  const scale = progress.interpolate({
    inputRange: [0, 1],
    outputRange: [0.65, 1],
  });

  return (
    <Animated.View
      pointerEvents="none"
      style={[
        styles.selectionBackground,
        { opacity: progress, transform: [{ scale }] },
      ]}
    />
  );
}

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
              <View style={styles.centerSlot}>
                <Pressable
                  accessibilityLabel={centerAction.accessibilityLabel}
                  accessibilityRole={centerAction.selected === undefined ? 'button' : 'tab'}
                  accessibilityState={
                    centerAction.selected === undefined
                      ? undefined
                      : { selected: centerAction.selected }
                  }
                  onPress={centerAction.onPress}
                  style={({ pressed }) => [
                    centerAction.label ? styles.centerTab : styles.centerButton,
                    pressed && styles.pressed,
                  ]}
                >
                  {centerAction.label ? (
                    <>
                      <AnimatedSelectionBackground
                        selected={Boolean(centerAction.selected)}
                      />
                      <View style={styles.centerTabIcon}>
                        <AnimatedSelectionIcon
                          selected={Boolean(centerAction.selected)}
                        >
                          {centerAction.icon}
                        </AnimatedSelectionIcon>
                      </View>
                      <Text
                        adjustsFontSizeToFit
                        minimumFontScale={0.8}
                        numberOfLines={1}
                        style={[styles.label, centerAction.selected && styles.selectedLabel]}
                      >
                        {centerAction.label}
                      </Text>
                    </>
                  ) : (
                    <AnimatedSelectionIcon
                      selected={Boolean(centerAction.selected)}
                    >
                      {centerAction.icon}
                    </AnimatedSelectionIcon>
                  )}
                </Pressable>
              </View>
            ) : null}
            <View style={styles.itemSlot}>
              <Pressable
                accessibilityLabel={item.label}
                accessibilityRole="tab"
                accessibilityState={{ selected }}
                onPress={() => onSelect(item.key)}
                style={({ pressed }) => [
                  styles.item,
                  centerAction && index === centerIndex - 1
                    ? styles.beforeCenterItem
                    : null,
                  centerAction && index === centerIndex
                    ? styles.afterCenterItem
                    : null,
                  pressed && styles.pressed,
                ]}
              >
                <AnimatedSelectionBackground selected={selected} />
                <View style={styles.icon}>
                  <AnimatedSelectionIcon selected={selected}>
                    {item.icon(selected)}
                  </AnimatedSelectionIcon>
                </View>
                <Text
                  adjustsFontSizeToFit
                  minimumFontScale={0.8}
                  numberOfLines={1}
                  style={[styles.label, selected && styles.selectedLabel]}
                >
                  {item.label}
                </Text>
              </Pressable>
            </View>
          </Fragment>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  animatedIcon: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  container: {
    alignItems: 'center',
    backgroundColor: 'rgba(247, 246, 242, 0.6)',
    borderRadius: 28,
    boxShadow: '0px 4px 6px rgba(0, 0, 0, 0.15)',
    flexDirection: 'row',
    height: 60,
    justifyContent: 'space-between',
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[4],
  },
  itemSlot: {
    alignItems: 'center',
    flex: 1,
    justifyContent: 'center',
    minWidth: 0,
  },
  item: {
    alignItems: 'center',
    gap: spacing[2],
    height: 48,
    justifyContent: 'center',
    transform: [{ translateY: 2 }],
    width: '100%',
  },
  beforeCenterItem: {
    transform: [{ translateX: -3 }, { translateY: 2 }],
  },
  afterCenterItem: {
    transform: [{ translateX: 3 }, { translateY: 2 }],
  },
  icon: {
    alignItems: 'center',
    height: 24,
    justifyContent: 'center',
    width: 24,
  },
  label: {
    ...typography.captionSemiBold12,
    color: '#777671',
    textAlign: 'center',
    width: '100%',
  },
  selectedLabel: {
    color: colors.green100,
  },
  selectionBackground: {
    backgroundColor: 'rgba(34, 197, 94, 0.14)',
    borderRadius: 24,
    height: 48,
    left: '50%',
    marginLeft: -34,
    position: 'absolute',
    top: 0,
    width: 68,
  },
  centerButton: {
    alignItems: 'center',
    backgroundColor: colors.green400,
    borderRadius: 25,
    height: 50,
    justifyContent: 'center',
    transform: [{ translateY: 2 }],
    width: 50,
  },
  centerSlot: {
    alignItems: 'center',
    justifyContent: 'center',
    width: 50,
  },
  centerTab: {
    alignItems: 'center',
    gap: spacing[2],
    height: 48,
    justifyContent: 'center',
    transform: [{ translateY: 2 }],
  },
  centerTabIcon: {
    alignItems: 'center',
    backgroundColor: colors.green400,
    borderRadius: 18,
    height: 36,
    justifyContent: 'center',
    width: 36,
  },
  pressed: {
    opacity: 0.7,
  },
});
