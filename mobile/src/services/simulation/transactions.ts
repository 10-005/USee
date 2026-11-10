import type { AxiosInstance } from 'axios';

import { simulationClient } from '../api/client';
import { ApiClientError } from '../api/errors';
import { readApiData } from '../api/response';
import type { ShopItemCategory } from './shopItems';

export type SimulationTransactionType = 'PURCHASE' | 'EVENT';

export type SimulationTransactionItem = {
  itemSeq: number;
  itemName: string;
  category: ShopItemCategory;
};

export type SimulationTransaction = {
  transactionSeq: number;
  transactionType: SimulationTransactionType;
  item: SimulationTransactionItem | null;
  occurrenceSeq: number | null;
  quantity: number;
  amount: number;
  balanceAfter: number;
  description: string | null;
  regDttm: string | null;
};

export type SimulationTransactionList = {
  items: SimulationTransaction[];
  page: number;
  hasNext: boolean;
  totalElements: number;
};

const PAGE_SIZE = 20;
const SORT = 'regDttm,desc';

const ITEM_CATEGORIES = new Set<ShopItemCategory>([
  'FOOD_BOWL',
  'WATER_BOWL',
  'LITTER_BOX',
  'TOY',
  'FURNITURE',
  'LEASH',
  'GROOMING_KIT',
  'FOOD',
  'SNACK',
  'LITTER',
  'SHAMPOO',
  'MEDICINE',
  'POOP_BAG',
  'CATNIP',
]);

const TRANSACTION_ERROR_MESSAGES: Record<string, string> = {
  SIMULATION_NOT_FOUND: '진행 중인 모의입양을 찾지 못했어요.',
  COMMON_INVALID_INPUT: '지출 내역 조회 조건을 확인해주세요.',
};

const TRANSACTION_FAILURE = '지출 내역을 불러오지 못했어요. 다시 시도해주세요.';

type SimulationClient = Pick<AxiosInstance, 'get'>;
type TransactionQuery = { page: number };

type TransactionCache = SimulationTransactionList & {
  sessionSeq: number | null;
  loaded: boolean;
  stale: boolean;
};

const emptyList = (): SimulationTransactionList => ({
  items: [],
  page: 0,
  hasNext: false,
  totalElements: 0,
});

let cache: TransactionCache = {
  ...emptyList(),
  sessionSeq: null,
  loaded: false,
  stale: false,
};

function isPositiveId(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0;
}

function invalidTransactions(): ApiClientError {
  return new ApiClientError({ code: 'UNKNOWN_ERROR', message: TRANSACTION_FAILURE });
}

function readItem(value: unknown): SimulationTransactionItem | null {
  if (value === null) {
    return null;
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidTransactions();
  }
  const item = value as Partial<SimulationTransactionItem>;
  if (
    !isPositiveId(item.itemSeq) ||
    typeof item.itemName !== 'string' ||
    item.itemName.trim().length === 0 ||
    !ITEM_CATEGORIES.has(item.category as ShopItemCategory)
  ) {
    throw invalidTransactions();
  }
  return {
    itemSeq: item.itemSeq,
    itemName: item.itemName,
    category: item.category as ShopItemCategory,
  };
}

function readTransaction(value: unknown): SimulationTransaction {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidTransactions();
  }
  const transaction = value as Partial<SimulationTransaction>;
  if (
    !isPositiveId(transaction.transactionSeq) ||
    (transaction.transactionType !== 'PURCHASE' && transaction.transactionType !== 'EVENT') ||
    (transaction.occurrenceSeq !== null && !isPositiveId(transaction.occurrenceSeq)) ||
    !isPositiveId(transaction.quantity) ||
    typeof transaction.amount !== 'number' ||
    !Number.isInteger(transaction.amount) ||
    transaction.amount < 0 ||
    typeof transaction.balanceAfter !== 'number' ||
    !Number.isInteger(transaction.balanceAfter) ||
    (transaction.description !== null &&
      (typeof transaction.description !== 'string' || transaction.description.length === 0)) ||
    (transaction.regDttm !== null &&
      (typeof transaction.regDttm !== 'string' || transaction.regDttm.length === 0)) ||
    !('item' in transaction) ||
    !('occurrenceSeq' in transaction) ||
    !('description' in transaction) ||
    !('regDttm' in transaction)
  ) {
    throw invalidTransactions();
  }
  return {
    transactionSeq: transaction.transactionSeq,
    transactionType: transaction.transactionType,
    item: readItem(transaction.item),
    occurrenceSeq: transaction.occurrenceSeq ?? null,
    quantity: transaction.quantity,
    amount: transaction.amount,
    balanceAfter: transaction.balanceAfter,
    description: transaction.description ?? null,
    regDttm: transaction.regDttm ?? null,
  };
}

export function readSimulationTransactionPage(
  value: unknown,
  requestedPage: number,
): SimulationTransactionList {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidTransactions();
  }
  const page = value as Record<string, unknown>;
  if (
    !Array.isArray(page.content) ||
    page.page !== requestedPage ||
    typeof page.hasNext !== 'boolean' ||
    typeof page.totalElements !== 'number' ||
    !Number.isInteger(page.totalElements) ||
    page.totalElements < 0
  ) {
    throw invalidTransactions();
  }
  const items = page.content.map(item => readTransaction(item));
  if (new Set(items.map(item => item.transactionSeq)).size !== items.length) {
    throw invalidTransactions();
  }
  return {
    items,
    page: requestedPage,
    hasNext: page.hasNext,
    totalElements: page.totalElements,
  };
}

function toList(state: TransactionCache): SimulationTransactionList {
  return {
    items: state.items,
    page: state.page,
    hasNext: state.hasNext,
    totalElements: state.totalElements,
  };
}

export function clearSimulationTransactions(): void {
  cache = { ...emptyList(), sessionSeq: null, loaded: false, stale: false };
}

export function invalidateSimulationTransactions(sessionSeq: number): void {
  if (!isPositiveId(sessionSeq)) {
    return;
  }
  if (cache.sessionSeq !== sessionSeq) {
    cache = { ...emptyList(), sessionSeq, loaded: false, stale: true };
    return;
  }
  cache = { ...cache, stale: true };
}

export function isSimulationTransactionsStale(sessionSeq: number): boolean {
  return cache.stale && cache.sessionSeq === sessionSeq;
}

export function readSimulationTransactionCache(
  sessionSeq: number,
): SimulationTransactionList | null {
  if (cache.sessionSeq !== sessionSeq || !cache.loaded) {
    return null;
  }
  return toList(cache);
}

export function getTransactionErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError && TRANSACTION_ERROR_MESSAGES[error.code]) {
    return TRANSACTION_ERROR_MESSAGES[error.code];
  }
  return TRANSACTION_FAILURE;
}

export async function getSimulationTransactions(
  sessionSeq: number,
  query: TransactionQuery,
  client: SimulationClient = simulationClient,
): Promise<SimulationTransactionList> {
  if (!isPositiveId(sessionSeq) || !Number.isInteger(query.page) || query.page < 0) {
    throw invalidTransactions();
  }
  const response = await client.get(`/simulations/${sessionSeq}/transactions`, {
    params: { page: query.page, size: PAGE_SIZE, sort: SORT },
  });
  return readSimulationTransactionPage(readApiData(response.data), query.page);
}

async function refreshSimulationTransactions(
  sessionSeq: number,
  client: SimulationClient,
): Promise<SimulationTransactionList> {
  const first = await getSimulationTransactions(sessionSeq, { page: 0 }, client);
  cache = { ...first, sessionSeq, loaded: true, stale: false };
  return first;
}

export async function loadTransactionsOnEntry(
  sessionSeq: number,
  client: SimulationClient = simulationClient,
): Promise<SimulationTransactionList | null> {
  if (!isSimulationTransactionsStale(sessionSeq)) {
    return readSimulationTransactionCache(sessionSeq);
  }
  return refreshSimulationTransactions(sessionSeq, client);
}

export async function loadMoreSimulationTransactions(
  sessionSeq: number,
  client: SimulationClient = simulationClient,
): Promise<SimulationTransactionList> {
  if (cache.sessionSeq !== sessionSeq || !cache.loaded || cache.stale || !cache.hasNext) {
    return readSimulationTransactionCache(sessionSeq) ?? emptyList();
  }
  const next = await getSimulationTransactions(sessionSeq, { page: cache.page + 1 }, client);
  const seen = new Set(cache.items.map(item => item.transactionSeq));
  const items = [...cache.items, ...next.items.filter(item => !seen.has(item.transactionSeq))];
  cache = {
    items,
    page: next.page,
    hasNext: next.hasNext,
    totalElements: next.totalElements,
    sessionSeq,
    loaded: true,
    stale: false,
  };
  return toList(cache);
}
