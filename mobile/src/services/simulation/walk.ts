import type { AxiosInstance } from 'axios';

import { simulationClient } from '../api/client';
import { ApiClientError } from '../api/errors';
import { readApiData } from '../api/response';

export type SimulationStatDelta = {
  energy: number;
  calmness: number;
  bond: number;
  satiety: number;
  hydration: number;
};

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
};

const FINISHED_WALK_CODES = new Set([
  'SIMULATION_WALK_NOT_IN_PROGRESS',
  'SIMULATION_WALK_NOT_FOUND',
  'SIMULATION_NOT_IN_PROGRESS',
]);

const STAT_DELTA_KEYS = ['energy', 'calmness', 'bond', 'satiety', 'hydration'] as const;

const WALK_END_FAILURE = '산책을 종료하지 못했어요. 다시 시도해주세요.';

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
  if (!value || typeof value !== 'object') {
    return false;
  }
  return STAT_DELTA_KEYS.every(key => Number.isFinite((value as SimulationStatDelta)[key]));
}

function invalidWalk(message = WALK_END_FAILURE): ApiClientError {
  return new ApiClientError({ code: 'UNKNOWN_ERROR', message });
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
