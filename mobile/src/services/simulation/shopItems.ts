import type { AxiosInstance } from 'axios';

import { simulationClient } from '../api/client';
import { ApiClientError } from '../api/errors';
import { readApiData } from '../api/response';
import type { Product } from '../shop/catalog';
import { hasSessionShopCatalog, replaceSessionShopCatalog } from '../shop/shopDatabase';
import { CARE_STAT_KEYS, type CareStatKey } from './care';

export type ShopSpeciesType = 'DOG' | 'CAT';

export type ShopItemCategory =
  | 'FOOD_BOWL'
  | 'WATER_BOWL'
  | 'LITTER_BOX'
  | 'FOOD'
  | 'SNACK'
  | 'TOY'
  | 'LITTER'
  | 'SHAMPOO'
  | 'MEDICINE';

export type ShopItem = {
  itemSeq: number;
  itemName: string;
  category: ShopItemCategory;
  isConsumable: boolean;
  price: number;
  description: string;
  imageUrl: string | null;
  speciesScope: 'ALL' | ShopSpeciesType;
  effects: Partial<Record<CareStatKey, number>> | null;
};

const EQUIPMENT = new Set<ShopItemCategory>(['FOOD_BOWL', 'WATER_BOWL', 'LITTER_BOX', 'TOY']);
const CONSUMABLES = new Set<ShopItemCategory>(['FOOD', 'SNACK', 'LITTER', 'SHAMPOO', 'MEDICINE']);
const CATEGORY_LABEL: Record<ShopItemCategory, string> = {
  FOOD_BOWL: '식사',
  WATER_BOWL: '식사',
  FOOD: '식사',
  SNACK: '간식',
  TOY: '놀이',
  LITTER_BOX: '위생',
  LITTER: '위생',
  SHAMPOO: '위생',
  MEDICINE: '생활',
};
const ROOM_ITEMS = new Set<ShopItemCategory>(['FOOD_BOWL', 'WATER_BOWL', 'LITTER_BOX', 'TOY']);

const SHOP_ITEMS_FAILURE = '상점 물품을 불러오지 못했어요. 다시 시도해주세요.';

type SimulationClient = Pick<AxiosInstance, 'get'>;

function invalidShopItems(): ApiClientError {
  return new ApiClientError({ code: 'UNKNOWN_ERROR', message: SHOP_ITEMS_FAILURE });
}

function isShopCategory(value: unknown): value is ShopItemCategory {
  return (
    typeof value === 'string' &&
    (EQUIPMENT.has(value as ShopItemCategory) || CONSUMABLES.has(value as ShopItemCategory))
  );
}

function readEffects(value: unknown): ShopItem['effects'] {
  if (value === null) {
    return null;
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidShopItems();
  }
  const effects: ShopItem['effects'] = {};
  for (const [key, amount] of Object.entries(value)) {
    if (
      !CARE_STAT_KEYS.includes(key as CareStatKey) ||
      typeof amount !== 'number' ||
      !Number.isFinite(amount)
    ) {
      throw invalidShopItems();
    }
    effects[key as CareStatKey] = amount;
  }
  return effects;
}

function readShopItem(value: unknown, speciesType?: ShopSpeciesType): ShopItem {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidShopItems();
  }
  const item = value as Partial<ShopItem>;
  const speciesScope = item.speciesScope;
  const category = item.category;
  const consumableMatches =
    isShopCategory(category) &&
    ((EQUIPMENT.has(category) && item.isConsumable === false) ||
      (CONSUMABLES.has(category) && item.isConsumable === true));
  if (
    typeof item.itemSeq !== 'number' ||
    !Number.isInteger(item.itemSeq) ||
    item.itemSeq <= 0 ||
    typeof item.itemName !== 'string' ||
    item.itemName.trim().length === 0 ||
    !consumableMatches ||
    !isShopCategory(category) ||
    typeof item.price !== 'number' ||
    !Number.isInteger(item.price) ||
    item.price <= 0 ||
    typeof item.description !== 'string' ||
    (item.imageUrl !== null && (typeof item.imageUrl !== 'string' || item.imageUrl.length === 0)) ||
    (speciesScope !== 'ALL' && speciesScope !== 'DOG' && speciesScope !== 'CAT') ||
    (speciesType !== undefined && speciesScope !== 'ALL' && speciesScope !== speciesType) ||
    !('effects' in item)
  ) {
    throw invalidShopItems();
  }
  return {
    itemSeq: item.itemSeq,
    itemName: item.itemName,
    category,
    isConsumable: item.isConsumable === true,
    price: item.price,
    description: item.description,
    imageUrl: item.imageUrl ?? null,
    speciesScope,
    effects: readEffects(item.effects),
  };
}

export function readShopItems(value: unknown, speciesType?: ShopSpeciesType): ShopItem[] {
  if (!Array.isArray(value)) {
    throw invalidShopItems();
  }
  const items = value.map(item => readShopItem(item, speciesType));
  if (new Set(items.map(item => item.itemSeq)).size !== items.length) {
    throw invalidShopItems();
  }
  return items;
}

export function toShopProduct(item: ShopItem): Product {
  const species = item.speciesScope === 'DOG' ? 'dog' : item.speciesScope === 'CAT' ? 'cat' : 'all';
  return {
    id: String(item.itemSeq),
    name: item.itemName,
    qty: 1,
    unit: '개',
    species,
    kind: item.isConsumable ? 'consumable' : 'durable',
    category: CATEGORY_LABEL[item.category],
    description: item.description,
    essential: false,
    sprite: 0,
    price: item.price,
    placement: ROOM_ITEMS.has(item.category) ? 'room' : 'inventory',
    imageUrl: item.imageUrl,
    itemSeq: item.itemSeq,
    serverCategory: item.category,
    effects: item.effects,
    market: { gamePrice: item.price, basis: '', sources: [] },
  };
}

export function getShopItemsErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError) {
    return SHOP_ITEMS_FAILURE;
  }
  return SHOP_ITEMS_FAILURE;
}

export async function listShopItems(
  speciesType?: ShopSpeciesType,
  client: SimulationClient = simulationClient,
): Promise<ShopItem[]> {
  const response = await client.get('/simulations/shop/items', {
    params: speciesType ? { speciesType } : undefined,
  });
  return readShopItems(readApiData(response.data), speciesType);
}

export async function ensureSessionShopCatalog(
  sessionKey: string,
  speciesType: ShopSpeciesType,
  client: SimulationClient = simulationClient,
): Promise<void> {
  if (hasSessionShopCatalog(sessionKey)) {
    return;
  }
  const items = await listShopItems(speciesType, client);
  replaceSessionShopCatalog(
    sessionKey,
    speciesType,
    items.map((item, displayOrder) => ({ product: toShopProduct(item), displayOrder })),
  );
}
