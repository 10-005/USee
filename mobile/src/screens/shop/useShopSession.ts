import { useEffect, useState } from 'react';
import { getActiveMockAdoption } from '../../services/storage/mockAdoptionStorage';
import { mockAdoptionPets } from '../MockAdoption/data';
import { useShopStore } from '../../stores/shopStore';

export function useShopSession(petId: string) {
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [attempt, setAttempt] = useState(0);
  const pet = mockAdoptionPets.find(p => p.id === petId);
  useEffect(() => {
    let active = true;
    setStatus('loading');
    const load = async () => {
      const session = await getActiveMockAdoption();
      if (!active) {
        return;
      }
      if (!session || session.petId !== petId) {
        setStatus('error');
        return;
      }
      useShopStore.getState().open(session);
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
  }, [petId, attempt]);
  return { pet, status, retry: () => setAttempt(n => n + 1) };
}
