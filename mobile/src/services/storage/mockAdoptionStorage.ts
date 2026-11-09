import { STORAGE_KEYS } from '../../constants/storageKeys';
import { getBudgetError } from '../../screens/MockAdoption/budget';
import { getPetNameError } from '../../screens/MockAdoption/petName';
import { mockAdoptionPets } from '../../screens/MockAdoption/data';
import { getStoredValue, removeStoredValue, setStoredValue } from './jsonStorage';

const WEEK_MS = 7 * 24 * 60 * 60 * 1000;

export type MockAdoptionSession = {
  version: 1;
  petId: string;
  petName?: string;
  weeklyBudget: number;
  startedAt: number;
  expiresAt: number;
  sessionSeq?: number;
};

export async function getActiveMockAdoption(): Promise<MockAdoptionSession | null> {
  const session = await getStoredValue<MockAdoptionSession>(STORAGE_KEYS.MOCK_ADOPTION_SESSION);
  if (!session) {
    return null;
  }
  if (
    session.version !== 1 ||
    (session.petName !== undefined &&
      (typeof session.petName !== 'string' || Boolean(getPetNameError(session.petName)))) ||
    !mockAdoptionPets.some(pet => pet.id === session.petId) ||
    (session.sessionSeq !== undefined &&
      (typeof session.sessionSeq !== 'number' ||
        !Number.isInteger(session.sessionSeq) ||
        session.sessionSeq <= 0)) ||
    typeof session.weeklyBudget !== 'number' ||
    getBudgetError(String(session.weeklyBudget)) ||
    !Number.isFinite(session.startedAt) ||
    session.expiresAt !== session.startedAt + WEEK_MS ||
    Date.now() >= session.expiresAt
  ) {
    await removeStoredValue(STORAGE_KEYS.MOCK_ADOPTION_SESSION);
    return null;
  }
  return session;
}

export async function startMockAdoption(
  petId: string,
  weeklyBudget: number,
  petName?: string,
  sessionSeq?: number,
) {
  if (!mockAdoptionPets.some(pet => pet.id === petId) || getBudgetError(String(weeklyBudget))) {
    throw new Error('Invalid mock adoption settings');
  }
  if (petName !== undefined && getPetNameError(petName)) {
    throw new Error('Invalid pet name');
  }
  if (sessionSeq !== undefined && (!Number.isInteger(sessionSeq) || sessionSeq <= 0)) {
    throw new Error('Invalid simulation session');
  }
  const active = await getActiveMockAdoption();
  if (active && (sessionSeq === undefined || active.sessionSeq === sessionSeq)) {
    return active;
  }
  const startedAt = Date.now();
  const session: MockAdoptionSession = {
    version: 1,
    petId,
    ...(petName === undefined ? {} : { petName }),
    ...(sessionSeq === undefined ? {} : { sessionSeq }),
    weeklyBudget,
    startedAt,
    expiresAt: startedAt + WEEK_MS,
  };
  await setStoredValue(STORAGE_KEYS.MOCK_ADOPTION_SESSION, session);
  return session;
}

export async function endMockAdoption() {
  await removeStoredValue(STORAGE_KEYS.MOCK_ADOPTION_SESSION);
}
