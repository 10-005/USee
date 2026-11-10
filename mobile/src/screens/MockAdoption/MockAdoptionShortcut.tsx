import { useEffect, useState } from 'react';
import { Alert, Image, Pressable, StyleSheet } from 'react-native';

import {
  getActiveMockAdoption,
  type MockAdoptionSession,
} from '../../services/storage/mockAdoptionStorage';
import { mockAdoptionPets } from './data';

type Props = {
  bottom: number;
  onOpen: (petId: string) => void;
  session: MockAdoptionSession | null;
};

export function MockAdoptionShortcut({ bottom, onOpen, session }: Props) {
  const [current, setCurrent] = useState<MockAdoptionSession | null>(session);
  const [opening, setOpening] = useState(false);

  useEffect(() => {
    const remaining = session ? session.expiresAt - Date.now() : 0;
    setCurrent(remaining > 0 ? session : null);
    if (remaining <= 0) {
      return;
    }
    const expiryTimer = setTimeout(() => setCurrent(null), remaining);
    return () => clearTimeout(expiryTimer);
  }, [session]);

  const pet = mockAdoptionPets.find(item => item.id === current?.petId);
  if (!current || !pet) {
    return null;
  }

  const open = async () => {
    setOpening(true);
    try {
      const active = await getActiveMockAdoption();
      const remaining = active ? active.expiresAt - Date.now() : 0;
      setCurrent(remaining > 0 ? active : null);
      if (active && remaining > 0) {
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
      accessibilityLabel={`${current.petName ?? pet.name} 모의입양 바로가기`}
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
