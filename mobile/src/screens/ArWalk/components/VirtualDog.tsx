import { StyleSheet } from 'react-native';
import Animated, { useAnimatedStyle } from 'react-native-reanimated';
import type { SharedValue } from 'react-native-reanimated';

type VirtualDogProps = {
  x: SharedValue<number>;
  y: SharedValue<number>;
  scale: SharedValue<number>;
  phase: SharedValue<number>;
  facing: SharedValue<number>;
  opacity: SharedValue<number>;
};

const DOG_WIDTH = 220;
const DOG_HEIGHT = 192;
const WALK_FRAMES = [
  require('../../../assets/images/dog/walk-3.png'),
  require('../../../assets/images/dog/walk-1.png'),
  require('../../../assets/images/dog/walk-2.png'),
  require('../../../assets/images/dog/walk-1.png'),
];

type WalkFrameProps = {
  source: number;
  index: number;
  phase: SharedValue<number>;
};

function WalkFrame({ source, index, phase }: WalkFrameProps) {
  const style = useAnimatedStyle(() => {
    const current = Math.abs(Math.floor(phase.value * 4)) % WALK_FRAMES.length;
    return { opacity: current === index ? 1 : 0 };
  });

  return (
    <Animated.Image
      fadeDuration={0}
      resizeMode="contain"
      source={source}
      style={[styles.frame, style]}
    />
  );
}

export function VirtualDog({ x, y, scale, phase, facing, opacity }: VirtualDogProps) {
  const bodyStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
    transform: [
      { translateX: x.value - DOG_WIDTH / 2 },
      { translateY: y.value - DOG_HEIGHT },
      { scale: scale.value },
      { scaleX: facing.value },
    ],
  }));

  return (
    <Animated.View pointerEvents="none" style={[styles.dog, bodyStyle]}>
      {WALK_FRAMES.map((source, index) => (
        <WalkFrame key={index} index={index} phase={phase} source={source} />
      ))}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  dog: {
    height: DOG_HEIGHT,
    left: 0,
    position: 'absolute',
    top: 0,
    width: DOG_WIDTH,
  },
  frame: {
    height: DOG_HEIGHT,
    left: 0,
    position: 'absolute',
    top: 0,
    width: DOG_WIDTH,
  },
});
