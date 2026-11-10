import {
  getShopDatabase,
  readShopSnapshot,
  replaceSessionShopCatalog,
} from '../../services/shop/shopDatabase';
import * as shopDatabase from '../../services/shop/shopDatabase';
import { useShopStore, cartTotal } from '../shopStore';
import { filterProducts, products } from '../../services/shop/catalog';
import { consumeCareSupplies } from '../../services/shop/consumption';
import {
  SimulationEventStatus,
  SimulationTriggerContext,
  SimulationTriggerType,
  type SimulationEvent,
} from '../../services/simulation/events';

const hospitalEvent = (id: number, time = Date.now()): SimulationEvent => ({
  occurrenceSeq: id,
  scenario: {
    scenarioSeq: 1,
    title: '진료 필요',
    description: '진료 안내',
    triggerContext: SimulationTriggerContext.Home,
    triggerType: SimulationTriggerType.Condition,
  },
  vetVisit: { cost: 30000 },
  walkSeq: null,
  status: SimulationEventStatus.Pending,
  occurredDttm: new Date(time).toISOString(),
  expiresDttm: new Date(time + 3600000).toISOString(),
  resolvedDttm: null,
  resolvedActionCode: null,
  resultText: null,
});

const session = {
  petId: 'dog-poodle',
  startedAt: 1,
  weeklyBudget: 50000,
  expiresAt: Date.now() + 86400000,
};
const shop = () => useShopStore.getState();
beforeEach(() => {
  const db = getShopDatabase();
  db.executeSync('DELETE FROM shop_sessions');
  db.executeSync('DELETE FROM simulation_shop_catalogs');
  db.executeSync("DELETE FROM products WHERE origin = 'server'");
  useShopStore.setState({ sessionKey: '', ready: false });
  shop().open(session);
});

it('orders hospital issues, persists payment and ignore, and prevents duplicate charges', () => {
  const time = Date.now();
  expect(shop().enqueueHospitalEvent(hospitalEvent(2, time))).toBe(true);
  expect(shop().enqueueHospitalEvent(hospitalEvent(1, time - 1000))).toBe(true);
  expect(shop().hospitalEvents?.map(event => event.occurrenceSeq)).toEqual([1, 2]);
  expect(shop().resolveHospitalEvent(1, true)).toBe(true);
  expect(shop().budget - shop().spent).toBe(20000);
  expect(shop().expenses).toEqual([
    expect.objectContaining({
      kind: 'event',
      title: '동물병원 진료비',
      detail: '진료 필요',
      amount: 30000,
      balanceAfter: 20000,
    }),
  ]);
  expect(shop().resolveHospitalEvent(1, true)).toBe(false);
  expect(shop().resolveHospitalEvent(2, false)).toBe(true);
  shop().open(session);
  expect(shop().spent).toBe(30000);
  expect(shop().expenses).toHaveLength(1);
  expect(
    shop().hospitalEvents?.every(event => event.status === SimulationEventStatus.Resolved),
  ).toBe(true);
  expect(shop().enqueueHospitalEvent(hospitalEvent(1))).toBe(false);
});

it('keeps the hospital issue pending and budget unchanged when saving fails', () => {
  shop().enqueueHospitalEvent(hospitalEvent(1));
  const save = jest.spyOn(shopDatabase, 'writeShopSnapshot').mockImplementationOnce(() => {
    throw new Error('disk full');
  });
  expect(shop().resolveHospitalEvent(1, true)).toBe(false);
  save.mockRestore();
  shop().open(session);
  expect(shop().spent).toBe(0);
  expect(shop().hospitalEvents?.[0].status).toBe(SimulationEventStatus.Pending);
});

it('contains all 32 products with valid prices and species filters', () => {
  expect(products).toHaveLength(32);
  expect(new Set(products.map(p => p.id)).size).toBe(32);
  expect(products.every(p => p.price > 0 && p.market.sources.length > 0)).toBe(true);
  expect(filterProducts('dog', '전체', '', '기본순').some(p => p.species === 'cat')).toBe(false);
  expect(filterProducts('cat', '필수 준비', '', '낮은 가격순').every(p => p.essential)).toBe(true);
  expect(filterProducts('cat', '전체', '물그릇', '기본순').map(p => p.id)).toEqual(['waterBowl']);
});

it('shows the shop list saved for this adoption', () => {
  replaceSessionShopCatalog(shop().sessionKey, 'DOG', [
    {
      displayOrder: 0,
      product: {
        id: '11',
        name: '건식 사료 1kg',
        qty: 1,
        unit: '개',
        species: 'dog',
        kind: 'consumable',
        category: '식사',
        description: '한 끼 분량씩 소모됩니다.',
        sprite: 0,
        price: 8000,
        placement: 'inventory',
        itemSeq: 11,
        serverCategory: 'FOOD',
        imageUrl: null,
        market: { gamePrice: 8000, basis: '', sources: [] },
      },
    },
  ]);
  shop().open(session);
  expect(products.map(item => item.id)).toEqual(['11']);
  expect(filterProducts('dog', '식사', '사료', '기본순').map(item => item.name)).toEqual([
    '건식 사료 1kg',
  ]);
  getShopDatabase().executeSync('DELETE FROM simulation_shop_catalogs');
  getShopDatabase().executeSync("DELETE FROM products WHERE origin = 'server'");
  shop().open(session);
});

it('applies a server purchase to the spent budget and inventory', () => {
  const product = {
    id: '11',
    name: '건식 사료 1kg',
    qty: 1,
    unit: '개',
    species: 'dog' as const,
    kind: 'consumable' as const,
    category: '식사',
    description: '한 끼 분량씩 소모됩니다.',
    sprite: 0,
    price: 8000,
    placement: 'inventory',
    itemSeq: 11,
    serverCategory: 'FOOD',
    imageUrl: null,
    market: { gamePrice: 8000, basis: '', sources: [] },
  };
  replaceSessionShopCatalog(shop().sessionKey, 'DOG', [{ displayOrder: 0, product }]);
  shop().open(session);
  expect(shop().setQuantity('11', 5, 'dog')).toBe(true);
  expect(shop().applyServerPurchase('11', { amount: 40000, inventoryQuantity: 5 })).toBeNull();
  expect(shop().spent).toBe(40000);
  expect(shop().inventory['11']).toBe(5);
  expect(shop().cart['11']).toBeUndefined();
  expect(shop().ownedItems.filter(item => item.productId === '11')).toHaveLength(5);
});

it('buys only walk bags, saves purchase history and budget, and preserves the cart', () => {
  shop().setQuantity('food', 2, 'dog');
  const selected = [...shop().selectedIds];
  expect(shop().buyWalkBags()).toBeNull();
  expect(shop().inventory.bags).toBe(10);
  expect(shop().ownedItems).toEqual([
    expect.objectContaining({ productId: 'bags', capacity: 10, remaining: 10 }),
  ]);
  expect(shop().budget - shop().spent).toBe(49500);
  expect(shop().cart).toEqual({ food: 2 });
  expect(shop().selectedIds).toEqual(selected);
  shop().open(session);
  expect(shop().inventory.bags).toBe(10);
  expect(shop().spent).toBe(500);
  expect(shop().consume({ bags: 1 })).toBe(true);
  expect(shop().inventory.bags).toBe(9);
});

it('rejects walk bag purchases without enough budget or after the session expires', () => {
  useShopStore.setState({ budget: 499, spent: 0 });
  expect(shop().buyWalkBags()).toBe('남은 예산이 부족해요.');
  expect(shop().spent).toBe(0);
  expect(shop().ownedItems).toEqual([]);
  expect(shop().inventory.bags).toBeUndefined();
  useShopStore.setState({ budget: 50000, expiresAt: Date.now() - 1 });
  expect(shop().buyWalkBags()).toBe('모의 입양 기간이 종료되었어요.');
  expect(shop().spent).toBe(0);
});

it('does not debit the budget or grant walk bags when saving fails', () => {
  const save = jest.spyOn(shopDatabase, 'writeShopSnapshot').mockImplementationOnce(() => {
    throw new Error('disk full');
  });
  try {
    expect(shop().buyWalkBags()).toContain('저장하지 못했어요');
    expect(shop().spent).toBe(0);
    expect(shop().inventory.bags).toBeUndefined();
    expect(shop().ownedItems).toEqual([]);
  } finally {
    save.mockRestore();
  }
  shop().open(session);
  expect(shop().spent).toBe(0);
  expect(shop().inventory.bags).toBeUndefined();
});

it('enforces species and valid quantities while allowing quantities above the former limit', () => {
  shop().setQuantity('litter', 1, 'dog');
  shop().setQuantity('food', -1, 'dog');
  shop().setQuantity('food', 1.5, 'dog');
  expect(shop().cart).toEqual({});
  expect(shop().setQuantity('food', 100, 'dog')).toBe(true);
  expect(shop().setQuantity('bowl', 2, 'dog')).toBe(true);
  expect(shop().cart).toEqual({ food: 100, bowl: 2 });
});

it('purchases selected items atomically, converts bundles and allows repeat durable purchases', () => {
  shop().setQuantity('food', 2, 'dog');
  shop().setQuantity('bowl', 1, 'dog');
  shop().selectAll(['food']);
  expect(cartTotal(shop().cart, shop().selectedIds)).toBe(21200);
  expect(shop().checkout('dog')).toBeNull();
  expect(shop().spent).toBe(21200);
  expect(shop().expenses).toEqual([
    expect.objectContaining({
      kind: 'purchase',
      title: '균형 잡힌 사료 × 2',
      detail: '식사 · 2개 구매',
      amount: 21200,
      balanceAfter: 28800,
    }),
  ]);
  expect(shop().inventory.food).toBe(24);
  expect(shop().cart).toEqual({ bowl: 1 });
  shop().selectAll(['bowl']);
  expect(shop().checkout('dog')).toBeNull();
  shop().setQuantity('bowl', 1, 'dog');
  expect(shop().cart).toEqual({ bowl: 1 });
  expect(shop().checkout('dog')).toBeNull();
  expect(shop().spent).toBe(41200);
  expect(shop().inventory.bowl).toBe(2);
});

it('accumulates repeated additions beyond 99 and restores added budget on reopening', () => {
  for (let i = 0; i < 12; i += 1) {
    expect(shop().addToCart('bowl', 10, 'dog')).toBe(true);
  }
  expect(shop().cart.bowl).toBe(120);
  expect(shop().addBudget(10000)).toBe(true);
  expect(shop().budget).toBe(60000);
  shop().open(session);
  expect(shop().budget).toBe(60000);
  expect(shop().cart.bowl).toBe(120);
  expect(shop().addBudget(0)).toBe(false);
  expect(shop().addBudget(-100)).toBe(false);
  expect(shop().addBudget(100_000_000)).toBe(false);
});

it('allows purchases beyond the budget and restores the negative balance', () => {
  shop().setQuantity('food', 5, 'dog');
  expect(shop().checkout('dog')).toBeNull();
  expect(shop().budget - shop().spent).toBe(-3000);
  expect(shop().cart).toEqual({});
  expect(shop().inventory.food).toBe(60);
  shop().open(session);
  expect(shop().budget - shop().spent).toBe(-3000);
});

it('rejects checkout after the adoption expires', () => {
  shop().setQuantity('food', 1, 'dog');
  useShopStore.setState({ expiresAt: Date.now() - 1 });
  expect(shop().checkout('dog')).toContain('종료');
  expect(shop().spent).toBe(0);
});

it('cleans selection on removal and isolates new adoption sessions', () => {
  shop().setQuantity('food', 1, 'dog');
  shop().remove(['food']);
  expect(shop().selectedIds).toEqual([]);
  shop().setQuantity('bowl', 1, 'dog');
  shop().open(session);
  expect(shop().cart.bowl).toBe(1);
  shop().open({ ...session, startedAt: 2 });
  expect(shop().cart).toEqual({});
  expect(shop().inventory).toEqual({});
});

it('restores purchased inventory and spending from SQLite without buying twice', () => {
  shop().setQuantity('food', 1, 'dog');
  shop().checkout('dog');
  expect(readShopSnapshot(shop().sessionKey)!.spent).toBe(10600);
  useShopStore.setState({ spent: 0, inventory: {} });
  shop().open(session);
  expect(shop().spent).toBe(10600);
  expect(shop().inventory.food).toBe(12);
  expect(shop().cart).toEqual({});
});

it('keeps the UI and saved state unchanged when the purchase transaction fails', () => {
  shop().setQuantity('food', 1, 'dog');
  const db = getShopDatabase();
  db.executeSync(
    "CREATE TRIGGER fail_purchase BEFORE INSERT ON inventory_items BEGIN SELECT RAISE(ABORT, 'disk failure'); END",
  );
  try {
    expect(shop().checkout('dog')).toContain('저장');
    expect(shop().spent).toBe(0);
    expect(shop().cart).toEqual({ food: 1 });
    expect(readShopSnapshot(shop().sessionKey)!.spent).toBe(0);
    expect(readShopSnapshot(shop().sessionKey)!.cart).toEqual({ food: 1 });
  } finally {
    db.executeSync('DROP TRIGGER fail_purchase');
  }
});

it('tracks each purchased package independently and restores remaining uses', () => {
  shop().setQuantity('food', 2, 'dog');
  expect(shop().checkout('dog')).toBeNull();
  expect(
    shop()
      .ownedItems.filter(i => i.productId === 'food')
      .map(i => i.remaining),
  ).toEqual([12, 12]);
  expect(shop().consume({ food: 13 })).toBe(true);
  expect(
    shop()
      .ownedItems.filter(i => i.productId === 'food')
      .map(i => i.remaining),
  ).toEqual([0, 11]);
  expect(shop().inventory.food).toBe(11);
  shop().open(session);
  expect(
    shop()
      .ownedItems.filter(i => i.productId === 'food')
      .map(i => i.remaining),
  ).toEqual([0, 11]);
  expect(shop().consume({ food: 12 })).toBe(false);
  expect(shop().inventory.food).toBe(11);
});

it('uses the requested item and atomically rejects incomplete cleaning supplies', () => {
  shop().setQuantity('food', 2, 'dog');
  shop().setQuantity('cleaner', 1, 'dog');
  shop().setQuantity('bowl', 1, 'dog');
  shop().checkout('dog');
  const packs = shop().ownedItems.filter(i => i.productId === 'food');
  expect(shop().consume({ food: 1 }, packs[1].id)).toBe(true);
  expect(
    shop()
      .ownedItems.filter(i => i.productId === 'food')
      .map(i => i.remaining),
  ).toEqual([12, 11]);
  expect(shop().consume({ cleaner: 1, pads: 1 })).toBe(false);
  expect(shop().inventory.cleaner).toBe(10);
  expect(shop().consume({ bowl: 1 })).toBe(false);
});

it('deducts snacks and cleaning supplies from the active pet inventory', () => {
  shop().setQuantity('treat', 1, 'dog');
  shop().setQuantity('cleaner', 1, 'dog');
  shop().setQuantity('pads', 1, 'dog');
  shop().checkout('dog');
  expect(consumeCareSupplies(session.petId, 'dog', 'treat')).toBe(true);
  expect(shop().inventory.treat).toBe(4);
  expect(consumeCareSupplies(session.petId, 'dog', 'clean')).toBe(true);
  expect(shop().inventory.cleaner).toBe(9);
  expect(shop().inventory.pads).toBe(9);
  expect(consumeCareSupplies('other-pet', 'dog', 'clean')).toBe(false);
  expect(shop().inventory.cleaner).toBe(9);
});
