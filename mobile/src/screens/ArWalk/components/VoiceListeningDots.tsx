import { useEffect, useRef } from 'react';
import { Animated, StyleSheet, View } from 'react-native';

export function VoiceListeningDots() {
  const dots = useRef([
    new Animated.Value(0),
    new Animated.Value(0),
    new Animated.Value(0),
  ]).current;

  useEffect(() => {
    const animation = Animated.loop(
      Animated.sequence([
        ...dots.map(dot =>
          Animated.sequence([
            Animated.timing(dot, { toValue: -4, duration: 160, useNativeDriver: true }),
            Animated.timing(dot, { toValue: 0, duration: 160, useNativeDriver: true }),
          ]),
        ),
        Animated.delay(200),
      ]),
    );
    animation.start();
    return () => {
      animation.stop();
      dots.forEach(dot => dot.setValue(0));
    };
  }, [dots]);

  return (
    <View accessibilityLabel="음성 인식 중" style={styles.dots}>
      {dots.map((dot, index) => (
        <Animated.Text
          key={index}
          style={[
            styles.dot,
            index % 2 === 0 ? styles.greenGlow : styles.purpleGlow,
            {
              transform: [{ translateY: dot }],
            },
          ]}
        >
          .
        </Animated.Text>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  greenGlow: { textShadowColor: '#75E6B2' },
  purpleGlow: { textShadowColor: '#BE8CEB' },
  dots: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', height: 24 },
  dot: {
    color: '#FFFFFF',
    fontSize: 20,
    lineHeight: 24,
    fontWeight: '400',
    width: 9,
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 4,
    textAlign: 'center',
  },
});
