import type { AxiosInstance } from 'axios';

import { simulationClient } from '../api/client';
import { ApiClientError } from '../api/errors';
import { readApiData } from '../api/response';
import { SimulationCareAction, type SimulationCareRequest } from './actions';

export const CARE_STAT_KEYS = [
  'satiety',
  'hydration',
  'cleanliness',
  'bond',
  'calmness',
  'health',
  'energy',
] as const;

export type CareStatKey = (typeof CARE_STAT_KEYS)[number];
export type CareStatDelta = Partial<Record<CareStatKey, number>>;
export type CareStats = Record<CareStatKey, number> & {
  calculatedDttm: string;
};

export type ConsumedCareItem = {
  itemSeq: number;
  itemName: string;
  remainingQuantity: number;
};

export type ResolvedCareEvent = {
  occurrenceSeq: number;
  scenarioTitle: string;
  resultText: string;
  eventStatDelta: CareStatDelta;
  eventCost: number;
};

export type CareActionResult = {
  actionLogSeq: number;
  actionType: 'CARE';
  actionCode: SimulationCareAction;
  consumedItem: ConsumedCareItem | null;
  statDelta: CareStatDelta;
  stats: CareStats;
  balance: number;
  resolvedEvent: ResolvedCareEvent | null;
};

const CARE_ACTION_CODES = new Set<string>(Object.values(SimulationCareAction));

const CARE_ERROR_MESSAGES: Record<string, string> = {
  SIMULATION_NOT_FOUND: '진행 중인 모의입양을 찾지 못했어요.',
  SIMULATION_EQUIPMENT_MISSING: '필요한 기구가 없어요.',
  SIMULATION_ITEM_NOT_IN_INVENTORY: '필요한 아이템이 없어요.',
  SIMULATION_NOT_IN_PROGRESS: '진행 중인 모의입양이 없어요.',
  SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES: '이 동물에게 맞지 않는 물품이에요.',
  SIMULATION_VET_NOT_NEEDED: '지금은 병원 진료가 필요하지 않아요.',
  COMMON_INVALID_INPUT: '돌봄 행동을 확인해주세요.',
  SIMULATION_SHOP_ITEM_NOT_FOUND: '물품을 찾지 못했어요.',
};

const CARE_FAILURE = '돌봄을 진행하지 못했어요. 다시 시도해주세요.';

type SimulationClient = Pick<AxiosInstance, 'post'>;

function isPositiveId(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0;
}

function invalidCare(): ApiClientError {
  return new ApiClientError({ code: 'UNKNOWN_ERROR', message: CARE_FAILURE });
}

function isStatDelta(value: unknown): value is CareStatDelta {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return false;
  }
  return Object.entries(value).every(
    ([key, amount]) =>
      CARE_STAT_KEYS.includes(key as CareStatKey) &&
      typeof amount === 'number' &&
      Number.isFinite(amount),
  );
}

function readStats(value: unknown): CareStats | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null;
  }
  const stats = value as Partial<CareStats>;
  if (typeof stats.calculatedDttm !== 'string' || stats.calculatedDttm.length === 0) {
    return null;
  }
  if (!CARE_STAT_KEYS.every(key => typeof stats[key] === 'number' && Number.isFinite(stats[key]))) {
    return null;
  }
  return {
    satiety: stats.satiety as number,
    hydration: stats.hydration as number,
    cleanliness: stats.cleanliness as number,
    bond: stats.bond as number,
    calmness: stats.calmness as number,
    health: stats.health as number,
    energy: stats.energy as number,
    calculatedDttm: stats.calculatedDttm,
  };
}

function readConsumedItem(value: unknown): ConsumedCareItem | null {
  if (value === null) {
    return null;
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidCare();
  }
  const item = value as Partial<ConsumedCareItem>;
  if (
    !isPositiveId(item.itemSeq) ||
    typeof item.itemName !== 'string' ||
    item.itemName.length === 0 ||
    !Number.isInteger(item.remainingQuantity) ||
    (item.remainingQuantity ?? -1) < 0
  ) {
    throw invalidCare();
  }
  return {
    itemSeq: item.itemSeq,
    itemName: item.itemName,
    remainingQuantity: item.remainingQuantity as number,
  };
}

function readResolvedEvent(value: unknown): ResolvedCareEvent | null {
  if (value === null) {
    return null;
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidCare();
  }
  const event = value as Partial<ResolvedCareEvent>;
  if (
    !isPositiveId(event.occurrenceSeq) ||
    typeof event.scenarioTitle !== 'string' ||
    event.scenarioTitle.length === 0 ||
    typeof event.resultText !== 'string' ||
    !isStatDelta(event.eventStatDelta) ||
    !Number.isInteger(event.eventCost) ||
    (event.eventCost ?? -1) < 0
  ) {
    throw invalidCare();
  }
  return {
    occurrenceSeq: event.occurrenceSeq,
    scenarioTitle: event.scenarioTitle,
    resultText: event.resultText,
    eventStatDelta: event.eventStatDelta,
    eventCost: event.eventCost as number,
  };
}

function readCareAction(value: unknown): CareActionResult {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidCare();
  }
  const action = value as Partial<CareActionResult>;
  const stats = readStats(action.stats);
  if (
    !isPositiveId(action.actionLogSeq) ||
    action.actionType !== 'CARE' ||
    typeof action.actionCode !== 'string' ||
    !CARE_ACTION_CODES.has(action.actionCode) ||
    !isStatDelta(action.statDelta) ||
    !stats ||
    !Number.isInteger(action.balance)
  ) {
    throw invalidCare();
  }
  return {
    actionLogSeq: action.actionLogSeq,
    actionType: 'CARE',
    actionCode: action.actionCode as SimulationCareAction,
    consumedItem: readConsumedItem(action.consumedItem),
    statDelta: action.statDelta,
    stats,
    balance: action.balance as number,
    resolvedEvent: readResolvedEvent(action.resolvedEvent),
  };
}

export function careRequest(
  actionCode: SimulationCareAction,
  itemSeq?: number,
): SimulationCareRequest | null {
  switch (actionCode) {
    case SimulationCareAction.Water:
    case SimulationCareAction.Bathe:
    case SimulationCareAction.VetVisit:
      return { actionCode };
    case SimulationCareAction.Play:
      return itemSeq === undefined ? { actionCode } : { actionCode, itemSeq };
    case SimulationCareAction.Feed:
    case SimulationCareAction.CleanLitter:
    case SimulationCareAction.Snack:
    case SimulationCareAction.Medicine:
      return itemSeq === undefined ? null : { actionCode, itemSeq };
  }
}

function careActionPayload(request: SimulationCareRequest) {
  if ('itemSeq' in request && request.itemSeq !== undefined) {
    if (!isPositiveId(request.itemSeq)) {
      throw invalidCare();
    }
    return { actionCode: request.actionCode, itemSeq: request.itemSeq };
  }
  return { actionCode: request.actionCode };
}

export function getCareActionErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError && CARE_ERROR_MESSAGES[error.code]) {
    return CARE_ERROR_MESSAGES[error.code];
  }
  return CARE_FAILURE;
}

export async function performCareAction(
  sessionSeq: number,
  request: SimulationCareRequest,
  client: SimulationClient = simulationClient,
): Promise<CareActionResult> {
  if (!isPositiveId(sessionSeq)) {
    throw invalidCare();
  }
  const response = await client.post(
    `/simulations/${sessionSeq}/actions`,
    careActionPayload(request),
  );
  return readCareAction(readApiData(response.data));
}
