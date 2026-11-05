import { useEffect, useId, useRef } from 'react';
import { Animated, Easing, Image, View } from 'react-native';
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

const microphoneOn = require('../../../assets/icons/ic-microphone-on.png');
const microphoneOff = require('../../../assets/icons/ic-microphone-off.png');

export function VoiceMicrophone({ enabled, hearing }: { enabled: boolean; hearing: boolean }) {
  const id = useId().replace(/:/g, '');
  const phase = useRef(new Animated.Value(0)).current;
  const reacting = enabled && hearing;
  useEffect(() => {
    phase.setValue(0);
    if (!reacting) {
      return;
    }
    const timing = (toValue: number) =>
      Animated.timing(phase, {
        toValue,
        duration: 900,
        easing: Easing.inOut(Easing.sin),
        useNativeDriver: true,
      });
    const animation = Animated.loop(Animated.sequence([timing(1), timing(0)]));
    animation.start();
    return () => animation.stop();
  }, [phase, reacting]);
  return (
    <View pointerEvents="none" style={styles.container}>
      {reacting && (
        <Animated.View
          style={[
            styles.glow,
            {
              transform: [
                { scale: phase.interpolate({ inputRange: [0, 1], outputRange: [0.78, 1.15] }) },
                {
                  rotate: phase.interpolate({
                    inputRange: [0, 1],
                    outputRange: ['-30deg', '40deg'],
                  }),
                },
              ],
            },
          ]}
        >
          <Svg width={32} height={32}>
            <Defs>
              <LinearGradient id={`orb-${id}`} x1="0" y1="0" x2="1" y2="1">
                <Stop offset="0" stopColor="#AD5CFF" />
                <Stop offset="1" stopColor="#388BFF" />
              </LinearGradient>
            </Defs>
            <Circle cx={16} cy={16} r={16} fill={`url(#orb-${id})`} />
          </Svg>
        </Animated.View>
      )}
      {reacting ? (
        <Svg width={40} height={40}>
          <Defs>
            <LinearGradient id={`line-${id}`} x1="0" y1="0" x2="1" y2="1">
              <Stop offset="0" stopColor="#AD5CFF" />
              <Stop offset="1" stopColor="#388BFF" />
            </LinearGradient>
            <Mask id={`mic-${id}`} x={0} y={0} width={40} height={40} maskUnits="userSpaceOnUse">
              <SvgImage href={microphoneOn} x={0} y={0} width={40} height={40} />
            </Mask>
          </Defs>
          <Rect width={40} height={40} fill={`url(#line-${id})`} mask={`url(#mic-${id})`} />
        </Svg>
      ) : (
        <Image source={enabled ? microphoneOn : microphoneOff} style={styles.icon} />
      )}
    </View>
  );
}
