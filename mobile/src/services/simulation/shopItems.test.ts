import { ApiClientError } from '../api/errors';
import {
  getShopDatabase,
  readSessionShopCatalog,
  readShopProducts,
  replaceSessionShopCatalog,
} from '../shop/shopDatabase';
import {
  ensureSessionShopCatalog,
  getShopItemsErrorMessage,
  listShopItems,
  type ShopItem,
} from './shopItems';

const bowl: ShopItem = {
  itemSeq: 1,
  itemName: '스테인리스 밥그릇',
  category: 'FOOD_BOWL',
  isConsumable: false,
  price: 12000,
  description: '한 번 사면 계속 쓰는 기구입니다.',
  imageUrl: 'https://example.com/simulation/items/food-bowl.png',
  speciesScope: 'ALL',
  effects: null,
};

const food: ShopItem = {
  itemSeq: 11,
  itemName: '건식 사료 1kg',
  category: 'FOOD',
  isConsumable: true,
  price: 8000,
  description: '한 끼 분량씩 소모됩니다.',
  imageUrl: null,
  speciesScope: 'DOG',
  effects: { satiety: 30, bond: 1 },
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

test('requests one species and keeps the server order', async () => {
  const get = jest.fn().mockResolvedValue(envelope([bowl, food]));

  await expect(listShopItems('DOG', { get })).resolves.toEqual([bowl, food]);
  expect(get).toHaveBeenCalledWith('/simulations/shop/items', { params: { speciesType: 'DOG' } });
});

test('requests every item when the species is omitted', async () => {
  const get = jest.fn().mockResolvedValue(envelope([bowl]));

  await listShopItems(undefined, { get });
  expect(get).toHaveBeenCalledWith('/simulations/shop/items', { params: undefined });
});

test('rejects an item outside the requested species', async () => {
  const get = jest.fn().mockResolvedValue(envelope([{ ...food, speciesScope: 'CAT' }]));

  await expect(listShopItems('DOG', { get })).rejects.toMatchObject({ code: 'UNKNOWN_ERROR' });
});

test('rejects equipment that is marked consumable', async () => {
  const get = jest.fn().mockResolvedValue(envelope([{ ...bowl, isConsumable: true }]));

  await expect(listShopItems('DOG', { get })).rejects.toBeInstanceOf(ApiClientError);
});

test('saves the catalog once for an adoption and reuses it', async () => {
  const get = jest.fn().mockResolvedValue(envelope([bowl, food]));
  const sessionKey = 'dog-1:shop-items';

  await ensureSessionShopCatalog(sessionKey, 'DOG', { get });
  await ensureSessionShopCatalog(sessionKey, 'DOG', { get });

  expect(get).toHaveBeenCalledTimes(1);
  expect(readSessionShopCatalog(sessionKey)?.map(item => item.itemSeq)).toEqual([1, 11]);
  expect(readSessionShopCatalog(sessionKey)?.[1]).toMatchObject({
    id: '11',
    name: '건식 사료 1kg',
    category: '식사',
    serverCategory: 'FOOD',
    species: 'dog',
    kind: 'consumable',
    imageUrl: null,
  });
  replaceSessionShopCatalog('dog-1:replaced', 'CAT', []);
  expect(readSessionShopCatalog(sessionKey)).toBeNull();
  expect(readShopProducts().some(item => item.id === 'food')).toBe(true);
  getShopDatabase().executeSync('DELETE FROM simulation_shop_catalogs');
  getShopDatabase().executeSync("DELETE FROM products WHERE origin = 'server'");
});

test('uses the same message when the shop list cannot be loaded', () => {
  expect(
    getShopItemsErrorMessage(new ApiClientError({ code: 'UNKNOWN_ERROR', message: 'x' })),
  ).toBe('상점 물품을 불러오지 못했어요. 다시 시도해주세요.');
  expect(getShopItemsErrorMessage(new Error('offline'))).toBe(
    '상점 물품을 불러오지 못했어요. 다시 시도해주세요.',
  );
});
