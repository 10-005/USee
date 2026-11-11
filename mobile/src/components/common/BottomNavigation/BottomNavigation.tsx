import {
  Fragment,
  type ReactNode,
  useCallback,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
} from 'react';
import { Animated, Easing, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { BlurView } from '@react-native-community/blur';
import Svg, { Defs, LinearGradient, Rect, Stop } from 'react-native-svg';

import { colors, spacing, typography } from '../../../theme';

export type BottomNavigationItem<Key extends string = string> = {
  icon: (selected: boolean) => ReactNode;
  key: Key;
  label: string;
};

type BottomNavigationProps<Key extends string> = {
  backgroundBlurEnabled?: boolean;
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

export function BottomNavigation<Key extends string>({
  backgroundBlurEnabled = true,
  centerAction,
  items,
  onSelect,
  selectedKey,
}: BottomNavigationProps<Key>) {
  const glassGradientId = useId();
  const centerIndex = Math.ceil(items.length / 2);
  const [width, setWidth] = useState(0);
  const [blurPaused, setBlurPaused] = useState(false);
  const position = useRef(new Animated.Value(0)).current;
  const initialized = useRef(false);
  const trailingPosition = useRef(new Animated.Value(0)).current;
  const destination = useRef<number | null>(null);
  const animation = useRef<Animated.CompositeAnimation | null>(null);
  const itemWidth = (width - spacing[4] * 2 - (centerAction ? 50 : 0)) / items.length;
  const selectedIndex = items.findIndex(item => item.key === selectedKey);
  const centerSelected = Boolean(centerAction?.label && centerAction.selected);
  const hasCenterAction = Boolean(centerAction);
  const getTarget = useCallback(
    (index: number, center = false) =>
      center
        ? spacing[4] + itemWidth * centerIndex + 25 - 32
        : spacing[4] +
          itemWidth * (index + 0.5) +
          (hasCenterAction && index >= centerIndex ? 50 : 0) +
          (hasCenterAction && index === centerIndex - 1 ? -3 : 0) +
          (hasCenterAction && index === centerIndex ? 3 : 0) -
          32,
    [centerIndex, hasCenterAction, itemWidth],
  );
  const target = getTarget(selectedIndex, centerSelected);

  const moveSelection = useCallback(
    (nextTarget: number) => {
      if (width <= 0 || destination.current === nextTarget) {
        return;
      }
      destination.current = nextTarget;
      animation.current?.stop();
      if (!initialized.current) {
        position.setValue(nextTarget);
        trailingPosition.setValue(nextTarget);
        initialized.current = true;
        return;
      }
      // Continue from both live positions when a new tab interrupts the motion.
      setBlurPaused(true);
      animation.current = Animated.parallel([
        Animated.timing(position, {
          toValue: nextTarget,
          duration: 280,
          easing: Easing.bezier(0.2, 0.75, 0.25, 1),
          useNativeDriver: true,
          isInteraction: false,
        }),
        Animated.timing(trailingPosition, {
          toValue: nextTarget,
          duration: 380,
          easing: Easing.bezier(0.35, 0, 0.2, 1),
          useNativeDriver: true,
          isInteraction: false,
        }),
      ]);
      animation.current.start(({ finished }) => {
        if (finished) {
          setBlurPaused(false);
        }
      });
    },
    [position, trailingPosition, width],
  );

  useEffect(() => {
    moveSelection(target);
  }, [moveSelection, target]);

  useEffect(() => () => animation.current?.stop(), []);

  const { lensPosition, lensTransform } = useMemo(() => {
    const separation = Animated.subtract(position, trailingPosition);
    const center = Animated.divide(Animated.add(position, trailingPosition), 2);
    return {
      lensPosition: center,
      lensTransform: [
        { translateX: center },
        {
          scaleX: separation.interpolate({
            inputRange: [-160, 0, 160],
            outputRange: [1.5, 1, 1.5],
            extrapolate: 'clamp',
          }),
        },
        {
          scaleY: separation.interpolate({
            inputRange: [-160, 0, 160],
            outputRange: [0.94, 1, 0.94],
            extrapolate: 'clamp',
          }),
        },
      ],
    };
  }, [position, trailingPosition]);
  const iconScales = useMemo(
    () =>
      items.map((_, index) =>
        width > 0
          ? lensPosition.interpolate({
              inputRange: [
                getTarget(index) - itemWidth,
                getTarget(index),
                getTarget(index) + itemWidth,
              ],
              outputRange: [1, 1.12, 1],
              extrapolate: 'clamp',
            })
          : 1,
      ),
    [getTarget, itemWidth, items, lensPosition, width],
  );

  return (
    <View
      accessibilityRole="tablist"
      onLayout={event => setWidth(event.nativeEvent.layout.width)}
      style={styles.container}
    >
      <View pointerEvents="none" style={styles.background}>
        {backgroundBlurEnabled ? <BlurView
          {...Platform.select({ android: { enabled: true, autoUpdate: !blurPaused } })}
          blurAmount={16}
          blurType="light"
          reducedTransparencyFallbackColor="#F7F6F2"
          style={StyleSheet.absoluteFill}
        /> : null}
        <View style={styles.backgroundTint} />
      </View>
      {width > 0 && (selectedIndex >= 0 || centerSelected) ? (
        <Animated.View
          pointerEvents="none"
          style={[styles.selectionBackground, { transform: lensTransform }]}
        >
          <Svg width="100%" height="100%" style={StyleSheet.absoluteFill}>
            <Defs>
              <LinearGradient id={glassGradientId} x1="0%" y1="0%" x2="100%" y2="100%">
                <Stop offset="0" stopColor="#FFFFFF" stopOpacity={0.48} />
                <Stop offset="0.48" stopColor="#FFFFFF" stopOpacity={0.08} />
                <Stop offset="1" stopColor={colors.green100} stopOpacity={0.16} />
              </LinearGradient>
            </Defs>
            <Rect width="100%" height="100%" fill={`url(#${glassGradientId})`} />
          </Svg>
        </Animated.View>
      ) : null}
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
                  onPress={() => {
                    if (centerAction.label) {
                      moveSelection(
                        centerAction.selected
                          ? getTarget(selectedIndex)
                          : getTarget(centerIndex, true),
                      );
                    }
                    centerAction.onPress();
                  }}
                  style={({ pressed }) => [
                    centerAction.label ? styles.centerTab : styles.centerButton,
                    pressed && styles.pressed,
                  ]}
                >
                  {centerAction.label ? (
                    <>
                      <View style={styles.centerTabIcon}>
                        <View style={styles.animatedIcon}>{centerAction.icon}</View>
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
                    <View style={styles.animatedIcon}>{centerAction.icon}</View>
                  )}
                </Pressable>
              </View>
            ) : null}
            <View style={styles.itemSlot}>
              <Pressable
                accessibilityLabel={item.label}
                accessibilityRole="tab"
                accessibilityState={{ selected }}
                onPress={() => {
                  moveSelection(getTarget(index));
                  onSelect(item.key);
                }}
                style={({ pressed }) => [
                  styles.item,
                  centerAction && index === centerIndex - 1 ? styles.beforeCenterItem : null,
                  centerAction && index === centerIndex ? styles.afterCenterItem : null,
                  pressed && styles.pressed,
                ]}
              >
                <Animated.View style={[styles.icon, { transform: [{ scale: iconScales[index] }] }]}>
                  {item.icon(selected)}
                </Animated.View>
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
    borderRadius: 28,
    boxShadow: '0px 4px 6px rgba(0, 0, 0, 0.15)',
    flexDirection: 'row',
    height: 60,
    justifyContent: 'space-between',
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[4],
  },
  background: {
    ...StyleSheet.absoluteFill,
    borderRadius: 28,
    overflow: 'hidden',
    zIndex: 0,
  },
  backgroundTint: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(247, 246, 242, 0.6)',
  },
  itemSlot: {
    alignItems: 'center',
    flex: 1,
    justifyContent: 'center',
    minWidth: 0,
    zIndex: 2,
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
    backgroundColor: 'rgba(34, 197, 94, 0.06)',
    borderColor: 'rgba(255, 255, 255, 0.65)',
    borderWidth: 1,
    borderRadius: 24,
    height: 48,
    left: 0,
    position: 'absolute',
    top: 8,
    width: 64,
    overflow: 'hidden',
    zIndex: 1,
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
    zIndex: 2,
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
