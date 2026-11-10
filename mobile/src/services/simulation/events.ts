import type { SimulationCareAction, SimulationWalkAction } from './actions';

// A simulation FCM push only carries data.target SIMULATION. It is not this DTO.
export enum SimulationEventStatus {
  Pending = 'PENDING',
  Resolved = 'RESOLVED',
  Expired = 'EXPIRED',
}
export enum SimulationTriggerContext {
  Home = 'HOME',
  Walk = 'WALK',
}
export enum SimulationTriggerType {
  Random = 'RANDOM',
  Condition = 'CONDITION',
}
export enum SimulationPushType {
  Event = 'EVENT',
}
export type SimulationEvent = {
  occurrenceSeq: number;
  scenario: {
    scenarioSeq: number;
    title: string;
    description: string;
    triggerContext: SimulationTriggerContext;
    triggerType: SimulationTriggerType;
  };
  vetVisit: { cost: number } | null;
  walkSeq: number | null;
  status: SimulationEventStatus;
  occurredDttm: string;
  expiresDttm: string;
  resolvedDttm: string | null;
  resolvedActionCode: SimulationCareAction | SimulationWalkAction | null;
  resultText: string | null;
};
export type SimulationEventsQuery = {
  status?: SimulationEventStatus;
  page?: number;
  size?: number;
  sort?: 'occurredDttm,asc' | 'occurredDttm,desc';
};
export function getVetVisit(event: SimulationEvent | null) {
  return event?.status === SimulationEventStatus.Pending ? event.vetVisit : null;
}
