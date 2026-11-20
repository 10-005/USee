import type { AxiosInstance } from 'axios';

import { simulationClient } from '../api/client';
import { ApiClientError } from '../api/errors';
import { readApiData } from '../api/response';
import { CARE_STAT_KEYS, type CareStats } from './care';
import { SimulationWalkAction } from './actions';
import {
  SimulationEventStatus,
  SimulationTriggerContext,
  SimulationTriggerType,
  type SimulationEvent,
} from './events';

export type SimulationStatDelta = Record<string, number>;

export type EndedSimulationWalk = {
  walkSeq: number;
  status: 'ENDED';
  startedDttm: string;
  endedDttm: string;
  durationMinutes: number;
  statDelta: SimulationStatDelta;
  stats: Record<string, unknown>;
  eventCount: number;
};

const WALK_END_ERROR_MESSAGES: Record<string, string> = {
  SIMULATION_WALK_ALREADY_IN_PROGRESS: '이미 진행 중인 산책이 있어요.',
  SIMULATION_WALK_NOT_IN_PROGRESS: '진행 중인 산책이 없어요.',
  SIMULATION_ITEM_NOT_IN_INVENTORY: '필요한 아이템이 없어요.',
  SIMULATION_NOT_IN_PROGRESS: '진행 중인 모의입양이 없어요.',
  SIMULATION_WALK_NOT_FOUND: '산책을 찾지 못했어요.',
  SIMULATION_POOP_NOT_FOUND: '치울 배변이 없어요.',
};

const FINISHED_WALK_CODES = new Set([
  'SIMULATION_WALK_NOT_IN_PROGRESS',
  'SIMULATION_WALK_NOT_FOUND',
  'SIMULATION_NOT_IN_PROGRESS',
]);

const WALK_END_FAILURE = '산책을 종료하지 못했어요. 다시 시도해주세요.';
const WALK_ACTION_FAILURE = '산책 중 행동을 하지 못했어요. 다시 시도해주세요.';

const WALK_ACTION_ERROR_MESSAGES: Record<string, string> = {
  SIMULATION_WALK_NOT_IN_PROGRESS: '진행 중인 산책이 없어요.',
  SIMULATION_POOP_NOT_FOUND: '치울 배변이 없어요.',
  SIMULATION_ITEM_NOT_IN_INVENTORY: '필요한 아이템이 없어요.',
  SIMULATION_NOT_IN_PROGRESS: '진행 중인 모의입양이 없어요.',
  SIMULATION_NOT_FOUND: '진행 중인 모의입양을 찾지 못했어요.',
  SIMULATION_WALK_NOT_FOUND: '산책을 찾지 못했어요.',
  COMMON_INVALID_INPUT: '산책 중 행동을 확인해주세요.',
};

export type WalkActionRequest =
  | { actionCode: SimulationWalkAction.GiveWater }
  | { actionCode: SimulationWalkAction.GiveSnack; itemSeq: number }
  | { actionCode: SimulationWalkAction.CleanUpPoop; itemSeq?: number };

export type WalkActionResult = {
  actionLogSeq: number;
  actionCode: WalkActionRequest['actionCode'];
  walkSeq: number;
  consumedItem: { itemSeq: number; itemName: string; remainingQuantity: number } | null;
  statDelta: SimulationStatDelta;
  stats: CareStats;
  balance: number;
  resolvedEvent: {
    occurrenceSeq: number;
    scenarioTitle: string;
    resultText: string | null;
    eventStatDelta: SimulationStatDelta;
    eventCost: number;
  } | null;
};

export type WalkHeartbeat = {
  walkSeq: number;
  elapsedMinutes: number;
  stats: CareStats;
  newEvent: SimulationEvent | null;
  pendingEvent: SimulationEvent | null;
};

export type StartedSimulationWalk = {
  walkSeq: number;
  status: 'IN_PROGRESS';
  startedDttm: string;
  heartbeatIntervalSeconds: number;
  stats: Record<string, unknown>;
};

const WALK_START_ERROR_MESSAGES: Record<string, string> = {
  SIMULATION_WALK_ALREADY_IN_PROGRESS: '이미 진행 중인 산책이 있어요.',
  SIMULATION_WALK_NOT_FOR_SPECIES: '이 동물은 산책할 수 없어요.',
};

const WALK_START_FAILURE = '산책을 시작하지 못했어요. 다시 시도해주세요.';

type SimulationClient = Pick<AxiosInstance, 'post'>;

function isPositiveId(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0;
}

function isStatDelta(value: unknown): value is SimulationStatDelta {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return false;
  }
  return Object.values(value).every(item => typeof item === 'number' && Number.isInteger(item));
}

function invalidWalk(message = WALK_END_FAILURE): ApiClientError {
  return new ApiClientError({ code: 'UNKNOWN_ERROR', message });
}

const EVENT_STATUSES = new Set(Object.values(SimulationEventStatus));
const TRIGGER_CONTEXTS = new Set(Object.values(SimulationTriggerContext));
const TRIGGER_TYPES = new Set(Object.values(SimulationTriggerType));

function readText(value: unknown): string | null {
  return typeof value === 'string' && value.length > 0 ? value : null;
}

function readHeartbeatStats(value: unknown, failure = WALK_START_FAILURE): CareStats {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidWalk(failure);
  }
  const stats = value as Partial<CareStats>;
  if (
    !readText(stats.calculatedDttm) ||
    !CARE_STAT_KEYS.every(key => typeof stats[key] === 'number' && Number.isInteger(stats[key]))
  ) {
    throw invalidWalk(failure);
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

function readHeartbeatEvent(value: unknown): SimulationEvent | null {
  if (value === null) {
    return null;
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidWalk(WALK_START_FAILURE);
  }
  const event = value as Partial<SimulationEvent>;
  const scenario = event.scenario;
  if (
    !scenario ||
    typeof scenario !== 'object' ||
    !isPositiveId(event.occurrenceSeq) ||
    !isPositiveId(scenario.scenarioSeq) ||
    !readText(scenario.title) ||
    !readText(scenario.description) ||
    !TRIGGER_CONTEXTS.has(scenario.triggerContext) ||
    !TRIGGER_TYPES.has(scenario.triggerType) ||
    !EVENT_STATUSES.has(event.status as SimulationEventStatus) ||
    !readText(event.occurredDttm) ||
    !readText(event.expiresDttm)
  ) {
    throw invalidWalk(WALK_START_FAILURE);
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
    vetVisit: null,
    walkSeq: isPositiveId(event.walkSeq) ? event.walkSeq : null,
    status: event.status as SimulationEventStatus,
    occurredDttm: event.occurredDttm as string,
    expiresDttm: event.expiresDttm as string,
    resolvedDttm: event.resolvedDttm === null ? null : readText(event.resolvedDttm),
    resolvedActionCode: null,
    resultText: event.resultText === null ? null : readText(event.resultText),
  };
}

export function isWalkPoopEvent(event: SimulationEvent): boolean {
  const text = `${event.scenario.title} ${event.scenario.description}`;
  return text.includes('똥') || text.includes('배변');
}

function readHeartbeat(value: unknown): WalkHeartbeat {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidWalk(WALK_START_FAILURE);
  }
  const beat = value as Partial<WalkHeartbeat>;
  if (
    !isPositiveId(beat.walkSeq) ||
    typeof beat.elapsedMinutes !== 'number' ||
    !Number.isInteger(beat.elapsedMinutes) ||
    beat.elapsedMinutes < 0
  ) {
    throw invalidWalk(WALK_START_FAILURE);
  }
  return {
    walkSeq: beat.walkSeq,
    elapsedMinutes: beat.elapsedMinutes,
    stats: readHeartbeatStats(beat.stats),
    newEvent: readHeartbeatEvent(beat.newEvent),
    pendingEvent: readHeartbeatEvent(beat.pendingEvent),
  };
}

function readStartedWalk(value: unknown): StartedSimulationWalk {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidWalk(WALK_START_FAILURE);
  }
  const walk = value as Partial<StartedSimulationWalk>;
  if (
    !isPositiveId(walk.walkSeq) ||
    walk.status !== 'IN_PROGRESS' ||
    typeof walk.startedDttm !== 'string' ||
    walk.startedDttm.length === 0 ||
    typeof walk.heartbeatIntervalSeconds !== 'number' ||
    !Number.isInteger(walk.heartbeatIntervalSeconds) ||
    walk.heartbeatIntervalSeconds <= 0 ||
    !walk.stats ||
    typeof walk.stats !== 'object' ||
    Array.isArray(walk.stats)
  ) {
    throw invalidWalk(WALK_START_FAILURE);
  }
  return {
    walkSeq: walk.walkSeq,
    status: 'IN_PROGRESS',
    startedDttm: walk.startedDttm,
    heartbeatIntervalSeconds: walk.heartbeatIntervalSeconds,
    stats: walk.stats,
  };
}

function readEndedWalk(value: unknown): EndedSimulationWalk {
  if (!value || typeof value !== 'object') {
    throw invalidWalk();
  }
  const walk = value as Partial<EndedSimulationWalk>;
  if (
    !isPositiveId(walk.walkSeq) ||
    walk.status !== 'ENDED' ||
    typeof walk.startedDttm !== 'string' ||
    walk.startedDttm.length === 0 ||
    typeof walk.endedDttm !== 'string' ||
    walk.endedDttm.length === 0 ||
    !Number.isFinite(walk.durationMinutes) ||
    !isStatDelta(walk.statDelta) ||
    !walk.stats ||
    typeof walk.stats !== 'object' ||
    !Number.isInteger(walk.eventCount) ||
    walk.eventCount < 0
  ) {
    throw invalidWalk();
  }
  return {
    walkSeq: walk.walkSeq,
    status: 'ENDED',
    startedDttm: walk.startedDttm,
    endedDttm: walk.endedDttm,
    durationMinutes: walk.durationMinutes,
    statDelta: walk.statDelta,
    stats: walk.stats,
    eventCount: walk.eventCount,
  };
}

export function getSimulationWalkEndErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError && WALK_END_ERROR_MESSAGES[error.code]) {
    return WALK_END_ERROR_MESSAGES[error.code];
  }
  return WALK_END_FAILURE;
}

export function isFinishedWalkError(error: unknown): boolean {
  return error instanceof ApiClientError && FINISHED_WALK_CODES.has(error.code);
}

export function getSimulationWalkStartErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError && WALK_START_ERROR_MESSAGES[error.code]) {
    return WALK_START_ERROR_MESSAGES[error.code];
  }
  return WALK_START_FAILURE;
}

export async function startSimulationWalk(
  sessionSeq: number,
  client: SimulationClient = simulationClient,
): Promise<StartedSimulationWalk> {
  if (!isPositiveId(sessionSeq)) {
    throw invalidWalk(WALK_START_FAILURE);
  }
  const response = await client.post(`/simulations/${sessionSeq}/walks`);
  return readStartedWalk(readApiData(response.data));
}

export async function sendSimulationWalkHeartbeat(
  sessionSeq: number,
  walkSeq: number,
  client: SimulationClient = simulationClient,
): Promise<WalkHeartbeat> {
  if (!isPositiveId(sessionSeq) || !isPositiveId(walkSeq)) {
    throw invalidWalk(WALK_START_FAILURE);
  }
  const response = await client.post(`/simulations/${sessionSeq}/walks/${walkSeq}/heartbeat`);
  return readHeartbeat(readApiData(response.data));
}

function readResolvedWalkEvent(value: unknown): WalkActionResult['resolvedEvent'] {
  if (value == null) {
    return null;
  }
  if (typeof value !== 'object' || Array.isArray(value)) {
    throw invalidWalk(WALK_ACTION_FAILURE);
  }
  const event = value as Partial<NonNullable<WalkActionResult['resolvedEvent']>>;
  const scenarioTitle = readText(event.scenarioTitle);
  const resultText = event.resultText === null ? null : readText(event.resultText);
  if (
    !isPositiveId(event.occurrenceSeq) ||
    !scenarioTitle ||
    (event.resultText !== null && !resultText) ||
    !isStatDelta(event.eventStatDelta) ||
    typeof event.eventCost !== 'number' ||
    !Number.isInteger(event.eventCost) ||
    event.eventCost < 0
  ) {
    throw invalidWalk(WALK_ACTION_FAILURE);
  }
  return {
    occurrenceSeq: event.occurrenceSeq,
    scenarioTitle,
    resultText,
    eventStatDelta: event.eventStatDelta,
    eventCost: event.eventCost,
  };
}

function readConsumedWalkItem(value: unknown): WalkActionResult['consumedItem'] {
  if (value == null) {
    return null;
  }
  if (typeof value !== 'object' || Array.isArray(value)) {
    throw invalidWalk(WALK_ACTION_FAILURE);
  }
  const item = value as Partial<NonNullable<WalkActionResult['consumedItem']>>;
  const itemName = readText(item.itemName);
  if (
    !isPositiveId(item.itemSeq) ||
    !itemName ||
    typeof item.remainingQuantity !== 'number' ||
    !Number.isInteger(item.remainingQuantity) ||
    item.remainingQuantity < 0
  ) {
    throw invalidWalk(WALK_ACTION_FAILURE);
  }
  return {
    itemSeq: item.itemSeq,
    itemName,
    remainingQuantity: item.remainingQuantity,
  };
}

function readWalkAction(value: unknown, request: WalkActionRequest): WalkActionResult {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidWalk(WALK_ACTION_FAILURE);
  }
  const action = value as Partial<WalkActionResult>;
  if (
    !isPositiveId(action.actionLogSeq) ||
    action.actionCode !== request.actionCode ||
    !isPositiveId(action.walkSeq) ||
    !isStatDelta(action.statDelta) ||
    typeof action.balance !== 'number' ||
    !Number.isInteger(action.balance)
  ) {
    throw invalidWalk(WALK_ACTION_FAILURE);
  }
  return {
    actionLogSeq: action.actionLogSeq,
    actionCode: request.actionCode,
    walkSeq: action.walkSeq,
    consumedItem: readConsumedWalkItem(action.consumedItem),
    statDelta: action.statDelta,
    stats: readHeartbeatStats(action.stats, WALK_ACTION_FAILURE),
    balance: action.balance,
    resolvedEvent: readResolvedWalkEvent(action.resolvedEvent),
  };
}

export function getSimulationWalkActionErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError && WALK_ACTION_ERROR_MESSAGES[error.code]) {
    return WALK_ACTION_ERROR_MESSAGES[error.code];
  }
  return WALK_ACTION_FAILURE;
}

export async function performSimulationWalkAction(
  sessionSeq: number,
  walkSeq: number,
  request: WalkActionRequest,
  client: SimulationClient = simulationClient,
): Promise<WalkActionResult> {
  if (
    !isPositiveId(sessionSeq) ||
    !isPositiveId(walkSeq) ||
    (request.actionCode === SimulationWalkAction.GiveSnack && !isPositiveId(request.itemSeq)) ||
    (request.actionCode === SimulationWalkAction.CleanUpPoop &&
      request.itemSeq !== undefined &&
      !isPositiveId(request.itemSeq))
  ) {
    throw invalidWalk(WALK_ACTION_FAILURE);
  }
  const body =
    request.actionCode === SimulationWalkAction.GiveSnack ||
    (request.actionCode === SimulationWalkAction.CleanUpPoop && request.itemSeq !== undefined)
      ? { actionCode: request.actionCode, itemSeq: request.itemSeq }
      : { actionCode: request.actionCode };
  const response = await client.post(`/simulations/${sessionSeq}/walks/${walkSeq}/actions`, body);
  return readWalkAction(readApiData(response.data), request);
}

export async function endSimulationWalk(
  sessionSeq: number,
  walkSeq: number,
  client: SimulationClient = simulationClient,
): Promise<EndedSimulationWalk> {
  if (!isPositiveId(sessionSeq) || !isPositiveId(walkSeq)) {
    throw invalidWalk();
  }
  const response = await client.post(`/simulations/${sessionSeq}/walks/${walkSeq}/end`);
  return readEndedWalk(readApiData(response.data));
}
