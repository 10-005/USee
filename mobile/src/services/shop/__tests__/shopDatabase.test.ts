import {
  getShopDatabase,
  readShopProducts,
  readShopSnapshot,
  writeShopSnapshot,
  type ShopSnapshot,
  migrateShopCartV2,
  migrateShopBudgetV3,
  migrateShopOwnedItemsV4,
} from '../shopDatabase';
import { open } from '@op-engineering/op-sqlite';

const snapshot: ShopSnapshot = {
  sessionKey: 'database-test',
  budget: 50000,
  expiresAt: Date.now() + 86400000,
  spent: 0,
  cart: { food: 2, bowl: 1 },
  inventory: {},
  ownedItems: [],
  selectedIds: ['food'],
};
beforeEach(() => {
  getShopDatabase().executeSync('DELETE FROM shop_sessions');
});

it('migrates aggregate stock into independently counted items without losing remaining uses', () => {
  const db = open({ name: 'owned-migration-test.sqlite' });
  db.executeSync('PRAGMA foreign_keys=ON');
  db.executeSync('CREATE TABLE products (id TEXT PRIMARY KEY, bundle_quantity INTEGER)');
  db.executeSync('CREATE TABLE shop_sessions (id TEXT PRIMARY KEY)');
  db.executeSync(
    'CREATE TABLE inventory_items (session_id TEXT, product_id TEXT, quantity INTEGER)',
  );
  db.executeSync("INSERT INTO products VALUES ('food',12)");
  db.executeSync("INSERT INTO shop_sessions VALUES ('test')");
  db.executeSync("INSERT INTO inventory_items VALUES ('test','food',13)");
  migrateShopOwnedItemsV4(db);
  expect(db.executeSync('SELECT capacity,remaining FROM owned_items ORDER BY rowid').rows).toEqual([
    { capacity: 12, remaining: 12 },
    { capacity: 12, remaining: 1 },
  ]);
  expect(db.executeSync('PRAGMA foreign_key_check').rows).toEqual([]);
  expect(db.executeSync('PRAGMA user_version').rows[0].user_version).toBe(4);
  db.close();
});

it('seeds 32 distinct products once and reads the SQL columns', () => {
  const db = getShopDatabase();
  expect(readShopProducts()).toHaveLength(32);
  getShopDatabase();
  expect(db.executeSync('SELECT count(*) AS total FROM products').rows[0].total).toBe(32);
  db.executeSync('UPDATE products SET price=1234 WHERE id=?', ['food']);
  expect(readShopProducts().find(p => p.id === 'food')!.price).toBe(1234);
  db.executeSync('UPDATE products SET price=10600 WHERE id=?', ['food']);
  expect(db.executeSync('PRAGMA user_version').rows[0].user_version).toBe(4);
});

it('restores selection, bundle quantities, budget and inventory', () => {
  writeShopSnapshot(snapshot);
  expect(readShopSnapshot(snapshot.sessionKey)).toEqual(snapshot);
  const purchased = {
    ...snapshot,
    spent: 21200,
    cart: { bowl: 1 },
    selectedIds: [],
    inventory: { food: 24 },
  };
  writeShopSnapshot(purchased);
  expect(readShopSnapshot(snapshot.sessionKey)).toEqual(purchased);
});

it('rolls back budget, cart deletion and inventory together when any write fails', () => {
  writeShopSnapshot(snapshot);
  expect(() =>
    writeShopSnapshot({ ...snapshot, spent: 21200, cart: {}, inventory: { 'unknown-product': 1 } }),
  ).toThrow();
  expect(readShopSnapshot(snapshot.sessionKey)).toEqual(snapshot);
});

it('keeps sessions isolated and enforces relational constraints', () => {
  writeShopSnapshot(snapshot);
  expect(readShopSnapshot('other-session')).toBeNull();
  expect(() => writeShopSnapshot({ ...snapshot, cart: { food: 0 } })).toThrow();
  expect(readShopSnapshot(snapshot.sessionKey)).toEqual(snapshot);
});

it('migrates v1 cart rows without losing quantities or selection', () => {
  const db = open({ name: 'migration-test.sqlite' });
  db.executeSync('CREATE TABLE products (id TEXT PRIMARY KEY)');
  db.executeSync('CREATE TABLE shop_sessions (id TEXT PRIMARY KEY)');
  db.executeSync(
    'CREATE TABLE cart_items (session_id TEXT, product_id TEXT, quantity INTEGER CHECK(quantity BETWEEN 1 AND 99), selected INTEGER, PRIMARY KEY(session_id, product_id))',
  );
  db.executeSync("INSERT INTO products VALUES ('food')");
  db.executeSync("INSERT INTO shop_sessions VALUES ('test')");
  db.executeSync("INSERT INTO cart_items VALUES ('test','food',99,1)");
  db.executeSync('PRAGMA user_version=1');
  migrateShopCartV2(db);
  expect(db.executeSync('SELECT quantity, selected FROM cart_items').rows[0]).toEqual({
    quantity: 99,
    selected: 1,
  });
  db.executeSync('UPDATE cart_items SET quantity=120');
  expect(db.executeSync('SELECT quantity FROM cart_items').rows[0].quantity).toBe(120);
  expect(db.executeSync('PRAGMA user_version').rows[0].user_version).toBe(2);
  db.close();
});

it('migrates budget constraints while preserving session, basket and inventory references', () => {
  const db = open({ name: 'budget-migration-test.sqlite' });
  db.executeSync('PRAGMA foreign_keys=ON');
  db.executeSync('CREATE TABLE products (id TEXT PRIMARY KEY)');
  db.executeSync(
    'CREATE TABLE shop_sessions (id TEXT PRIMARY KEY, budget INTEGER, spent INTEGER CHECK(spent <= budget), expires_at INTEGER)',
  );
  db.executeSync(
    'CREATE TABLE cart_items (session_id TEXT REFERENCES shop_sessions(id) ON DELETE CASCADE, product_id TEXT REFERENCES products(id), quantity INTEGER, selected INTEGER)',
  );
  db.executeSync(
    'CREATE TABLE inventory_items (session_id TEXT REFERENCES shop_sessions(id) ON DELETE CASCADE, product_id TEXT REFERENCES products(id), quantity INTEGER)',
  );
  db.executeSync("INSERT INTO products VALUES ('food')");
  db.executeSync("INSERT INTO shop_sessions VALUES ('test',1000,500,12345)");
  db.executeSync("INSERT INTO cart_items VALUES ('test','food',2,1)");
  db.executeSync("INSERT INTO inventory_items VALUES ('test','food',12)");
  db.executeSync('PRAGMA user_version=2');
  migrateShopBudgetV3(db);
  expect(db.executeSync('SELECT quantity,selected FROM cart_items').rows[0]).toEqual({
    quantity: 2,
    selected: 1,
  });
  expect(db.executeSync('SELECT quantity FROM inventory_items').rows[0].quantity).toBe(12);
  db.executeSync('UPDATE shop_sessions SET spent=2000');
  expect(db.executeSync('SELECT budget-spent AS balance FROM shop_sessions').rows[0].balance).toBe(
    -1000,
  );
  expect(db.executeSync('PRAGMA foreign_key_check').rows).toEqual([]);
  expect(db.executeSync('PRAGMA foreign_keys').rows[0].foreign_keys).toBe(1);
  expect(db.executeSync('PRAGMA user_version').rows[0].user_version).toBe(3);
  db.close();
});
