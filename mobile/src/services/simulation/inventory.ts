import type { AxiosInstance } from 'axios';

import { simulationClient } from '../api/client';
import { ApiClientError } from '../api/errors';
import { readApiData } from '../api/response';
import {
  clearSimulationInventoryRows,
  markSimulationInventoryRowStale,
  readSimulationInventoryRow,
  writeSimulationInventoryRow,
} from '../shop/shopDatabase';
import type { ShopItemCategory } from './shopItems';

export type InventoryItem = {
  itemSeq: number;
  itemName: string;
  category: ShopItemCategory;
  isConsumable: boolean;
  quantity: number;
  imageUrl: string | null;
};

export type OwnedEquipment = {
  key: string;
  label: string;
  itemSeq?: number;
  imageUrl?: string | null;
};

export type SimulationInventory = {
  balance: number;
  expectedCost: number;
  equipment: OwnedEquipment[];
  items: InventoryItem[];
};

const EQUIPMENT = new Set<ShopItemCategory>([
  'FOOD_BOWL',
  'WATER_BOWL',
  'LITTER_BOX',
  'TOY',
  'FURNITURE',
  'LEASH',
  'GROOMING_KIT',
]);
const CONSUMABLES = new Set<ShopItemCategory>([
  'FOOD',
  'SNACK',
  'LITTER',
  'SHAMPOO',
  'MEDICINE',
  'POOP_BAG',
  'CATNIP',
]);

const EQUIPMENT_LABEL: Record<string, string> = {
  FOOD_BOWL: '밥그릇',
  foodBowl: '밥그릇',
  WATER_BOWL: '물그릇',
  waterBowl: '물그릇',
  LITTER_BOX: '화장실',
  litterBox: '화장실',
  TOY: '장난감',
  toy: '장난감',
  FURNITURE: '가구',
  furniture: '가구',
  LEASH: '리드줄',
  leash: '리드줄',
  GROOMING_KIT: '위생 키트',
  groomingKit: '위생 키트',
};

const INVENTORY_ERROR_MESSAGES: Record<string, string> = {
  SIMULATION_NOT_FOUND: '진행 중인 모의입양을 찾지 못했어요.',
  SIMULATION_SHOP_ITEM_NOT_FOUND: '물품을 찾지 못했어요.',
  COMMON_INVALID_INPUT: '보유 물품을 확인해주세요.',
  SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES: '이 동물에게 맞지 않는 물품이에요.',
  SIMULATION_EQUIPMENT_ALREADY_OWNED: '이미 가지고 있는 기구예요.',
  SIMULATION_NOT_IN_PROGRESS: '진행 중인 모의입양이 없어요.',
};

const INVENTORY_FAILURE = '보유 물품을 불러오지 못했어요. 다시 시도해주세요.';

type SimulationClient = Pick<AxiosInstance, 'get'>;

function isPositiveId(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0;
}

function invalidInventory(): ApiClientError {
  return new ApiClientError({ code: 'UNKNOWN_ERROR', message: INVENTORY_FAILURE });
}

function isCategory(value: unknown): value is ShopItemCategory {
  return (
    typeof value === 'string' &&
    (EQUIPMENT.has(value as ShopItemCategory) || CONSUMABLES.has(value as ShopItemCategory))
  );
}

function categoryMatchesConsumable(category: ShopItemCategory, isConsumable: boolean): boolean {
  return (EQUIPMENT.has(category) && !isConsumable) || (CONSUMABLES.has(category) && isConsumable);
}

function readImageUrl(value: unknown): string | null {
  if (value === null) {
    return null;
  }
  if (typeof value !== 'string' || value.length === 0) {
    throw invalidInventory();
  }
  return value;
}

function readInventoryItem(value: unknown): InventoryItem {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidInventory();
  }
  const item = value as Partial<InventoryItem>;
  if (
    !isPositiveId(item.itemSeq) ||
    typeof item.itemName !== 'string' ||
    item.itemName.trim().length === 0 ||
    !isCategory(item.category) ||
    typeof item.isConsumable !== 'boolean' ||
    !categoryMatchesConsumable(item.category, item.isConsumable) ||
    typeof item.quantity !== 'number' ||
    !Number.isInteger(item.quantity) ||
    item.quantity < 0 ||
    !('imageUrl' in item)
  ) {
    throw invalidInventory();
  }
  return {
    itemSeq: item.itemSeq,
    itemName: item.itemName,
    category: item.category,
    isConsumable: item.isConsumable,
    quantity: item.quantity,
    imageUrl: readImageUrl(item.imageUrl),
  };
}

function readEquipmentEntry(key: string, value: unknown): OwnedEquipment | null {
  if (value === false || value === null) {
    return null;
  }
  if (value === true) {
    return { key, label: EQUIPMENT_LABEL[key] ?? key };
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidInventory();
  }
  const entry = value as Record<string, unknown>;
  if (entry.owned === false) {
    return null;
  }
  const itemName = typeof entry.itemName === 'string' ? entry.itemName : undefined;
  const label = itemName?.trim() || EQUIPMENT_LABEL[key];
  if (!label) {
    throw invalidInventory();
  }
  const itemSeq = entry.itemSeq === undefined ? undefined : entry.itemSeq;
  if (itemSeq !== undefined && !isPositiveId(itemSeq)) {
    throw invalidInventory();
  }
  return {
    key,
    label,
    ...(itemSeq === undefined ? {} : { itemSeq }),
    ...('imageUrl' in entry ? { imageUrl: readImageUrl(entry.imageUrl) } : {}),
  };
}

export function readSimulationInventory(value: unknown): SimulationInventory {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw invalidInventory();
  }
  const inventory = value as Record<string, unknown>;
  const items = inventory.items;
  if (
    typeof inventory.balance !== 'number' ||
    !Number.isInteger(inventory.balance) ||
    typeof inventory.expectedCost !== 'number' ||
    !Number.isInteger(inventory.expectedCost) ||
    inventory.expectedCost < 0 ||
    !inventory.equipment ||
    typeof inventory.equipment !== 'object' ||
    Array.isArray(inventory.equipment) ||
    !Array.isArray(items)
  ) {
    throw invalidInventory();
  }
  const readItems = items.map(item => readInventoryItem(item));
  if (new Set(readItems.map(item => item.itemSeq)).size !== readItems.length) {
    throw invalidInventory();
  }
  return {
    balance: inventory.balance,
    expectedCost: inventory.expectedCost,
    equipment: Object.entries(inventory.equipment).flatMap(([key, entry]) => {
      const owned = readEquipmentEntry(key, entry);
      return owned ? [owned] : [];
    }),
    items: readItems,
  };
}

export function getInventoryErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError && INVENTORY_ERROR_MESSAGES[error.code]) {
    return INVENTORY_ERROR_MESSAGES[error.code];
  }
  return INVENTORY_FAILURE;
}

export async function getSimulationInventory(
  sessionSeq: number,
  client: SimulationClient = simulationClient,
): Promise<SimulationInventory> {
  if (!isPositiveId(sessionSeq)) {
    throw invalidInventory();
  }
  const response = await client.get(`/simulations/${sessionSeq}/inventory`);
  return readSimulationInventory(readApiData(response.data));
}

function parseStoredInventory(payload: string): SimulationInventory | null {
  try {
    const value = JSON.parse(payload) as Partial<SimulationInventory>;
    if (
      typeof value.balance !== 'number' ||
      !Number.isInteger(value.balance) ||
      typeof value.expectedCost !== 'number' ||
      !Number.isInteger(value.expectedCost) ||
      value.expectedCost < 0 ||
      !Array.isArray(value.items) ||
      !Array.isArray(value.equipment)
    ) {
      return null;
    }
    const items = value.items.map(item => readInventoryItem(item));
    if (new Set(items.map(item => item.itemSeq)).size !== items.length) {
      return null;
    }
    const equipment = value.equipment.map(entry => {
      if (!entry || typeof entry !== 'object' || Array.isArray(entry)) {
        throw invalidInventory();
      }
      if (typeof entry.key !== 'string' || entry.key.length === 0) {
        throw invalidInventory();
      }
      if (typeof entry.label !== 'string' || entry.label.length === 0) {
        throw invalidInventory();
      }
      if (entry.itemSeq !== undefined && !isPositiveId(entry.itemSeq)) {
        throw invalidInventory();
      }
      return {
        key: entry.key,
        label: entry.label,
        ...(entry.itemSeq === undefined ? {} : { itemSeq: entry.itemSeq }),
        ...('imageUrl' in entry ? { imageUrl: readImageUrl(entry.imageUrl) } : {}),
      };
    });
    return {
      balance: value.balance,
      expectedCost: value.expectedCost,
      equipment,
      items,
    };
  } catch {
    return null;
  }
}

function readStoredInventory(
  sessionSeq: number,
): { inventory: SimulationInventory; stale: boolean } | null {
  const row = readSimulationInventoryRow(sessionSeq);
  if (!row) {
    return null;
  }
  const inventory = parseStoredInventory(row.payload);
  if (!inventory) {
    return null;
  }
  return { inventory, stale: row.stale };
}

export function clearSimulationInventories(): void {
  clearSimulationInventoryRows();
}

export function invalidateSimulationInventory(sessionSeq: number): void {
  if (!isPositiveId(sessionSeq)) {
    return;
  }
  markSimulationInventoryRowStale(sessionSeq);
}

export function isSimulationInventoryStale(sessionSeq: number): boolean {
  const stored = readStoredInventory(sessionSeq);
  return !stored || stored.stale;
}

export async function loadInventoryOnEntry(
  sessionSeq: number,
  client: SimulationClient = simulationClient,
): Promise<SimulationInventory | null> {
  const stored = readStoredInventory(sessionSeq);
  if (stored && !stored.stale) {
    return stored.inventory;
  }
  if (!isPositiveId(sessionSeq)) {
    return null;
  }
  const inventory = await getSimulationInventory(sessionSeq, client);
  writeSimulationInventoryRow(sessionSeq, JSON.stringify(inventory), false);
  return inventory;
}
