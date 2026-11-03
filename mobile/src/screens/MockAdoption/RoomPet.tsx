import { useIsFocused } from '@react-navigation/native';
import { useEffect, useState } from 'react';
import { AppState, Platform, requireNativeComponent, type ViewProps } from 'react-native';

type Props = ViewProps & { petId: string; active: boolean };
const NativeRoomPet =
  Platform.OS === 'android' ? requireNativeComponent<Props>('RoomPetView') : null;

export function RoomPet({ petId, style }: Pick<Props, 'petId' | 'style'>) {
  const focused = useIsFocused();
  const [foreground, setForeground] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const subscription = AppState.addEventListener('change', state =>
      setForeground(state === 'active'),
    );
    return () => subscription.remove();
  }, []);
  if (!NativeRoomPet || !focused || !foreground) {
    return null;
  }
  return <NativeRoomPet petId={petId} active style={style} pointerEvents="none" />;
}
