import { ApiClientError } from '../api/errors';
import {
  clearSimulationInventories,
  getInventoryErrorMessage,
  getSimulationInventory,
  invalidateSimulationInventory,
  loadInventoryOnEntry,
} from './inventory';

const inventory = {
  balance: 110000,
  expectedCost: 150000,
  equipment: {
    FOOD_BOWL: true,
    WATER_BOWL: false,
    TOY: { itemSeq: 3, itemName: '공', imageUrl: null },
  },
  items: [
    {
      itemSeq: 11,
      itemName: '건식 사료 1kg',
      category: 'FOOD',
      isConsumable: true,
      quantity: 5,
      imageUrl: 'https://example.com/food.png',
    },
    {
      itemSeq: 12,
      itemName: '간식',
      category: 'SNACK',
      isConsumable: true,
      quantity: 0,
      imageUrl: null,
    },
  ],
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

beforeEach(() => {
  clearSimulationInventories();
});

test('saves the inventory on the first visit and refetches after spending', async () => {
  const get = jest
    .fn()
    .mockResolvedValueOnce(envelope(inventory))
    .mockResolvedValueOnce(envelope({ ...inventory, balance: 90000 }));

  await expect(loadInventoryOnEntry(3001, { get })).resolves.toMatchObject({ balance: 110000 });
  expect(get).toHaveBeenCalledTimes(1);
  await expect(loadInventoryOnEntry(3001, { get })).resolves.toMatchObject({ balance: 110000 });
  expect(get).toHaveBeenCalledTimes(1);

  invalidateSimulationInventory(3001);
  await expect(loadInventoryOnEntry(3001, { get })).resolves.toMatchObject({ balance: 90000 });
  expect(get).toHaveBeenCalledTimes(2);
  await expect(loadInventoryOnEntry(3001, { get })).resolves.toMatchObject({ balance: 90000 });
  expect(get).toHaveBeenCalledTimes(2);
});

test('reads the balance, owned equipment and a used-up consumable', async () => {
  const get = jest.fn().mockResolvedValue(envelope(inventory));

  await expect(getSimulationInventory(3001, { get })).resolves.toEqual({
    balance: 110000,
    expectedCost: 150000,
    equipment: [
      { key: 'FOOD_BOWL', label: '밥그릇' },
      { key: 'TOY', label: '공', itemSeq: 3, imageUrl: null },
    ],
    items: inventory.items,
  });
  expect(get).toHaveBeenCalledWith('/simulations/3001/inventory');
});

test('keeps a negative balance', async () => {
  const get = jest.fn().mockResolvedValue(envelope({ ...inventory, balance: -5000 }));

  await expect(getSimulationInventory(3001, { get })).resolves.toMatchObject({ balance: -5000 });
});

test('rejects an inventory item whose category and consumable flag disagree', async () => {
  const get = jest.fn().mockResolvedValue(
    envelope({
      ...inventory,
      items: [{ ...inventory.items[0], isConsumable: false }],
    }),
  );

  await expect(getSimulationInventory(3001, { get })).rejects.toMatchObject({
    code: 'UNKNOWN_ERROR',
  });
});

test.each([
  ['SIMULATION_NOT_FOUND', '진행 중인 모의입양을 찾지 못했어요.'],
  ['SIMULATION_SHOP_ITEM_NOT_FOUND', '물품을 찾지 못했어요.'],
  ['COMMON_INVALID_INPUT', '보유 물품을 확인해주세요.'],
  ['SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES', '이 동물에게 맞지 않는 물품이에요.'],
  ['SIMULATION_EQUIPMENT_ALREADY_OWNED', '이미 가지고 있는 기구예요.'],
  ['SIMULATION_NOT_IN_PROGRESS', '진행 중인 모의입양이 없어요.'],
])('describes %s for inventory', (code, message) => {
  expect(getInventoryErrorMessage(new ApiClientError({ code, message: 'server' }))).toBe(message);
});

test('uses a generic message when inventory cannot be loaded', () => {
  expect(getInventoryErrorMessage(new Error('offline'))).toBe(
    '보유 물품을 불러오지 못했어요. 다시 시도해주세요.',
  );
});
