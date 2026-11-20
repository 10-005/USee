import { useEffect, useState } from 'react';
import { getActiveMockAdoption } from '../../services/storage/mockAdoptionStorage';
import type { MockAdoptionSession } from '../../services/storage/mockAdoptionStorage';
import { ensureSessionShopCatalog } from '../../services/simulation/shopItems';
import { mockAdoptionPets } from '../MockAdoption/data';
import { useShopStore } from '../../stores/shopStore';

export function useShopSession(petId: string) {
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [attempt, setAttempt] = useState(0);
  const [session, setSession] = useState<MockAdoptionSession | null>(null);
  const pet = mockAdoptionPets.find(p => p.id === petId);
  useEffect(() => {
    let active = true;
    setStatus('loading');
    setSession(null);
    const load = async () => {
      const loadedSession = await getActiveMockAdoption();
      if (!active) {
        return;
      }
      if (!loadedSession || loadedSession.petId !== petId || !pet) {
        setStatus('error');
        return;
      }
      if (loadedSession.sessionSeq) {
        await ensureSessionShopCatalog(
          `${loadedSession.petId}:${loadedSession.startedAt}`,
          pet.category === 'cat' ? 'CAT' : 'DOG',
        );
      }
      if (!active) {
        return;
      }
      useShopStore.getState().open(loadedSession);
      setSession(loadedSession);
      setStatus('ready');
    };
    load().catch(() => {
      if (active) {
        setStatus('error');
      }
    });
    return () => {
      active = false;
    };
  }, [pet, petId, attempt]);
  return { pet, session, status, retry: () => setAttempt(n => n + 1) };
}
