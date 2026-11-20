import { useEffect, useState } from 'react';
import { useWindowDimensions } from 'react-native';
import {
  runOnJS,
  SensorType,
  useAnimatedReaction,
  useAnimatedSensor,
  useFrameCallback,
  useSharedValue,
} from 'react-native-reanimated';

import { accelerationEnergy, placeDog, projectDog, stepWalk } from './walkMath';
import type { Pose } from './walkMath';

const WALK_ENERGY = 1.05;

function hasOrientation(qw: number, qx: number, qy: number, qz: number) {
  'worklet';
  return qw * qw + qx * qx + qy * qy + qz * qz > 0.5;
}

export function useDogWalk() {
  const window = useWindowDimensions();
  const rotation = useAnimatedSensor(SensorType.ROTATION, { interval: 16 });
  const acceleration = useAnimatedSensor(SensorType.ACCELEROMETER, { interval: 16 });
  const width = useSharedValue(window.width);
  const height = useSharedValue(window.height);
  const user = useSharedValue<Pose>({ x: 0, z: 0, yaw: 0 });
  const dog = useSharedValue<Pose>({ x: 0, z: 0, yaw: 0 });
  const ready = useSharedValue(false);
  const energy = useSharedValue(0);
  const x = useSharedValue(window.width / 2);
  const y = useSharedValue(window.height * 0.62);
  const scale = useSharedValue(1);
  const phase = useSharedValue(0);
  const facing = useSharedValue(1);
  const opacity = useSharedValue(0);
  const walkingValue = useSharedValue(false);
  const [walking, setWalking] = useState(false);
  const [motionReady, setMotionReady] = useState(false);

  useEffect(() => {
    width.value = window.width;
    height.value = window.height;
  }, [height, width, window.height, window.width]);

  useAnimatedReaction(
    () => walkingValue.value,
    (isWalking, previous) => {
      if (isWalking !== previous) {
        runOnJS(setWalking)(isWalking);
      }
    },
  );

  useFrameCallback(frame => {
    if (width.value <= 0 || height.value <= 0) {
      return;
    }

    const sample = rotation.sensor.value;
    if (!hasOrientation(sample.qw, sample.qx, sample.qy, sample.qz)) {
      return;
    }

    const dt = frame.timeSincePreviousFrame == null ? 0.016 : frame.timeSincePreviousFrame / 1000;
    if (!ready.value) {
      ready.value = true;
      dog.value = placeDog(sample.yaw);
      runOnJS(setMotionReady)(true);
    }

    const motion = acceleration.sensor.value;
    energy.value = energy.value * 0.85 + accelerationEnergy(motion.x, motion.y, motion.z) * 0.15;
    const isWalking = energy.value > WALK_ENERGY;
    walkingValue.value = isWalking;

    const next = stepWalk({
      user: user.value,
      dog: dog.value,
      yaw: sample.yaw,
      walking: isWalking,
      dt,
    });
    user.value = next.user;
    dog.value = next.dog;

    const projected = projectDog({
      user: next.user,
      dog: next.dog,
      pitch: sample.pitch,
      width: width.value,
      height: height.value,
    });
    const follow = 0.35;
    x.value = x.value + (projected.x - x.value) * follow;
    y.value = y.value + (projected.y - y.value) * follow;
    scale.value = scale.value + (projected.scale - scale.value) * follow;
    facing.value = projected.facing;
    opacity.value = projected.visible ? 1 : 0.4;
    phase.value += dt * (next.speed > 0.2 ? 7.5 : 1.4);
  });

  return {
    x,
    y,
    scale,
    phase,
    facing,
    opacity,
    walking,
    motionReady,
  };
}
