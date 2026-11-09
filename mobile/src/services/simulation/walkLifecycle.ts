import {
  setMockAdoptionExpireHandler,
  type MockAdoptionSession,
} from '../storage/mockAdoptionStorage';
import { endSimulationWalk, isFinishedWalkError } from './walk';

export async function endStoredWalk(session: MockAdoptionSession): Promise<void> {
  if (!session.sessionSeq || !session.walkSeq) {
    return;
  }
  try {
    await endSimulationWalk(session.sessionSeq, session.walkSeq);
  } catch (error) {
    if (!isFinishedWalkError(error)) {
      throw error;
    }
  }
}

export function installMockAdoptionWalkExpiry() {
  setMockAdoptionExpireHandler(session => endStoredWalk(session));
}
