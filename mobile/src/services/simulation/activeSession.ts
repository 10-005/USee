import type { AxiosInstance } from 'axios';

import { getBudgetError } from '../../screens/MockAdoption/budget';
import { mockAdoptionPets } from '../../screens/MockAdoption/data';
import { getPetNameError } from '../../screens/MockAdoption/petName';
import { simulationClient } from '../api/client';
import { ApiClientError } from '../api/errors';
import { readApiData } from '../api/response';
import {
  endMockAdoption,
  getActiveMockAdoption,
  startMockAdoption,
  updateMockAdoptionServerState,
  type MockAdoptionSession,
} from '../storage/mockAdoptionStorage';
import { CARE_STAT_KEYS, type CareStats } from './care';
import { invalidateSimulationReports } from './reports';
import {
  SimulationEventStatus,
  SimulationTriggerContext,
  SimulationTriggerType,
  type SimulationEvent,
} from './events';

export type ActiveEquipment = {
  foodBowl: boolean;
  waterBowl: boolean;
  litterBox: boolean;
  toy: boolean;
  leash: boolean;
};

export type ActiveSimulationStatus = 'IN_PROGRESS' | 'COMPLETED' | 'ABANDONED' | 'FAILED';

export type ActiveSimulation = {
  sessionSeq: number;
  userSeq: number;
  animalName: string;
  breed: {
    breedSeq: string;
    breedName: string;
    speciesType: 'DOG' | 'CAT';
    representativeImageUrl: string | null;
  };
  speciesType: 'DOG' | 'CAT';
  personality: 'ACTIVE' | 'TIMID' | 'AGGRESSIVE' | 'FRIENDLY';
  status: ActiveSimulationStatus;
  expectedCost: number;
  balance: number;
  startedDttm: string;
  endsDttm: string;
  endedDttm: string | null;
  stats: CareStats;
  lastActionDttm: string | null;
  equipment: ActiveEquipment;
  pendingEvent: SimulationEvent | null;
  activeWalkSeq: number | null;
};

const PERSONALITIES = new Set(['ACTIVE', 'TIMID', 'AGGRESSIVE', 'FRIENDLY']);
const SESSION_STATUSES = new Set(['IN_PROGRESS', 'COMPLETED', 'ABANDONED', 'FAILED']);
const EVENT_STATUSES = new Set(Object.values(SimulationEventStatus));
const TRIGGER_CONTEXTS = new Set(Object.values(SimulationTriggerContext));
const TRIGGER_TYPES = new Set(Object.values(SimulationTriggerType));
const EQUIPMENT_KEYS = ['foodBowl', 'waterBowl', 'litterBox', 'toy', 'leash'] as const;

const ACTIVE_FAILURE = '진행 중인 모의입양을 확인하지 못했어요. 다시 시도해주세요.';

type SimulationClient = Pick<AxiosInstance, 'get'>;

let currentActiveSimulation: ActiveSimulation | null = null;

function isPositiveId(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0;
}

function invalidActive(): ApiClientError {
  return new ApiClientError({ code: 'UNKNOWN_ERROR', message: ACTIVE_FAILURE });
}

function readText(value: unknown): string | null {
  if (typeof value !== 'string' || value.length === 0) {
    return null;
  }
  return value;
}

function readNullableText(value: unknown): string | null {
  if (value === null) {
    return null;
  }
  const text = readText(value);
  if (!text) {
    throw invalidActive();
  }
  return text;
}

function readBreedSeq(value: unknown): string {
  if (typeof value === 'string' && value.trim().length > 0) {
    return value.trim();
  }
  if (isPositiveId(value)) {
    return String(value);
  }
  throw invalidActive();
}

function readStats(value: unknown): CareStats {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidActive();
  }
  const stats = value as Partial<CareStats>;
  if (!readText(stats.calculatedDttm)) {
    throw invalidActive();
  }
  if (
    !CARE_STAT_KEYS.every(
      key => typeof stats[key] === 'number' && Number.isInteger(stats[key] as number),
    )
  ) {
    throw invalidActive();
  }
  return {
    satiety: stats.satiety as number,
    hydration: stats.hydration as number,
    cleanliness: stats.cleanliness as number,
    bond: stats.bond as number,
    calmness: stats.calmness as number,
    health: stats.health as number,
    energy: stats.energy as number,
    calculatedDttm: stats.calculatedDttm as string,
  };
}

function readEquipment(value: unknown): ActiveEquipment {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidActive();
  }
  const equipment = value as Partial<ActiveEquipment>;
  if (!EQUIPMENT_KEYS.every(key => typeof equipment[key] === 'boolean')) {
    throw invalidActive();
  }
  return {
    foodBowl: equipment.foodBowl as boolean,
    waterBowl: equipment.waterBowl as boolean,
    litterBox: equipment.litterBox as boolean,
    toy: equipment.toy as boolean,
    leash: equipment.leash as boolean,
  };
}

function readPendingEvent(value: unknown): SimulationEvent | null {
  if (value === null) {
    return null;
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidActive();
  }
  const event = value as Partial<SimulationEvent>;
  const scenario = event.scenario;
  if (
    !scenario ||
    typeof scenario !== 'object' ||
    Array.isArray(scenario) ||
    !isPositiveId(event.occurrenceSeq) ||
    !isPositiveId(scenario.scenarioSeq) ||
    !readText(scenario.title) ||
    !readText(scenario.description) ||
    !TRIGGER_CONTEXTS.has(scenario.triggerContext) ||
    !TRIGGER_TYPES.has(scenario.triggerType) ||
    !EVENT_STATUSES.has(event.status as SimulationEventStatus) ||
    !readText(event.occurredDttm) ||
    !readText(event.expiresDttm) ||
    (event.walkSeq !== null && !isPositiveId(event.walkSeq)) ||
    (event.resolvedActionCode !== null && !readText(event.resolvedActionCode))
  ) {
    throw invalidActive();
  }
  const vetVisit = event.vetVisit;
  if (vetVisit !== null) {
    if (
      !vetVisit ||
      typeof vetVisit !== 'object' ||
      Array.isArray(vetVisit) ||
      typeof vetVisit.cost !== 'number' ||
      !Number.isInteger(vetVisit.cost) ||
      vetVisit.cost < 0
    ) {
      throw invalidActive();
    }
  }
  return {
    occurrenceSeq: event.occurrenceSeq,
    scenario: {
      scenarioSeq: scenario.scenarioSeq,
      title: scenario.title,
      description: scenario.description,
      triggerContext: scenario.triggerContext,
      triggerType: scenario.triggerType,
    },
    vetVisit: vetVisit === null ? null : { cost: vetVisit.cost },
    walkSeq: event.walkSeq ?? null,
    status: event.status as SimulationEventStatus,
    occurredDttm: event.occurredDttm as string,
    expiresDttm: event.expiresDttm as string,
    resolvedDttm: readNullableText(event.resolvedDttm),
    resolvedActionCode: event.resolvedActionCode,
    resultText: readNullableText(event.resultText),
  };
}

function readActiveSimulation(value: unknown): ActiveSimulation {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidActive();
  }
  const session = value as Partial<ActiveSimulation>;
  const breed = session.breed;
  if (
    !breed ||
    typeof breed !== 'object' ||
    Array.isArray(breed) ||
    !isPositiveId(session.sessionSeq) ||
    !isPositiveId(session.userSeq) ||
    !readText(session.animalName) ||
    !readText(breed.breedName) ||
    (breed.speciesType !== 'DOG' && breed.speciesType !== 'CAT') ||
    session.speciesType !== breed.speciesType ||
    !PERSONALITIES.has(session.personality as string) ||
    !SESSION_STATUSES.has(session.status as string) ||
    typeof session.expectedCost !== 'number' ||
    !Number.isInteger(session.expectedCost) ||
    typeof session.balance !== 'number' ||
    !Number.isInteger(session.balance) ||
    !readText(session.startedDttm) ||
    !readText(session.endsDttm) ||
    (session.activeWalkSeq !== null && !isPositiveId(session.activeWalkSeq)) ||
    (breed.representativeImageUrl !== null && !readText(breed.representativeImageUrl))
  ) {
    throw invalidActive();
  }
  return {
    sessionSeq: session.sessionSeq,
    userSeq: session.userSeq,
    animalName: session.animalName as string,
    breed: {
      breedSeq: readBreedSeq(breed.breedSeq),
      breedName: breed.breedName,
      speciesType: breed.speciesType,
      representativeImageUrl: breed.representativeImageUrl,
    },
    speciesType: session.speciesType as 'DOG' | 'CAT',
    personality: session.personality as ActiveSimulation['personality'],
    status: session.status as ActiveSimulationStatus,
    expectedCost: session.expectedCost,
    balance: session.balance,
    startedDttm: session.startedDttm as string,
    endsDttm: session.endsDttm as string,
    endedDttm: readNullableText(session.endedDttm),
    stats: readStats(session.stats),
    lastActionDttm: readNullableText(session.lastActionDttm),
    equipment: readEquipment(session.equipment),
    pendingEvent: readPendingEvent(session.pendingEvent),
    activeWalkSeq: session.activeWalkSeq ?? null,
  };
}

function petForBreed(breedSeq: string) {
  return mockAdoptionPets.find(
    pet => pet.breedCode === breedSeq || pet.breedCode === breedSeq.padStart(5, '0'),
  );
}

export function readCurrentActiveSimulation(): ActiveSimulation | null {
  return currentActiveSimulation;
}

export async function applyActiveSimulation(
  active: ActiveSimulation | null,
): Promise<MockAdoptionSession | null> {
  if (!active || active.status !== 'IN_PROGRESS') {
    currentActiveSimulation = null;
    const local = await getActiveMockAdoption();
    if (local) {
      await endMockAdoption();
    }
    if (active || local) {
      invalidateSimulationReports();
    }
    return null;
  }

  currentActiveSimulation = active;
  const local = await getActiveMockAdoption();
  const pet = petForBreed(active.breed.breedSeq);
  const sameSession = local?.sessionSeq === active.sessionSeq;
  const samePet = local?.sessionSeq === undefined && pet?.id === local?.petId;
  if (local && (sameSession || samePet)) {
    await updateMockAdoptionServerState({
      sessionSeq: active.sessionSeq,
      walkSeq: active.activeWalkSeq,
    });
    return getActiveMockAdoption();
  }

  if (pet && !getPetNameError(active.animalName) && !getBudgetError(String(active.expectedCost))) {
    return startMockAdoption(
      pet.id,
      active.expectedCost,
      active.animalName,
      active.sessionSeq,
      active.activeWalkSeq ?? undefined,
    );
  }

  if (local) {
    await endMockAdoption();
  }
  return null;
}

export async function getActiveSimulation(
  client: SimulationClient = simulationClient,
): Promise<ActiveSimulation | null> {
  const response = await client.get('/simulations/me/active');
  const data = readApiData<ActiveSimulation | null>(response.data);
  if (data === null) {
    return null;
  }
  return readActiveSimulation(data);
}
