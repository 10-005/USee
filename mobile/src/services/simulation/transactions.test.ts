import { ApiClientError } from '../api/errors';
import {
  clearSimulationTransactions,
  getTransactionErrorMessage,
  invalidateSimulationTransactions,
  loadMoreSimulationTransactions,
  loadTransactionsOnEntry,
} from './transactions';

const purchase = {
  transactionSeq: 70001,
  transactionType: 'PURCHASE',
  item: { itemSeq: 22, itemName: '균형 잡힌 사료', category: 'FOOD' },
  occurrenceSeq: null,
  quantity: 2,
  amount: 21200,
  balanceAfter: 128800,
  description: '균형 잡힌 사료 × 2',
  regDttm: '2026-11-01T15:10:00+09:00',
};

const event = {
  transactionSeq: 70002,
  transactionType: 'EVENT',
  item: null,
  occurrenceSeq: 90001,
  quantity: 1,
  amount: 30000,
  balanceAfter: 98800,
  description: '동물병원 진료비 · 축 처져 있어요',
  regDttm: '2026-11-02T10:12:00+09:00',
};

const envelope = (data: unknown) => ({
  data: {
    status: 200,
    code: 'SUCCESS',
    message: '요청이 성공했습니다.',
    data,
    errors: [],
    traceId: '4f2c9e1a7b3d4c5e',
  },
});

const page = (content: unknown[], pageNumber: number, hasNext: boolean, totalElements: number) =>
  envelope({
    content,
    page: pageNumber,
    size: 20,
    totalElements,
    totalPages: hasNext ? pageNumber + 2 : pageNumber + 1,
    hasNext,
  });

beforeEach(() => {
  clearSimulationTransactions();
});

test('loads the first page once after a purchase and keeps it on the next visit', async () => {
  const get = jest
    .fn()
    .mockResolvedValueOnce(page([event, purchase], 0, true, 3))
    .mockResolvedValueOnce(
      page(
        [
          {
            ...purchase,
            transactionSeq: 70000,
            regDttm: '2026-10-31T09:00:00+09:00',
          },
        ],
        1,
        false,
        3,
      ),
    );

  await expect(loadTransactionsOnEntry(3001, { get })).resolves.toBeNull();
  expect(get).not.toHaveBeenCalled();

  invalidateSimulationTransactions(3001);
  await expect(loadTransactionsOnEntry(3001, { get })).resolves.toMatchObject({
    page: 0,
    hasNext: true,
    totalElements: 3,
    items: [event, purchase],
  });
  expect(get).toHaveBeenCalledTimes(1);
  expect(get).toHaveBeenCalledWith('/simulations/3001/transactions', {
    params: { page: 0, size: 20, sort: 'regDttm,desc' },
  });

  await expect(loadTransactionsOnEntry(3001, { get })).resolves.toMatchObject({ page: 0 });
  expect(get).toHaveBeenCalledTimes(1);

  await expect(loadMoreSimulationTransactions(3001, { get })).resolves.toMatchObject({
    page: 1,
    hasNext: false,
    items: [event, purchase, expect.objectContaining({ transactionSeq: 70000 })],
  });
  expect(get).toHaveBeenLastCalledWith('/simulations/3001/transactions', {
    params: { page: 1, size: 20, sort: 'regDttm,desc' },
  });
});

test('a later purchase replaces the cached page on the next visit', async () => {
  const get = jest
    .fn()
    .mockResolvedValueOnce(page([purchase], 0, false, 1))
    .mockResolvedValueOnce(page([event, purchase], 0, false, 2));

  invalidateSimulationTransactions(3001);
  await loadTransactionsOnEntry(3001, { get });
  invalidateSimulationTransactions(3001);
  await expect(loadTransactionsOnEntry(3001, { get })).resolves.toMatchObject({
    totalElements: 2,
    items: [event, purchase],
  });
  expect(get).toHaveBeenCalledTimes(2);
});

test('does not keep another session cache when spending starts', async () => {
  const get = jest.fn().mockResolvedValue(page([purchase], 0, false, 1));
  invalidateSimulationTransactions(3001);
  await loadTransactionsOnEntry(3001, { get });
  invalidateSimulationTransactions(3002);
  await expect(loadTransactionsOnEntry(3001, { get })).resolves.toBeNull();
  await expect(loadTransactionsOnEntry(3002, { get })).resolves.toMatchObject({
    items: [purchase],
  });
});

test('rejects a page whose transactions repeat', async () => {
  const get = jest.fn().mockResolvedValue(page([purchase, purchase], 0, false, 2));
  invalidateSimulationTransactions(3001);
  await expect(loadTransactionsOnEntry(3001, { get })).rejects.toMatchObject({
    code: 'UNKNOWN_ERROR',
  });
});

test('keeps a negative balance after an event charge', async () => {
  const get = jest.fn().mockResolvedValue(page([{ ...event, balanceAfter: -5000 }], 0, false, 1));
  invalidateSimulationTransactions(3001);
  await expect(loadTransactionsOnEntry(3001, { get })).resolves.toMatchObject({
    items: [expect.objectContaining({ balanceAfter: -5000 })],
  });
});

test.each([
  ['SIMULATION_NOT_FOUND', '진행 중인 모의입양을 찾지 못했어요.'],
  ['COMMON_INVALID_INPUT', '지출 내역 조회 조건을 확인해주세요.'],
  ['UNKNOWN_ERROR', '지출 내역을 불러오지 못했어요. 다시 시도해주세요.'],
])('maps %s to a transaction message', (code, message) => {
  expect(getTransactionErrorMessage(new ApiClientError({ code, message: '서버' }))).toBe(message);
});
