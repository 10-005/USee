import type { AxiosInstance } from 'axios';

import { simulationClient } from '../api/client';
import { ApiClientError } from '../api/errors';
import { readApiData } from '../api/response';
import type { ShopItemCategory } from './shopItems';

export type ShopPurchaseLine = {
  itemSeq: number;
  quantity: number;
};

export type ShopPurchase = {
  transactionSeq: number;
  item: {
    itemSeq: number;
    itemName: string;
    category: ShopItemCategory;
  };
  quantity: number;
  amount: number;
  balanceAfter: number;
  inventoryQuantity: number;
};

export type ShopPurchaseResult = {
  purchases: ShopPurchase[];
  totalAmount: number;
  balanceAfter: number;
};

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

const PURCHASE_ERROR_MESSAGES: Record<string, string> = {
  SIMULATION_NOT_FOUND: '진행 중인 모의입양을 찾지 못했어요.',
  SIMULATION_SHOP_ITEM_NOT_FOUND: '물품을 찾지 못했어요.',
  COMMON_INVALID_INPUT: '구매 수량을 확인해주세요.',
  SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES: '이 동물에게 맞지 않는 물품이에요.',
  SIMULATION_EQUIPMENT_ALREADY_OWNED: '이미 가지고 있는 기구예요.',
  SIMULATION_NOT_IN_PROGRESS: '진행 중인 모의입양이 없어요.',
};

const PURCHASE_FAILURE = '구매하지 못했어요. 다시 시도해주세요.';

type SimulationClient = Pick<AxiosInstance, 'post'>;

function isPositiveId(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0;
}

function invalidPurchase(): ApiClientError {
  return new ApiClientError({ code: 'UNKNOWN_ERROR', message: PURCHASE_FAILURE });
}

function readPurchase(value: unknown, request: ShopPurchaseLine): ShopPurchase {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidPurchase();
  }
  const purchase = value as Partial<ShopPurchase>;
  const item = purchase.item;
  if (
    !isPositiveId(purchase.transactionSeq) ||
    !item ||
    typeof item !== 'object' ||
    item.itemSeq !== request.itemSeq ||
    typeof item.itemName !== 'string' ||
    item.itemName.trim().length === 0 ||
    !ITEM_CATEGORIES.has(item.category) ||
    !isPositiveId(purchase.quantity) ||
    typeof purchase.amount !== 'number' ||
    !Number.isInteger(purchase.amount) ||
    purchase.amount < 0 ||
    typeof purchase.balanceAfter !== 'number' ||
    !Number.isInteger(purchase.balanceAfter) ||
    typeof purchase.inventoryQuantity !== 'number' ||
    !Number.isInteger(purchase.inventoryQuantity) ||
    purchase.inventoryQuantity < 0
  ) {
    throw invalidPurchase();
  }
  return {
    transactionSeq: purchase.transactionSeq,
    item: { itemSeq: item.itemSeq, itemName: item.itemName, category: item.category },
    quantity: purchase.quantity,
    amount: purchase.amount,
    balanceAfter: purchase.balanceAfter,
    inventoryQuantity: purchase.inventoryQuantity,
  };
}

export function getPurchaseErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError && PURCHASE_ERROR_MESSAGES[error.code]) {
    return PURCHASE_ERROR_MESSAGES[error.code];
  }
  return PURCHASE_FAILURE;
}

function readPurchaseResult(value: unknown, items: ShopPurchaseLine[]): ShopPurchaseResult {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidPurchase();
  }
  const result = value as Partial<ShopPurchaseResult>;
  if (!Array.isArray(result.purchases) || result.purchases.length !== items.length) {
    throw invalidPurchase();
  }
  const purchases = result.purchases.map((purchase, index) => readPurchase(purchase, items[index]));
  const totalAmount = purchases.reduce((sum, purchase) => sum + purchase.amount, 0);
  const balanceAfter = purchases[purchases.length - 1]?.balanceAfter;
  if (
    typeof result.totalAmount !== 'number' ||
    !Number.isInteger(result.totalAmount) ||
    result.totalAmount !== totalAmount ||
    typeof result.balanceAfter !== 'number' ||
    !Number.isInteger(result.balanceAfter) ||
    result.balanceAfter !== balanceAfter
  ) {
    throw invalidPurchase();
  }
  return { purchases, totalAmount, balanceAfter };
}

export async function purchaseSimulationItems(
  sessionSeq: number,
  items: ShopPurchaseLine[],
  client: SimulationClient = simulationClient,
): Promise<ShopPurchaseResult> {
  if (
    !isPositiveId(sessionSeq) ||
    items.length < 1 ||
    items.length > 50 ||
    new Set(items.map(item => item.itemSeq)).size !== items.length ||
    items.some(
      item => !isPositiveId(item.itemSeq) || !isPositiveId(item.quantity) || item.quantity > 99,
    )
  ) {
    throw invalidPurchase();
  }
  const response = await client.post(`/simulations/${sessionSeq}/purchases`, {
    items: items.map(item => ({ itemSeq: item.itemSeq, quantity: item.quantity })),
  });
  return readPurchaseResult(readApiData(response.data), items);
}
