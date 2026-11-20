import { AxiosError } from 'axios';
import type { InternalAxiosRequestConfig } from 'axios';

import { simulationClient } from '../api/client';
import { applyActiveSimulation, readCurrentActiveSimulation } from './activeSession';
import { invalidateSimulationReports } from './reports';
import { getActiveMockAdoption } from '../storage/mockAdoptionStorage';
import { installSimulationEndedHandler, subscribeSimulationEnded } from './sessionEnd';

jest.mock('./activeSession', () => ({
  applyActiveSimulation: jest.fn().mockResolvedValue(null),
  readCurrentActiveSimulation: jest.fn().mockReturnValue(null),
}));

jest.mock('./reports', () => ({
  invalidateSimulationReports: jest.fn(),
}));

jest.mock('../storage/mockAdoptionStorage', () => ({
  getActiveMockAdoption: jest.fn().mockResolvedValue(null),
}));

const savedAdapter = simulationClient.defaults.adapter;

function failWith(status: number, code: string) {
  simulationClient.defaults.adapter = async config => {
    const error = new AxiosError('fail');
    error.config = config as InternalAxiosRequestConfig;
    error.response = {
      status,
      statusText: 'error',
      headers: {},
      config: config as InternalAxiosRequestConfig,
      data: {
        status,
        code,
        message: 'server',
        data: null,
        errors: [],
        traceId: 'trace',
      },
    };
    throw error;
  };
}

beforeAll(() => {
  installSimulationEndedHandler();
});

afterEach(() => {
  simulationClient.defaults.adapter = savedAdapter;
  jest.mocked(applyActiveSimulation).mockClear();
  jest.mocked(invalidateSimulationReports).mockClear();
  jest.mocked(getActiveMockAdoption).mockReset();
  jest.mocked(getActiveMockAdoption).mockResolvedValue(null);
  jest.mocked(readCurrentActiveSimulation).mockReturnValue(null);
});

test('ends the local session and asks for a fresh report list on 409', async () => {
  jest.mocked(getActiveMockAdoption).mockResolvedValue({
    version: 1,
    petId: 'dog-1',
    petName: '초코',
    weeklyBudget: 35000,
    startedAt: 1,
    expiresAt: 2,
    sessionSeq: 3001,
  });
  const notices: Array<{ petName: string }> = [];
  const unsubscribe = subscribeSimulationEnded(notice => notices.push(notice));
  failWith(409, 'SIMULATION_NOT_IN_PROGRESS');

  await expect(simulationClient.get('/simulations/3001/actions')).rejects.toMatchObject({
    code: 'SIMULATION_NOT_IN_PROGRESS',
    status: 409,
  });
  for (let step = 0; step < 5; step += 1) {
    await Promise.resolve();
  }

  expect(applyActiveSimulation).toHaveBeenCalledWith(null);
  expect(invalidateSimulationReports).toHaveBeenCalled();
  expect(notices).toEqual([expect.objectContaining({ petId: 'dog-1', petName: '초코' })]);
  unsubscribe();
});

test('leaves the session alone for a different conflict', async () => {
  failWith(409, 'SIMULATION_WALK_NOT_IN_PROGRESS');
  await expect(
    simulationClient.post('/simulations/3001/walks/8001/heartbeat'),
  ).rejects.toMatchObject({ code: 'SIMULATION_WALK_NOT_IN_PROGRESS' });
  expect(applyActiveSimulation).not.toHaveBeenCalled();
  expect(invalidateSimulationReports).not.toHaveBeenCalled();
});
