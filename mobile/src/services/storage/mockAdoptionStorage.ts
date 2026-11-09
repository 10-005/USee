import { STORAGE_KEYS } from '../../constants/storageKeys';
import { getBudgetError } from '../../screens/MockAdoption/budget';
import { getPetNameError } from '../../screens/MockAdoption/petName';
import { mockAdoptionPets } from '../../screens/MockAdoption/data';
import { invalidateSimulationReports } from '../simulation/reports';
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
  walkSeq?: number;
};

type MockAdoptionExpireHandler = (session: MockAdoptionSession) => Promise<void>;

let expireHandler: MockAdoptionExpireHandler | null = null;

export function setMockAdoptionExpireHandler(handler: MockAdoptionExpireHandler | null) {
  expireHandler = handler;
}

function isPositiveId(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0;
}

function isStoredSession(session: MockAdoptionSession): boolean {
  return (
    session.version === 1 &&
    (session.petName === undefined ||
      (typeof session.petName === 'string' && !getPetNameError(session.petName))) &&
    mockAdoptionPets.some(pet => pet.id === session.petId) &&
    (session.sessionSeq === undefined || isPositiveId(session.sessionSeq)) &&
    (session.walkSeq === undefined || isPositiveId(session.walkSeq)) &&
    typeof session.weeklyBudget === 'number' &&
    !getBudgetError(String(session.weeklyBudget)) &&
    Number.isFinite(session.startedAt) &&
    session.expiresAt === session.startedAt + WEEK_MS
  );
}

export async function getActiveMockAdoption(): Promise<MockAdoptionSession | null> {
  const session = await getStoredValue<MockAdoptionSession>(STORAGE_KEYS.MOCK_ADOPTION_SESSION);
  if (!session) {
    return null;
  }
  if (!isStoredSession(session)) {
    await removeStoredValue(STORAGE_KEYS.MOCK_ADOPTION_SESSION);
    return null;
  }
  if (Date.now() >= session.expiresAt) {
    await expireHandler?.(session);
    await removeStoredValue(STORAGE_KEYS.MOCK_ADOPTION_SESSION);
    invalidateSimulationReports();
    return null;
  }
  return session;
}

export async function startMockAdoption(
  petId: string,
  weeklyBudget: number,
  petName?: string,
  sessionSeq?: number,
  walkSeq?: number,
) {
  if (!mockAdoptionPets.some(pet => pet.id === petId) || getBudgetError(String(weeklyBudget))) {
    throw new Error('Invalid mock adoption settings');
  }
  if (petName !== undefined && getPetNameError(petName)) {
    throw new Error('Invalid pet name');
  }
  if (sessionSeq !== undefined && !isPositiveId(sessionSeq)) {
    throw new Error('Invalid simulation session');
  }
  if (walkSeq !== undefined && !isPositiveId(walkSeq)) {
    throw new Error('Invalid simulation walk');
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
    ...(walkSeq === undefined ? {} : { walkSeq }),
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
