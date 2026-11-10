import type { AxiosInstance } from 'axios';

import { simulationClient } from '../api/client';
import { ApiClientError } from '../api/errors';
import { readApiData } from '../api/response';

export type StartSimulationRequest = {
  breedSeq: string;
  animalName: string;
  expectedCost: number;
};

export type SimulationSession = {
  sessionSeq: number;
};

const START_ERROR_MESSAGES: Record<string, string> = {
  COMMON_INVALID_INPUT: '이름 또는 예산을 확인해주세요.',
  SIMULATION_ACTIVE_ALREADY_EXISTS: '이미 진행 중인 모의입양이 있어요.',
  SIMULATION_BREED_NOT_FOUND: '선택한 품종을 찾지 못했어요.',
  SIMULATION_SPECIES_NOT_SUPPORTED: '아직 지원하지 않는 품종이에요.',
};

type SimulationClient = Pick<AxiosInstance, 'post'>;

export function getSimulationStartErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError && START_ERROR_MESSAGES[error.code]) {
    return START_ERROR_MESSAGES[error.code];
  }
  return '모의입양을 시작하지 못했어요. 다시 시도해주세요.';
}

export async function startSimulationSession(
  request: StartSimulationRequest,
  client: SimulationClient = simulationClient,
): Promise<SimulationSession> {
  const response = await client.post('/simulations', request);
  const session = readApiData<SimulationSession>(response.data);
  const sessionSeq = session?.sessionSeq;
  if (!Number.isInteger(sessionSeq) || sessionSeq <= 0) {
    throw new ApiClientError({
      code: 'UNKNOWN_ERROR',
      message: '모의입양을 시작하지 못했어요. 다시 시도해주세요.',
    });
  }
  return { sessionSeq };
}
