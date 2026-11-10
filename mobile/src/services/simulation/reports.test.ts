import { ApiClientError } from '../api/errors';
import {
  clearSimulationReports,
  getSimulationReport,
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

const detail = {
  sessionSeq: 3001,
  status: 'ABANDONED',
  isFinal: true,
  animalName: '초코',
  breed: {
    breedSeq: 21,
    breedName: '말티즈',
    speciesType: 'DOG',
    representativeImageUrl: 'https://storage.example.com/simulation/breeds/maltese.png',
  },
  speciesType: 'DOG',
  personality: 'FRIENDLY',
  startedDttm: '2026-11-01T14:00:00+09:00',
  endedDttm: '2026-11-03T10:20:00+09:00',
  expectedCost: 150000,
  totalSpent: 52800,
  difference: 97200,
  spentByCategory: {
    FOOD_BOWL: 10000,
    WATER_BOWL: 10000,
    LITTER_BOX: 0,
    TOY: 0,
    FURNITURE: 0,
    LEASH: 0,
    GROOMING_KIT: 0,
    FOOD: 21200,
    SNACK: 0,
    LITTER: 0,
    SHAMPOO: 0,
    MEDICINE: 0,
    POOP_BAG: 0,
    CATNIP: 0,
    EVENT: 11600,
  },
  dailySpent: { 1: 31200, 2: 21600, 3: 0, 4: 0, 5: 0, 6: 0, 7: 0 },
  finalStats: {
    satiety: 55,
    hydration: 60,
    cleanliness: 70,
    bond: 42,
    calmness: 52,
    health: 84,
    energy: 58,
  },
  actionCount: 12,
  eventCount: 1,
  eventResolvedCount: 1,
  walkCount: 2,
  summary: '  2일째에 돌봄을 그만뒀어요.  ',
};

const detailEnvelope = (data: unknown) => ({
  data: {
    status: 200,
    code: 'SUCCESS',
    message: '요청이 성공했습니다.',
    data,
    errors: [],
    traceId: '4f2c9e1a7b3d4c5e',
  },
});

test('reads one finished report for the selected session', async () => {
  const get = jest.fn().mockResolvedValue(detailEnvelope(detail));

  await expect(getSimulationReport(3001, { get })).resolves.toMatchObject({
    sessionSeq: 3001,
    status: 'ABANDONED',
    isFinal: true,
    animalName: '초코',
    breed: { breedSeq: '21', breedName: '말티즈', speciesType: 'DOG' },
    personality: 'FRIENDLY',
    endedDttm: '2026-11-03T10:20:00+09:00',
    totalSpent: 52800,
    difference: 97200,
    finalStats: { health: 84, bond: 42 },
    summary: '2일째에 돌봄을 그만뒀어요.',
  });
  expect(get).toHaveBeenCalledWith('/simulations/3001/report');
});

test('treats a blank summary as not ready yet', async () => {
  const get = jest.fn().mockResolvedValue(detailEnvelope({ ...detail, summary: '   ' }));

  await expect(getSimulationReport(3001, { get })).resolves.toMatchObject({ summary: null });
});

test('accepts an in-progress report that has not ended', async () => {
  const get = jest.fn().mockResolvedValue(
    detailEnvelope({
      ...detail,
      status: 'IN_PROGRESS',
      isFinal: false,
      endedDttm: null,
      summary: null,
    }),
  );

  await expect(getSimulationReport(3001, { get })).resolves.toMatchObject({
    status: 'IN_PROGRESS',
    isFinal: false,
    endedDttm: null,
    summary: null,
  });
});

test('rejects a report whose spending does not match the total', async () => {
  const get = jest.fn().mockResolvedValue(detailEnvelope({ ...detail, totalSpent: 1000 }));

  await expect(getSimulationReport(3001, { get })).rejects.toBeInstanceOf(ApiClientError);
});

test('rejects a report for a different session than requested', async () => {
  const get = jest.fn().mockResolvedValue(detailEnvelope(detail));

  await expect(getSimulationReport(3002, { get })).rejects.toBeInstanceOf(ApiClientError);
});

test('does not request a report when the session id is invalid', async () => {
  const get = jest.fn();

  await expect(getSimulationReport(0, { get })).rejects.toBeInstanceOf(ApiClientError);
  expect(get).not.toHaveBeenCalled();
});
