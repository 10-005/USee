import { useIsFocused } from '@react-navigation/native';
import { useEffect, useRef, useState } from 'react';
import {
  Animated,
  AppState,
  Easing,
  Platform,
  requireNativeComponent,
  type ViewProps,
} from 'react-native';

type Props = ViewProps & {
  petId: string;
  active: boolean;
  returningFromWalk: boolean;
  onReady: () => void;
  onPetPress?: () => void;
};
const NativeRoomPet =
  Platform.OS === 'android'
    ? Animated.createAnimatedComponent(requireNativeComponent<Props>('RoomPetView'))
    : null;

export function RoomPet({
  petId,
  style,
  returnSequence = 0,
  entryOffset = { x: 0, y: 0 },
  onPress,
}: Pick<Props, 'petId' | 'style'> & {
  returnSequence?: number;
  entryOffset?: { x: number; y: number };
  onPress?: () => void;
}) {
  const focused = useIsFocused();
  const [foreground, setForeground] = useState(AppState.currentState === 'active');
  const completedReturn = useRef(0);
  const { x: entryX, y: entryY } = entryOffset;
  const returningFromWalk = returnSequence > completedReturn.current;
  const offset = useRef(
    new Animated.ValueXY(returningFromWalk ? entryOffset : { x: 0, y: 0 }),
  ).current;
  useEffect(() => {
    offset.setValue(returningFromWalk ? { x: entryX, y: entryY } : { x: 0, y: 0 });
    return () => offset.stopAnimation();
  }, [offset, focused, foreground, returnSequence, returningFromWalk, entryX, entryY]);
  useEffect(() => {
    const subscription = AppState.addEventListener('change', state =>
      setForeground(state === 'active'),
    );
    return () => subscription.remove();
  }, []);
  if (!NativeRoomPet || !focused || !foreground) {
    return null;
  }
  const onReady = () => {
    if (!returningFromWalk) {
      return;
    }
    Animated.timing(offset, {
      toValue: { x: 0, y: 0 },
      duration: 3000,
      easing: Easing.linear,
      useNativeDriver: true,
    }).start(({ finished }) => {
      if (finished) {
        completedReturn.current = returnSequence;
      }
    });
  };
  return (
    <NativeRoomPet
      petId={petId}
      active
      returningFromWalk={returningFromWalk}
      onReady={onReady}
      onPetPress={onPress}
      accessibilityRole="button"
      accessibilityLabel="반려동물 상태 보기"
      style={[style, { transform: offset.getTranslateTransform() }]}
    />
  );
}
