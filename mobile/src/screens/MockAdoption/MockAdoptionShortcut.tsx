import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useState } from 'react';
import { Alert, AppState, Image, Pressable, StyleSheet, View } from 'react-native';

import {
  getActiveMockAdoption,
  type MockAdoptionSession,
} from '../../services/storage/mockAdoptionStorage';
import { colors, radius, shadows } from '../../theme';
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
            expiryTimer = setTimeout(() => setSession(null), remaining);
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
      <View style={styles.directBadge}>
        <Image
          accessible={false}
          source={require('../../assets/images/mock-adoption/direct.png')}
          resizeMode="contain"
          style={styles.direct}
        />
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    ...shadows.level2,
    position: 'absolute',
    right: 24,
    width: 80,
    height: 80,
    borderRadius: radius.pill,
    backgroundColor: colors.white,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pet: { width: 72, height: 72 },
  directBadge: {
    position: 'absolute',
    bottom: -2,
    right: -2,
    width: 28,
    height: 28,
    borderRadius: radius.pill,
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.gray200,
    alignItems: 'center',
    justifyContent: 'center',
  },
  direct: { width: 20, height: 20 },
  pressed: { opacity: 0.7 },
});
