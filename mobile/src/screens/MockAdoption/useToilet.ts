import { useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { STORAGE_KEYS } from '../../constants/storageKeys';
import { getActiveMockAdoption } from '../../services/storage/mockAdoptionStorage';
import { getStoredValue, setStoredValue } from '../../services/storage/jsonStorage';
import type { PetCategory } from './data';
import { createToiletSchedule, isToiletFull } from './toiletSchedule';

type ToiletState = { petId: string; startedAt: number; events: number[]; cleanedAt: number };

export function useToilet(petId: string, category: PetCategory) {
  const [full, setFull] = useState(false);
  const state = useRef<ToiletState | null>(null);
  const writes = useRef(Promise.resolve());
  useEffect(() => {
    let disposed = false;
    state.current = null;
    setFull(false);
    const refresh = () => {
      if (state.current) {
        setFull(isToiletFull(state.current.events, state.current.cleanedAt, Date.now()));
      }
    };
    (async () => {
      const session = await getActiveMockAdoption();
      if (!session || session.petId !== petId || disposed) {
        return;
      }
      const saved = await getStoredValue<ToiletState>(STORAGE_KEYS.MOCK_ADOPTION_TOILET);
      if (disposed) {
        return;
      }
      const next =
        saved?.petId === petId && saved.startedAt === session.startedAt
          ? saved
          : {
              petId,
              startedAt: session.startedAt,
              cleanedAt: session.startedAt,
              events: createToiletSchedule(category, session.startedAt, session.expiresAt),
            };
      await setStoredValue(STORAGE_KEYS.MOCK_ADOPTION_TOILET, next);
      if (disposed) {
        return;
      }
      state.current = next;
      refresh();
    })().catch(() => {
      /* Keep cleaning unavailable until persisted state is loaded. */
    });
    const timer = setInterval(refresh, 30_000);
    const subscription = AppState.addEventListener('change', value => {
      if (value === 'active') {
        refresh();
      }
    });
    return () => {
      disposed = true;
      clearInterval(timer);
      subscription.remove();
    };
  }, [petId, category]);

  const clean = async () => {
    const current = state.current;
    if (!current || !isToiletFull(current.events, current.cleanedAt, Date.now())) {
      return false;
    }
    const next = { ...current, cleanedAt: Date.now() };
    const write = writes.current
      .catch(() => {})
      .then(() => setStoredValue(STORAGE_KEYS.MOCK_ADOPTION_TOILET, next));
    writes.current = write;
    await write;
    if (state.current === current) {
      state.current = next;
      setFull(false);
    }
    return true;
  };
  return { full, clean };
}
