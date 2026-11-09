import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useState } from 'react';
import { Alert, AppState, Image, Pressable, StyleSheet } from 'react-native';

import {
  getActiveMockAdoption,
  type MockAdoptionSession,
} from '../../services/storage/mockAdoptionStorage';
import { mockAdoptionPets } from './data';

type Props = {
  bottom: number;
  onOpen: (petId: string) => void;
};

export function MockAdoptionShortcut({ bottom, onOpen }: Props) {
  const [session, setSession] = useState<MockAdoptionSession | null>(null);
  const [opening, setOpening] = useState(false);

  useFocusEffect(
    useCallback(() => {
      let mounted = true;
      let revision = 0;
      let expiryTimer: ReturnType<typeof setTimeout> | undefined;
      const refresh = async () => {
        const current = ++revision;
        clearTimeout(expiryTimer);
        try {
          const active = await getActiveMockAdoption();
          if (!mounted || current !== revision) {
            return;
          }
          const remaining = active ? active.expiresAt - Date.now() : 0;
          setSession(remaining > 0 ? active : null);
          if (remaining > 0) {
            expiryTimer = setTimeout(() => {
              setSession(null);
              getActiveMockAdoption().catch(() => undefined);
            }, remaining);
          }
        } catch {
          if (mounted && current === revision) {
            setSession(null);
          }
        }
      };
      refresh();
      const subscription = AppState.addEventListener('change', state => {
        if (state === 'active') {
          refresh();
        }
      });
      return () => {
        mounted = false;
        clearTimeout(expiryTimer);
        subscription.remove();
      };
    }, []),
  );

  const pet = mockAdoptionPets.find(item => item.id === session?.petId);
  if (!session || !pet) {
    return null;
  }

  const open = async () => {
    setOpening(true);
    try {
      const active = await getActiveMockAdoption();
      setSession(active);
      if (active) {
        onOpen(active.petId);
      }
    } catch {
      Alert.alert('모의입양', '진행 상태를 불러오지 못했어요. 다시 시도해주세요.');
    } finally {
      setOpening(false);
    }
  };

  return (
    <Pressable
      accessibilityLabel={`${session.petName ?? pet.name} 모의입양 바로가기`}
      accessibilityRole="button"
      accessibilityState={{ disabled: opening, busy: opening }}
      disabled={opening}
      onPress={open}
      style={({ pressed }) => [styles.button, { bottom }, pressed && styles.pressed]}
    >
      <Image accessible={false} source={pet.image} resizeMode="contain" style={styles.pet} />
      <Image
        accessible={false}
        source={require('../../assets/images/mock-adoption/direct.png')}
        resizeMode="contain"
        style={styles.direct}
      />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    position: 'absolute',
    right: 24,
    width: 72,
    height: 72,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pet: { width: 72, height: 72 },
  direct: {
    position: 'absolute',
    bottom: 0,
    right: 0,
    width: 28,
    height: 28,
  },
  pressed: { opacity: 0.7 },
});
