import type { AxiosInstance } from 'axios';

import { simulationClient } from '../api/client';
import { ApiClientError } from '../api/errors';
import { readApiData } from '../api/response';

export type SimulationReportBreed = {
  breedSeq: string;
  breedName: string;
  speciesType: 'DOG' | 'CAT';
  representativeImageUrl: string | null;
};

export type SimulationReport = {
  sessionSeq: number;
  animalName: string;
  breed: SimulationReportBreed;
  status: string;
  startedDttm: string;
  endedDttm: string;
  expectedCost: number;
  totalSpent: number;
  difference: number;
};

export type SimulationReportList = {
  reports: SimulationReport[];
  freshSessionSeqs: number[];
};

const REPORT_FAILURE = '리포트를 불러오지 못했어요. 다시 시도해주세요.';
const PAGE_SIZE = 20;

type SimulationClient = Pick<AxiosInstance, 'get'>;
type ReportCache = SimulationReportList & {
  loaded: boolean;
  stale: boolean;
};

let cache: ReportCache = { reports: [], freshSessionSeqs: [], loaded: false, stale: true };

function invalidReports(): ApiClientError {
  return new ApiClientError({ code: 'UNKNOWN_ERROR', message: REPORT_FAILURE });
}

function isPositiveId(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0;
}

function readBreedSeq(value: unknown): string {
  if (typeof value === 'string' && value.trim().length > 0) {
    return value.trim();
  }
  if (isPositiveId(value)) {
    return String(value);
  }
  throw invalidReports();
}

function readBreed(value: unknown): SimulationReportBreed {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidReports();
  }
  const breed = value as Partial<SimulationReportBreed>;
  if (
    typeof breed.breedName !== 'string' ||
    breed.breedName.trim().length === 0 ||
    (breed.speciesType !== 'DOG' && breed.speciesType !== 'CAT') ||
    (breed.representativeImageUrl !== null &&
      (typeof breed.representativeImageUrl !== 'string' ||
        breed.representativeImageUrl.length === 0))
  ) {
    throw invalidReports();
  }
  return {
    breedSeq: readBreedSeq(breed.breedSeq),
    breedName: breed.breedName,
    speciesType: breed.speciesType,
    representativeImageUrl: breed.representativeImageUrl,
  };
}

function readReport(value: unknown): SimulationReport {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidReports();
  }
  const report = value as Partial<SimulationReport>;
  if (
    !isPositiveId(report.sessionSeq) ||
    typeof report.animalName !== 'string' ||
    report.animalName.trim().length === 0 ||
    typeof report.status !== 'string' ||
    report.status.length === 0 ||
    typeof report.startedDttm !== 'string' ||
    report.startedDttm.length === 0 ||
    typeof report.endedDttm !== 'string' ||
    report.endedDttm.length === 0 ||
    typeof report.expectedCost !== 'number' ||
    !Number.isInteger(report.expectedCost) ||
    report.expectedCost < 0 ||
    typeof report.totalSpent !== 'number' ||
    !Number.isInteger(report.totalSpent) ||
    report.totalSpent < 0 ||
    typeof report.difference !== 'number' ||
    !Number.isInteger(report.difference)
  ) {
    throw invalidReports();
  }
  return {
    sessionSeq: report.sessionSeq,
    animalName: report.animalName,
    breed: readBreed(report.breed),
    status: report.status,
    startedDttm: report.startedDttm,
    endedDttm: report.endedDttm,
    expectedCost: report.expectedCost,
    totalSpent: report.totalSpent,
    difference: report.difference,
  };
}

function readReportPage(value: unknown): { content: SimulationReport[]; hasNext: boolean } {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidReports();
  }
  const page = value as Record<string, unknown>;
  if (!Array.isArray(page.content)) {
    throw invalidReports();
  }
  const content = page.content.map(item => readReport(item));
  if (new Set(content.map(item => item.sessionSeq)).size !== content.length) {
    throw invalidReports();
  }
  const pageNumber =
    typeof page.page === 'number' ? page.page : typeof page.number === 'number' ? page.number : 0;
  if (typeof page.hasNext === 'boolean') {
    return { content, hasNext: page.hasNext };
  }
  if (typeof page.last === 'boolean') {
    return { content, hasNext: !page.last };
  }
  if (typeof page.totalPages === 'number') {
    return { content, hasNext: pageNumber + 1 < page.totalPages };
  }
  return { content, hasNext: false };
}

export function getSimulationReportErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError) {
    return REPORT_FAILURE;
  }
  return REPORT_FAILURE;
}

export async function listSimulationReports(
  client: SimulationClient = simulationClient,
): Promise<SimulationReport[]> {
  const reports: SimulationReport[] = [];
  for (let page = 0; page < PAGE_SIZE; page += 1) {
    const response = await client.get('/simulations/reports', {
      params: { page, size: PAGE_SIZE, sort: 'endedDttm,desc' },
    });
    const body = readReportPage(readApiData(response.data));
    reports.push(...body.content);
    if (!body.hasNext) {
      return reports;
    }
  }
  return reports;
}

export function invalidateSimulationReports(): void {
  cache = { ...cache, stale: true };
}

export function clearSimulationReports(): void {
  cache = { reports: [], freshSessionSeqs: [], loaded: false, stale: true };
}

export async function loadSimulationReports(
  client: SimulationClient = simulationClient,
): Promise<SimulationReportList> {
  if (cache.loaded && !cache.stale) {
    return { reports: cache.reports, freshSessionSeqs: cache.freshSessionSeqs };
  }
  const reports = await listSimulationReports(client);
  const previous = new Set(cache.loaded ? cache.reports.map(report => report.sessionSeq) : []);
  const freshSessionSeqs = cache.loaded
    ? reports.filter(report => !previous.has(report.sessionSeq)).map(report => report.sessionSeq)
    : [];
  cache = { reports, freshSessionSeqs, loaded: true, stale: false };
  return { reports, freshSessionSeqs };
}
