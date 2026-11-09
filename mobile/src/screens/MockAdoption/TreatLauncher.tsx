import { useEffect, useMemo, useRef, useState } from 'react';
import { Animated, AppState, Easing, Image, PanResponder, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { PetCategory } from './data';

type Props = {
  category: PetCategory;
  item?: 'treat' | 'toy';
  bottomOffset?: number;
  width: number;
  height: number;
  target: { x: number; y: number };
  disabled: boolean;
  dockHidden?: boolean;
  onBusyChange: (busy: boolean) => void;
  onLanded: (point: { x: number; y: number }) => void;
};

export function TreatLauncher(props: Props) {
  const insets = useSafeAreaInsets();
  const [phase, setPhase] = useState<'idle' | 'drag' | 'flight'>('idle');
  const phaseRef = useRef(phase);
  const latest = useRef(props);
  latest.current = props;
  const bottom = insets.bottom + 20 + (props.bottomOffset ?? 0);
  const position = useRef(new Animated.ValueXY()).current;
  const progress = useRef(new Animated.Value(0)).current;
  const point = useRef({ x: 0, y: 0 });
  const [flight, setFlight] = useState({ x: 0, y: 0, targetX: 0, targetY: 0 });
  const resetRef = useRef(() => {});
  resetRef.current = () => {
    progress.stopAnimation();
    phaseRef.current = 'idle';
    setPhase('idle');
    latest.current.onBusyChange(false);
  };
  const responder = useMemo(
    () =>
      PanResponder.create({
        onStartShouldSetPanResponder: () => !latest.current.disabled && phaseRef.current === 'idle',
        onPanResponderGrant: event => {
          const { locationX, locationY } = event.nativeEvent;
          point.current = {
            x: latest.current.width - 20 - 72 + locationX - 36,
            y: latest.current.height - bottom - 72 + locationY - 36,
          };
          if (latest.current.disabled) {
            return;
          }
          position.setValue(point.current);
          phaseRef.current = 'drag';
          setPhase('drag');
          latest.current.onBusyChange(true);
        },
        onPanResponderMove: (_event, gesture) => {
          if (phaseRef.current !== 'drag') {
            return;
          }
          position.setValue({ x: point.current.x + gesture.dx, y: point.current.y + gesture.dy });
        },
        onPanResponderRelease: (_event, gesture) => {
          if (phaseRef.current !== 'drag') {
            return;
          }
          if (gesture.dy > -40 || (gesture.vy > -0.25 && gesture.dy > -80)) {
            resetRef.current();
            return;
          }
          const current = latest.current;
          const target = current.target;
          setFlight({
            x: point.current.x + gesture.dx,
            y: point.current.y + gesture.dy,
            targetX: target.x - 36,
            targetY: target.y - 36,
          });
          phaseRef.current = 'flight';
          setPhase('flight');
          progress.setValue(0);
          Animated.timing(progress, {
            toValue: 1,
            duration: 700,
            easing: Easing.linear,
            useNativeDriver: true,
          }).start(({ finished }) => {
            if (!finished || phaseRef.current !== 'flight') {
              return;
            }
            phaseRef.current = 'idle';
            setPhase('idle');
            latest.current.onLanded({ x: target.x, y: target.y });
          });
        },
        onPanResponderTerminationRequest: () => false,
        onPanResponderTerminate: () => resetRef.current(),
      }),
    [bottom, position, progress],
  );
  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      if (state !== 'active') {
        if (phaseRef.current !== 'idle') {
          resetRef.current();
        }
      }
    });
    return () => {
      progress.stopAnimation();
      subscription.remove();
      latest.current.onBusyChange(false);
    };
  }, [progress]);
  const source =
    props.item === 'toy'
      ? require('../../assets/images/mock-adoption/img_dog_toy.png')
      : props.category === 'dog'
      ? require('../../assets/images/mock-adoption/img_dog_treats.png')
      : require('../../assets/images/mock-adoption/img_cat_treats.png');
  const samples = Array.from({ length: 21 }, (_, index) => index / 20);
  const translateY = progress.interpolate({
    inputRange: samples,
    outputRange: samples.map(
      t => flight.y + (flight.targetY - flight.y) * t - 4 * 100 * t * (1 - t),
    ),
  });
  return (
    <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
      <View
        {...responder.panHandlers}
        accessibilityLabel={
          props.item === 'toy' ? '잡아서 위로 던져 공놀이하기' : '잡아서 위로 던져 간식 주기'
        }
        pointerEvents={props.dockHidden ? 'none' : 'auto'}
        accessibilityElementsHidden={props.dockHidden}
        importantForAccessibility={props.dockHidden ? 'no-hide-descendants' : 'auto'}
        style={[styles.dock, { bottom }, props.dockHidden && styles.hidden]}
      >
        <Image
          source={source}
          resizeMode="contain"
          style={[styles.image, phase !== 'idle' && styles.hidden]}
        />
      </View>
      {phase !== 'idle' ? (
        <Animated.Image
          pointerEvents="none"
          source={source}
          resizeMode="contain"
          style={[
            styles.floating,
            {
              transform: [
                {
                  translateX:
                    phase === 'flight'
                      ? progress.interpolate({
                          inputRange: [0, 1],
                          outputRange: [flight.x, flight.targetX],
                        })
                      : position.x,
                },
                { translateY: phase === 'flight' ? translateY : position.y },
                {
                  scale:
                    phase === 'flight'
                      ? progress.interpolate({
                          inputRange: [0, 1],
                          outputRange: [0.8, props.item === 'toy' ? 0.55 : 0.35],
                        })
                      : 0.8,
                },
              ],
            },
          ]}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  dock: { position: 'absolute', right: 20, width: 72, height: 72 },
  image: { width: '100%', height: '100%' },
  hidden: { opacity: 0 },
  floating: { position: 'absolute', left: 0, top: 0, width: 72, height: 72 },
});
