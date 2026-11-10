import type { AxiosInstance } from 'axios';

import { simulationClient } from '../api/client';
import { ApiClientError } from '../api/errors';
import { readApiData } from '../api/response';
import { CARE_STAT_KEYS, type CareStats } from './care';

export type SimulationStatsStatus = 'IN_PROGRESS' | 'COMPLETED' | 'ABANDONED' | 'FAILED';

export type SimulationStats = {
  sessionSeq: number;
  status: SimulationStatsStatus;
  stats: CareStats;
};

const STATUSES = new Set<SimulationStatsStatus>([
  'IN_PROGRESS',
  'COMPLETED',
  'ABANDONED',
  'FAILED',
]);

const STATS_FAILURE = '상태를 불러오지 못했어요. 다시 시도해주세요.';

const STATS_ERROR_MESSAGES: Record<string, string> = {
  SIMULATION_NOT_FOUND: '진행 중인 모의입양을 찾지 못했어요.',
  COMMON_INVALID_INPUT: '상태를 확인해주세요.',
};

type SimulationClient = Pick<AxiosInstance, 'get'>;

function isPositiveId(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0;
}

function invalidStats(): ApiClientError {
  return new ApiClientError({ code: 'UNKNOWN_ERROR', message: STATS_FAILURE });
}

function readText(value: unknown): string | null {
  return typeof value === 'string' && value.length > 0 ? value : null;
}

function readStats(value: unknown): CareStats {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidStats();
  }
  const stats = value as Partial<CareStats>;
  if (
    !readText(stats.calculatedDttm) ||
    !CARE_STAT_KEYS.every(key => typeof stats[key] === 'number' && Number.isInteger(stats[key]))
  ) {
    throw invalidStats();
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

function readSimulationStats(value: unknown, sessionSeq: number): SimulationStats {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidStats();
  }
  const body = value as Partial<SimulationStats>;
  if (body.sessionSeq !== sessionSeq || !STATUSES.has(body.status as SimulationStatsStatus)) {
    throw invalidStats();
  }
  return {
    sessionSeq,
    status: body.status as SimulationStatsStatus,
    stats: readStats(body.stats),
  };
}

export function getSimulationStatsErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError && STATS_ERROR_MESSAGES[error.code]) {
    return STATS_ERROR_MESSAGES[error.code];
  }
  return STATS_FAILURE;
}

export async function getSimulationStats(
  sessionSeq: number,
  client: SimulationClient = simulationClient,
): Promise<SimulationStats> {
  if (!isPositiveId(sessionSeq)) {
    throw invalidStats();
  }
  const response = await client.get(`/simulations/${sessionSeq}/stats`);
  return readSimulationStats(readApiData(response.data), sessionSeq);
}
