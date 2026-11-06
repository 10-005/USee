import { useEffect, useId } from 'react';
import { Image, View } from 'react-native';
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedProps,
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import Svg, {
  Circle,
  Defs,
  Image as SvgImage,
  LinearGradient,
  Mask,
  Rect,
  Stop,
} from 'react-native-svg';
import { styles } from './VoiceMicrophone.styles';

const microphone = require('../../../assets/icons/ic-microphone-on.png');
const MovingGradient = Animated.createAnimatedComponent(LinearGradient);

export function VoiceMicrophone({ listening }: { listening: boolean }) {
  const id = useId().replace(/:/g, '');
  const phase = useSharedValue(0);
  useEffect(() => {
    phase.value = 0;
    if (listening) {
      phase.value = withRepeat(withTiming(1, { duration: 1800, easing: Easing.linear }), -1, false);
    }
    return () => cancelAnimation(phase);
  }, [listening, phase]);
  const gradientProps = useAnimatedProps(() => {
    const angle = phase.value * Math.PI * 2;
    const x = Math.cos(angle) * 0.5;
    const y = Math.sin(angle) * 0.5;
    return { x1: 0.5 - x, y1: 0.5 - y, x2: 0.5 + x, y2: 0.5 + y };
  });
  const pulseStyle = useAnimatedStyle(() => {
    const pulse = (1 - Math.cos(phase.value * Math.PI * 2)) / 2;
    return { opacity: 0.65 + pulse * 0.35, transform: [{ scale: 0.78 + pulse * 0.22 }] };
  });
  return (
    <View pointerEvents="none" style={[styles.container, !listening && styles.idle]}>
      {listening ? (
        <>
          <Animated.View style={[styles.glow, pulseStyle]}>
            <Svg width={60} height={60}>
              <Defs>
                <MovingGradient id={`orb-${id}`} animatedProps={gradientProps}>
                  <Stop offset="0" stopColor="#388BFF" />
                  <Stop offset="0.5" stopColor="#AD5CFF" />
                  <Stop offset="1" stopColor="#FFFFFF" />
                </MovingGradient>
              </Defs>
              <Circle cx={30} cy={30} r={30} fill={`url(#orb-${id})`} />
            </Svg>
          </Animated.View>
          <Svg width={30} height={30}>
            <Defs>
              <MovingGradient id={`line-${id}`} animatedProps={gradientProps}>
                <Stop offset="0" stopColor="#AD5CFF" />
                <Stop offset="1" stopColor="#388BFF" />
              </MovingGradient>
              <Mask
                id={`mic-${id}`}
                x={0}
                y={0}
                width={30}
                height={30}
                maskUnits="userSpaceOnUse"
                maskType="alpha"
              >
                <SvgImage href={microphone} x={0} y={0} width={30} height={30} />
              </Mask>
            </Defs>
            <Rect width={30} height={30} fill={`url(#line-${id})`} mask={`url(#mic-${id})`} />
          </Svg>
        </>
      ) : (
        <Image source={microphone} style={styles.icon} />
      )}
    </View>
  );
}
