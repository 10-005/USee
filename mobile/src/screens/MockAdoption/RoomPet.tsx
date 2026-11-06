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
  careMotion?: string;
  bathing?: boolean;
  sceneOpacity?: number;
  returningFromWalk: boolean;
  onReady: () => void;
  onPetPress?: () => void;
  onHidden?: () => void;
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
  leaving = false,
  onHidden,
  care,
  onConsumed,
  onCareComplete,
  bathing = false,
  sceneOpacity = 1,
  onFetchPhase,
}: Pick<Props, 'petId' | 'style'> & {
  returnSequence?: number;
  bathing?: boolean;
  sceneOpacity?: number | Animated.Value;
  care?: { kind: 'food' | 'water' | 'treat' | 'fetch'; offset: { x: number; y: number } } | null;
  onFetchPhase?: (phase: 'carry' | 'drop' | 'cancel') => void;
  onConsumed?: () => void;
  onCareComplete?: () => void;
  entryOffset?: { x: number; y: number };
  onPress?: () => void;
  leaving?: boolean;
  onHidden?: () => void;
}) {
  const focused = useIsFocused();
  const [careMotion, setCareMotion] = useState('');
  const callbacks = useRef({ onConsumed, onCareComplete, onFetchPhase });
  callbacks.current = { onConsumed, onCareComplete, onFetchPhase };
  const [foreground, setForeground] = useState(AppState.currentState === 'active');
  const completedReturn = useRef(0);
  const { x: entryX, y: entryY } = entryOffset;
  const latestEntry = useRef(entryOffset);
  latestEntry.current = { x: entryX, y: entryY };
  const startedReturn = useRef<number | null>(null);
  const returningFromWalk = returnSequence > completedReturn.current;
  const offset = useRef(
    new Animated.ValueXY(returningFromWalk ? entryOffset : { x: 0, y: 0 }),
  ).current;
  useEffect(() => {
    if (!care || !focused || !foreground) {
      return;
    }
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    if (care.kind === 'fetch') {
      setCareMotion('fetchRun');
      Animated.timing(offset, {
        toValue: care.offset,
        duration: 1400,
        easing: Easing.linear,
        useNativeDriver: true,
      }).start(({ finished }) => {
        if (!finished || cancelled) {return;}
        setCareMotion('fetchPick');
        timer = setTimeout(() => {
          if (cancelled) {return;}
          callbacks.current.onFetchPhase?.('carry');
          setCareMotion('fetchCarry');
          Animated.timing(offset, {
            toValue: { x: 0, y: 0 },
            duration: 1600,
            easing: Easing.linear,
            useNativeDriver: true,
          }).start(({ finished: returned }) => {
            if (!returned || cancelled) {return;}
            setCareMotion('fetchDrop');
            callbacks.current.onFetchPhase?.('drop');
            timer = setTimeout(() => {
              if (cancelled) {return;}
              setCareMotion('');
              callbacks.current.onCareComplete?.();
            }, 700);
          });
        }, 800);
      });
      return () => {
        cancelled = true;
        clearTimeout(timer);
        offset.stopAnimation();
        offset.setValue({ x: 0, y: 0 });
        setCareMotion('');
        callbacks.current.onFetchPhase?.('cancel');
      };
    }
    if (care.kind === 'treat') {
      setCareMotion('treat');
      timer = setTimeout(() => {
        setCareMotion('');
        callbacks.current.onCareComplete?.();
      }, 2000);
      return () => {
        clearTimeout(timer);
        setCareMotion('');
      };
    }
    setCareMotion('approach');
    Animated.timing(offset, {
      toValue: care.offset,
      duration: 1200,
      easing: Easing.linear,
      useNativeDriver: true,
    }).start(({ finished }) => {
      if (!finished || cancelled) {
        return;
      }
      setCareMotion(care.kind === 'food' ? 'eat' : 'drink');
      timer = setTimeout(() => {
        callbacks.current.onConsumed?.();
        setCareMotion('return');
        Animated.timing(offset, {
          toValue: { x: 0, y: 0 },
          duration: 1200,
          easing: Easing.linear,
          useNativeDriver: true,
        }).start(({ finished: returned }) => {
          if (!returned || cancelled) {
            return;
          }
          setCareMotion('');
          callbacks.current.onCareComplete?.();
        });
      }, 2000);
    });
    return () => {
      cancelled = true;
      clearTimeout(timer);
      offset.stopAnimation();
      offset.setValue({ x: 0, y: 0 });
      setCareMotion('');
    };
  }, [care, focused, foreground, offset]);
  useEffect(() => {
    if (leaving && (!NativeRoomPet || !focused || !foreground)) {
      onHidden?.();
    }
  }, [leaving, focused, foreground, onHidden]);
  useEffect(() => {
    offset.setValue(returningFromWalk ? latestEntry.current : { x: 0, y: 0 });
    return () => {
      offset.stopAnimation();
      startedReturn.current = null;
    };
  }, [offset, focused, foreground, returnSequence, returningFromWalk]);
  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      setForeground(state === 'active');
      if (state !== 'active') {
        callbacks.current.onConsumed?.();
        callbacks.current.onCareComplete?.();
      }
    });
    return () => subscription.remove();
  }, []);
  if (!NativeRoomPet || !focused || !foreground) {
    return null;
  }
  const onReady = () => {
    if (
      leaving ||
      returnSequence <= completedReturn.current ||
      startedReturn.current === returnSequence
    ) {
      return;
    }
    startedReturn.current = returnSequence;
    offset.setValue(latestEntry.current);
    Animated.timing(offset, {
      toValue: { x: 0, y: 0 },
      duration: 3000,
      easing: Easing.linear,
      useNativeDriver: true,
    }).start(({ finished }) => {
      if (finished) {
        completedReturn.current = returnSequence;
      } else if (startedReturn.current === returnSequence) {
        startedReturn.current = null;
      }
    });
  };
  return (
    <NativeRoomPet
      petId={petId}
      active={!leaving}
      careMotion={careMotion}
      bathing={bathing}
      sceneOpacity={sceneOpacity}
      returningFromWalk={returningFromWalk}
      onReady={onReady}
      onPetPress={onPress}
      onHidden={onHidden}
      accessibilityRole="button"
      accessibilityLabel="반려동물 상태 보기"
      style={[style, { transform: offset.getTranslateTransform() }]}
    />
  );
}
