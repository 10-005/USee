import { AxiosError } from 'axios';

import { simulationClient } from '../api/client';
import { ApiClientError } from '../api/errors';
import { isApiResponse } from '../api/response';
import { getActiveMockAdoption } from '../storage/mockAdoptionStorage';
import { mockAdoptionPets } from '../../screens/MockAdoption/data';
import { applyActiveSimulation, readCurrentActiveSimulation } from './activeSession';
import { invalidateSimulationReports } from './reports';

export const SIMULATION_ENDED_TITLE = '7일간의 모의 입양이 끝났어요!';
export const SIMULATION_ENDED_DESCRIPTION =
  '발급된 리포트를 확인해 내 모의 입양 결과를 확인해볼 수 있어요';

const AUTO_ENDED_CODE = 'SIMULATION_NOT_IN_PROGRESS';

export type SimulationEndedNotice = {
  petId: string | null;
  petName: string;
  imageUrl: string | null;
};

const listeners = new Set<(notice: SimulationEndedNotice) => void>();
let installed = false;
let finishing: Promise<void> | null = null;

export function isSimulationAutoEnded(error: unknown): boolean {
  if (error instanceof ApiClientError) {
    return error.status === 409 && error.code === AUTO_ENDED_CODE;
  }
  if (
    error instanceof AxiosError &&
    error.response?.status === 409 &&
    isApiResponse(error.response.data)
  ) {
    return error.response.data.code === AUTO_ENDED_CODE;
  }
  return false;
}

export function subscribeSimulationEnded(
  listener: (notice: SimulationEndedNotice) => void,
): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function installSimulationEndedHandler(): void {
  if (installed) {
    return;
  }
  installed = true;
  simulationClient.interceptors.response.use(
    response => response,
    error => {
      if (isSimulationAutoEnded(error)) {
        finishExpiredSimulation().catch(() => undefined);
      }
      return Promise.reject(error);
    },
  );
}

export function finishExpiredSimulation(): Promise<void> {
  if (finishing) {
    return finishing;
  }
  invalidateSimulationReports();
  finishing = closeExpiredSimulation().finally(() => {
    finishing = null;
  });
  return finishing;
}

async function closeExpiredSimulation(): Promise<void> {
  const active = readCurrentActiveSimulation();
  const local = await getActiveMockAdoption().catch(() => null);
  const pet = mockAdoptionPets.find(item => item.id === local?.petId);
  const notice: SimulationEndedNotice = {
    petId: local?.petId ?? null,
    petName: local?.petName ?? pet?.name ?? active?.animalName ?? '반려동물',
    imageUrl: active?.breed.representativeImageUrl ?? null,
  };
  try {
    await applyActiveSimulation(null);
  } finally {
    listeners.forEach(listener => listener(notice));
  }
}
