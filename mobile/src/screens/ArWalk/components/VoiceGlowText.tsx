import { useEffect, useId, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedProps,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Defs, LinearGradient, Stop, Text as SvgText } from 'react-native-svg';

const MovingGradient = Animated.createAnimatedComponent(LinearGradient);

/** White letter faces with a subtle, moving green/purple outline. */
export function VoiceGlowText({ text }: { text: string }) {
  const gradientId = `voice-glow-${useId().replace(/:/g, '')}`;
  const [width, setWidth] = useState(0);
  const phase = useSharedValue(0);
  useEffect(() => {
    phase.value = withRepeat(withTiming(1, { duration: 4200, easing: Easing.linear }), -1, false);
    return () => cancelAnimation(phase);
  }, [phase]);
  const animatedProps = useAnimatedProps(() => {
    const angle = phase.value * Math.PI * 2;
    const x = Math.cos(angle) * 0.5;
    const y = Math.sin(angle) * 0.5;
    return { x1: 0.5 - x, y1: 0.5 - y, x2: 0.5 + x, y2: 0.5 + y };
  });

  // Fit the compact overlay while keeping the complete phrase accessible.
  let usedWidth = 0;
  let visibleText = '';
  for (const character of text) {
    usedWidth += character.charCodeAt(0) <= 255 ? 8 : 14;
    if (usedWidth > width - 24) {
      visibleText += '…';
      break;
    }
    visibleText += character;
  }

  return (
    <View
      accessible
      accessibilityLabel={text}
      onLayout={event => setWidth(event.nativeEvent.layout.width)}
      style={styles.container}
    >
      {width > 0 && (
        <Svg width={width} height={30} accessible={false}>
          <Defs>
            <MovingGradient id={gradientId} animatedProps={animatedProps}>
              <Stop offset="0" stopColor="#75E6B2" />
              <Stop offset="0.5" stopColor="#B8B4ED" />
              <Stop offset="1" stopColor="#BE8CEB" />
            </MovingGradient>
          </Defs>
          <SvgText
            x={width / 2}
            y={21}
            fontSize={14}
            fontWeight="400"
            textAnchor="middle"
            fill="none"
            stroke={`url(#${gradientId})`}
            strokeWidth={4}
            strokeOpacity={0.16}
            strokeLinejoin="round"
          >
            {visibleText}
          </SvgText>
          <SvgText
            x={width / 2}
            y={21}
            fontSize={14}
            fontWeight="400"
            textAnchor="middle"
            fill="none"
            stroke={`url(#${gradientId})`}
            strokeWidth={1.6}
            strokeOpacity={0.75}
            strokeLinejoin="round"
          >
            {visibleText}
          </SvgText>
          <SvgText
            x={width / 2}
            y={21}
            fontSize={14}
            fontWeight="400"
            textAnchor="middle"
            fill="#FFFFFF"
          >
            {visibleText}
          </SvgText>
        </Svg>
      )}
    </View>
  );
}

const styles = StyleSheet.create({ container: { width: '100%', height: 30 } });
