import type { AxiosInstance } from 'axios';

import { simulationClient } from '../api/client';
import { ApiClientError } from '../api/errors';
import { readApiData } from '../api/response';

export type AbandonedSimulation = {
  sessionSeq: number;
  status: 'ABANDONED';
  endedDttm: string;
  report: Record<string, unknown> & {
    summary: string | null;
  };
};

const ABANDON_ERROR_MESSAGES: Record<string, string> = {
  SIMULATION_NOT_IN_PROGRESS: '진행 중인 모의입양이 없어요.',
};

const CLOSED_SIMULATION_CODES = new Set(['SIMULATION_NOT_IN_PROGRESS']);

const ABANDON_FAILURE = '모의입양을 그만두지 못했어요. 다시 시도해주세요.';

type SimulationClient = Pick<AxiosInstance, 'post'>;

function isPositiveId(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0;
}

function invalidAbandon(): ApiClientError {
  return new ApiClientError({ code: 'UNKNOWN_ERROR', message: ABANDON_FAILURE });
}

function readAbandonedSimulation(value: unknown): AbandonedSimulation {
  if (!value || typeof value !== 'object') {
    throw invalidAbandon();
  }
  const abandoned = value as Partial<AbandonedSimulation>;
  const report = abandoned.report;
  if (
    !isPositiveId(abandoned.sessionSeq) ||
    abandoned.status !== 'ABANDONED' ||
    typeof abandoned.endedDttm !== 'string' ||
    abandoned.endedDttm.length === 0 ||
    !report ||
    typeof report !== 'object' ||
    Array.isArray(report) ||
    (report.summary !== null && typeof report.summary !== 'string')
  ) {
    throw invalidAbandon();
  }
  return {
    sessionSeq: abandoned.sessionSeq,
    status: 'ABANDONED',
    endedDttm: abandoned.endedDttm,
    report,
  };
}

export function getSimulationAbandonErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError && ABANDON_ERROR_MESSAGES[error.code]) {
    return ABANDON_ERROR_MESSAGES[error.code];
  }
  return ABANDON_FAILURE;
}

export function isSimulationAlreadyClosed(error: unknown): boolean {
  return error instanceof ApiClientError && CLOSED_SIMULATION_CODES.has(error.code);
}

export async function abandonSimulation(
  sessionSeq: number,
  client: SimulationClient = simulationClient,
): Promise<AbandonedSimulation> {
  if (!isPositiveId(sessionSeq)) {
    throw invalidAbandon();
  }
  const response = await client.post(`/simulations/${sessionSeq}/abandon`);
  return readAbandonedSimulation(readApiData(response.data));
}
