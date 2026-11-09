import { ApiClientError } from '../api/errors';
import { getPurchaseErrorMessage, purchaseSimulationItem } from './purchase';

const purchased = {
  transactionSeq: 70001,
  item: { itemSeq: 11, itemName: '건식 사료 1kg', category: 'FOOD' },
  quantity: 5,
  amount: 40000,
  balanceAfter: 110000,
  inventoryQuantity: 5,
};

const envelope = (data: unknown) => ({
  data: {
    status: 201,
    code: 'SUCCESS',
    message: '요청이 성공했습니다.',
    data,
    errors: [],
    traceId: '4f2c9e1a7b3d4c5e',
  },
});

test('purchases the selected quantity and keeps a negative balance', async () => {
  const post = jest.fn().mockResolvedValue(envelope({ ...purchased, balanceAfter: -5000 }));

  await expect(
    purchaseSimulationItem(3001, { itemSeq: 11, quantity: 5 }, { post }),
  ).resolves.toEqual({ ...purchased, balanceAfter: -5000 });
  expect(post).toHaveBeenCalledWith('/simulations/3001/purchases', { itemSeq: 11, quantity: 5 });
});

test('rejects a purchase response for a different item', async () => {
  const post = jest.fn().mockResolvedValue(envelope(purchased));

  await expect(
    purchaseSimulationItem(3001, { itemSeq: 12, quantity: 1 }, { post }),
  ).rejects.toMatchObject({ code: 'UNKNOWN_ERROR' });
});

test.each([
  ['SIMULATION_NOT_FOUND', '진행 중인 모의입양을 찾지 못했어요.'],
  ['SIMULATION_SHOP_ITEM_NOT_FOUND', '물품을 찾지 못했어요.'],
  ['COMMON_INVALID_INPUT', '구매 수량을 확인해주세요.'],
  ['SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES', '이 동물에게 맞지 않는 물품이에요.'],
  ['SIMULATION_EQUIPMENT_ALREADY_OWNED', '이미 가지고 있는 기구예요.'],
  ['SIMULATION_NOT_IN_PROGRESS', '진행 중인 모의입양이 없어요.'],
])('describes %s for a purchase', (code, message) => {
  expect(getPurchaseErrorMessage(new ApiClientError({ code, message: 'server' }))).toBe(message);
});

test('uses a generic message when the purchase cannot be completed', () => {
  expect(getPurchaseErrorMessage(new Error('offline'))).toBe(
    '구매하지 못했어요. 다시 시도해주세요.',
  );
});
