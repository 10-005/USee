import type { MockAdoptionSession } from './mockAdoptionStorage';
import { STORAGE_KEYS } from '../../constants/storageKeys';
import { getStoredValue, setStoredValue } from './jsonStorage';

// Internal contract for the future FCM adapter. Keep server payload mapping there.
export type MockAdoptionEvent = {
  id: string;
  petId: string;
  sessionStartedAt: number;
  title: string;
  body: string;
  kind?: 'illness' | 'other';
};

type EventState = { pending: MockAdoptionEvent[]; shown: string[]; illnesses: MockAdoptionEvent[] };
const KEY = STORAGE_KEYS.MOCK_ADOPTION_EVENTS;
let operation: Promise<unknown> = Promise.resolve();

function serialize<T>(action: () => Promise<T>): Promise<T> {
  const next = operation.then(action, action);
  operation = next.catch(() => undefined);
  return next;
}

function eventKey(event: MockAdoptionEvent) {
  return JSON.stringify([event.sessionStartedAt, event.petId, event.id]);
}

async function readState(): Promise<EventState> {
  const state = await getStoredValue<EventState>(KEY);
  return {
    pending: state?.pending ?? [],
    shown: state?.shown ?? [],
    illnesses: state?.illnesses ?? [],
  };
}

/** Call after the FCM adapter identifies a mock-adoption sudden event. */
export function receiveMockAdoptionEvent(event: MockAdoptionEvent): Promise<void> {
  return serialize(async () => {
    if (
      !event.id.trim() ||
      !event.petId.trim() ||
      !event.title.trim() ||
      !event.body.trim() ||
      !Number.isFinite(event.sessionStartedAt)
    ) {
      throw new Error('Invalid mock adoption event');
    }
    const state = await readState();
    const key = eventKey(event);
    if (state.shown.includes(key) || state.pending.some(item => eventKey(item) === key)) {
      return;
    }
    await setStoredValue(KEY, {
      ...state,
      pending: [...state.pending, event],
      illnesses: event.kind === 'illness' ? [...state.illnesses, event] : state.illnesses,
    });
  });
}

/** Persist consumption before displaying, including across screen/app restarts. */
export function takeMockAdoptionEvent(
  session: MockAdoptionSession,
  isActive: () => boolean = () => true,
) {
  return serialize(async () => {
    const state = await readState();
    const event = state.pending.find(
      item => item.petId === session.petId && item.sessionStartedAt === session.startedAt,
    );
    if (!event || !isActive()) {
      return null;
    }
    const key = eventKey(event);
    await setStoredValue(KEY, {
      ...state,
      pending: state.pending.filter(item => eventKey(item) !== key),
      shown: [...state.shown, key],
    });
    return event;
  });
}

function belongsToSession(event: MockAdoptionEvent, session: MockAdoptionSession) {
  return event.petId === session.petId && event.sessionStartedAt === session.startedAt;
}

export function hasPendingTreatment(session: MockAdoptionSession) {
  return serialize(async () =>
    (await readState()).illnesses.some(event => belongsToSession(event, session)),
  );
}

/** Call only after treatment succeeds; dismissing the popup does not resolve illness. */
export function completeMockAdoptionTreatment(session: MockAdoptionSession) {
  return serialize(async () => {
    const state = await readState();
    const resolved = state.illnesses.filter(event => belongsToSession(event, session));
    const resolvedKeys = new Set(resolved.map(eventKey));
    await setStoredValue(KEY, {
      pending: state.pending.filter(event => !resolvedKeys.has(eventKey(event))),
      shown: [...new Set([...state.shown, ...resolvedKeys])],
      illnesses: state.illnesses.filter(event => !belongsToSession(event, session)),
    });
  });
}
