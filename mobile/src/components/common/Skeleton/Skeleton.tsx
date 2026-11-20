import { type ReactNode, useEffect, useRef } from 'react';
import { Animated, type StyleProp, StyleSheet, View, type ViewStyle } from 'react-native';

import { colors } from '../../../theme';

export function SkeletonGroup({
  accessibilityLabel,
  children,
  style,
}: {
  accessibilityLabel: string;
  children: ReactNode;
  style?: StyleProp<ViewStyle>;
}) {
  const opacity = useRef(new Animated.Value(0.48)).current;

  useEffect(() => {
    const animation = Animated.loop(
      Animated.sequence([
        Animated.timing(opacity, {
          duration: 700,
          toValue: 1,
          useNativeDriver: true,
        }),
        Animated.timing(opacity, {
          duration: 700,
          toValue: 0.48,
          useNativeDriver: true,
        }),
      ]),
    );

    animation.start();
    return () => animation.stop();
  }, [opacity]);

  return (
    <Animated.View
      accessibilityLabel={accessibilityLabel}
      accessibilityRole="progressbar"
      accessibilityState={{ busy: true }}
      style={[style, { opacity }]}
    >
      {children}
    </Animated.View>
  );
}

export function SkeletonBlock({ style }: { style?: StyleProp<ViewStyle> }) {
  return <View style={[styles.block, style]} />;
}

const styles = StyleSheet.create({
  block: {
    backgroundColor: colors.gray200,
    borderRadius: 8,
  },
});
