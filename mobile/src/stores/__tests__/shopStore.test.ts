import { getShopDatabase, readShopSnapshot } from '../../services/shop/shopDatabase';
import { useShopStore, cartTotal } from '../shopStore';
import { filterProducts, products } from '../../services/shop/catalog';

const session = {
  petId: 'dog-poodle',
  startedAt: 1,
  weeklyBudget: 50000,
  expiresAt: Date.now() + 86400000,
};
const shop = () => useShopStore.getState();
beforeEach(() => {
  getShopDatabase().executeSync('DELETE FROM shop_sessions');
  useShopStore.setState({ sessionKey: '', ready: false });
  shop().open(session);
});

it('contains all 32 products with valid prices and species filters', () => {
  expect(products).toHaveLength(32);
  expect(new Set(products.map(p => p.id)).size).toBe(32);
  expect(products.every(p => p.price > 0 && p.market.sources.length > 0)).toBe(true);
  expect(filterProducts('dog', '전체', '', '기본순').some(p => p.species === 'cat')).toBe(false);
  expect(filterProducts('cat', '필수 준비', '', '낮은 가격순').every(p => p.essential)).toBe(true);
  expect(filterProducts('cat', '전체', '물그릇', '기본순').map(p => p.id)).toEqual(['waterBowl']);
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

it('does not change budget or cart when funds are insufficient', () => {
  shop().setQuantity('food', 5, 'dog');
  expect(shop().checkout('dog')).toContain('예산');
  expect(shop().spent).toBe(0);
  expect(shop().cart.food).toBe(5);
  expect(shop().inventory).toEqual({});
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
