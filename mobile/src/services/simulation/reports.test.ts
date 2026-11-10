import { ApiClientError } from '../api/errors';
import {
  clearSimulationReports,
  getSimulationReportErrorMessage,
  invalidateSimulationReports,
  loadSimulationReports,
} from './reports';

const breed = {
  breedSeq: '000018',
  breedName: '비숑 프리제',
  speciesType: 'DOG',
  representativeImageUrl: null,
};

const older = {
  sessionSeq: 3001,
  animalName: '초코',
  breed,
  status: 'ABANDONED',
  startedDttm: '2026-11-01T10:00:00+09:00',
  endedDttm: '2026-11-02T10:00:00+09:00',
  expectedCost: 150000,
  totalSpent: 40000,
  difference: 110000,
};

const newer = {
  ...older,
  sessionSeq: 3002,
  animalName: '나비',
  breed: { ...breed, breedSeq: '000172', breedName: '러시안 블루', speciesType: 'CAT' },
  endedDttm: '2026-11-03T10:00:00+09:00',
};

const envelope = (content: unknown[], hasNext = false) => ({
  data: {
    status: 200,
    code: 'SUCCESS',
    message: '요청이 성공했습니다.',
    data: { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, hasNext },
    errors: [],
    traceId: '4f2c9e1a7b3d4c5e',
  },
});

beforeEach(() => {
  clearSimulationReports();
});

test('requests the signed-in reports once and keeps that list until invalidated', async () => {
  const get = jest
    .fn()
    .mockResolvedValueOnce(envelope([older]))
    .mockResolvedValueOnce(envelope([newer, older]));

  await expect(loadSimulationReports({ get })).resolves.toEqual({
    reports: [older],
    freshSessionSeqs: [],
  });
  expect(get).toHaveBeenCalledWith('/simulations/reports', {
    params: { page: 0, size: 20, sort: 'endedDttm,desc' },
  });
  await loadSimulationReports({ get });
  expect(get).toHaveBeenCalledTimes(1);

  invalidateSimulationReports();
  await expect(loadSimulationReports({ get })).resolves.toEqual({
    reports: [newer, older],
    freshSessionSeqs: [3002],
  });
  expect(get).toHaveBeenCalledTimes(2);
});

test('reads the next page while the server says more reports remain', async () => {
  const get = jest
    .fn()
    .mockResolvedValueOnce(envelope([newer], true))
    .mockResolvedValueOnce(envelope([older]));

  await expect(loadSimulationReports({ get })).resolves.toEqual({
    reports: [newer, older],
    freshSessionSeqs: [],
  });
  expect(get).toHaveBeenNthCalledWith(2, '/simulations/reports', {
    params: { page: 1, size: 20, sort: 'endedDttm,desc' },
  });
});

test('clears the cached reports so the next login starts again', async () => {
  const get = jest.fn().mockResolvedValue(envelope([older]));
  await loadSimulationReports({ get });
  clearSimulationReports();
  await loadSimulationReports({ get });
  expect(get).toHaveBeenCalledTimes(2);
});

test('rejects a report without an end time', async () => {
  const get = jest.fn().mockResolvedValue(envelope([{ ...older, endedDttm: '' }]));

  await expect(loadSimulationReports({ get })).rejects.toBeInstanceOf(ApiClientError);
});

test('uses a generic message when reports cannot be loaded', () => {
  expect(getSimulationReportErrorMessage(new Error('offline'))).toBe(
    '리포트를 불러오지 못했어요. 다시 시도해주세요.',
  );
});
