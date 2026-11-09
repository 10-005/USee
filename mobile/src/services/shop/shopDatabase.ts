import { open, type DB } from '@op-engineering/op-sqlite';
import seed from './products.json';
import type { SimulationEvent } from '../simulation/events';
import type { Product } from './catalog';

export type OwnedItem = { id: string; productId: string; capacity: number; remaining: number };
export type ShopSnapshot = {
  hospitalEvents?: SimulationEvent[];
  sessionKey: string;
  budget: number;
  expiresAt: number;
  spent: number;
  cart: Record<string, number>;
  inventory: Record<string, number>;
  ownedItems: OwnedItem[];
  selectedIds: string[];
};

let database: DB | undefined;

function transaction<T>(db: DB, action: () => T): T {
  db.executeSync('BEGIN IMMEDIATE');
  try {
    const result = action();
    db.executeSync('COMMIT');
    return result;
  } catch (error) {
    db.executeSync('ROLLBACK');
    throw error;
  }
}

export function migrateShopCartV2(db: DB): void {
  transaction(db, () => {
    db.executeSync(`CREATE TABLE cart_items_v2 (
      session_id TEXT NOT NULL REFERENCES shop_sessions(id) ON DELETE CASCADE,
      product_id TEXT NOT NULL REFERENCES products(id),
      quantity INTEGER NOT NULL CHECK(quantity >= 1),
      selected INTEGER NOT NULL CHECK(selected IN (0,1)), PRIMARY KEY(session_id, product_id))`);
    db.executeSync('INSERT INTO cart_items_v2 SELECT * FROM cart_items');
    db.executeSync('DROP TABLE cart_items');
    db.executeSync('ALTER TABLE cart_items_v2 RENAME TO cart_items');
    db.executeSync('PRAGMA user_version = 2');
  });
}

export function migrateShopBudgetV3(db: DB): void {
  // Rebuild the referenced parent table without cascading deletion of cart or inventory.
  db.executeSync('PRAGMA foreign_keys = OFF');
  try {
    transaction(db, () => {
      db.executeSync(`CREATE TABLE shop_sessions_v3 (
        id TEXT PRIMARY KEY, budget INTEGER NOT NULL CHECK(budget >= 0),
        spent INTEGER NOT NULL CHECK(spent >= 0), expires_at INTEGER NOT NULL)`);
      db.executeSync('INSERT INTO shop_sessions_v3 SELECT * FROM shop_sessions');
      db.executeSync('DROP TABLE shop_sessions');
      db.executeSync('ALTER TABLE shop_sessions_v3 RENAME TO shop_sessions');
      if (db.executeSync('PRAGMA foreign_key_check').rows.length) {
        throw new Error('Invalid shop references');
      }
      db.executeSync('PRAGMA user_version = 3');
    });
  } finally {
    db.executeSync('PRAGMA foreign_keys = ON');
  }
}

export function migrateShopOwnedItemsV4(db: DB): void {
  transaction(db, () => {
    db.executeSync(`CREATE TABLE owned_items (
      id TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES shop_sessions(id) ON DELETE CASCADE,
      product_id TEXT NOT NULL REFERENCES products(id), capacity INTEGER NOT NULL CHECK(capacity > 0),
      remaining INTEGER NOT NULL CHECK(remaining >= 0 AND remaining <= capacity))`);
    const rows = db.executeSync(
      'SELECT i.*, p.bundle_quantity FROM inventory_items i JOIN products p ON p.id=i.product_id',
    ).rows;
    for (const row of rows) {
      const capacity = Number(row.bundle_quantity);
      let remaining = Number(row.quantity);
      for (let index = 1; remaining > 0; index += 1) {
        const units = Math.min(capacity, remaining);
        db.executeSync('INSERT INTO owned_items VALUES (?, ?, ?, ?, ?)', [
          `${row.session_id}:${row.product_id}:${index}`,
          row.session_id,
          row.product_id,
          capacity,
          units,
        ]);
        remaining -= units;
      }
    }
    db.executeSync('PRAGMA user_version = 4');
  });
}

// A private on-device SQLite file; the JSON is used only as first-install seed.
export function getShopDatabase(): DB {
  if (database) {
    return database;
  }
  const db = open({ name: 'usee-pet-shop.sqlite' });
  try {
    db.executeSync('PRAGMA foreign_keys = ON');
    db.executeSync('PRAGMA journal_mode = WAL');
    const version = Number(db.executeSync('PRAGMA user_version').rows[0].user_version);
    if (version > 4) {
      throw new Error('Unsupported shop database version');
    }
    if (version === 0) {
      transaction(db, () => {
        db.executeSync(`CREATE TABLE products (
          id TEXT PRIMARY KEY, name TEXT NOT NULL, species TEXT NOT NULL,
          category TEXT NOT NULL, price INTEGER NOT NULL CHECK(price > 0),
          bundle_quantity INTEGER NOT NULL CHECK(bundle_quantity > 0),
          kind TEXT NOT NULL CHECK(kind IN ('durable','consumable')),
          essential INTEGER NOT NULL, display_order INTEGER NOT NULL, metadata TEXT NOT NULL)`);
        db.executeSync(`CREATE TABLE shop_sessions (
          id TEXT PRIMARY KEY, budget INTEGER NOT NULL CHECK(budget >= 0),
          spent INTEGER NOT NULL CHECK(spent >= 0), expires_at INTEGER NOT NULL)`);
        db.executeSync(`CREATE TABLE cart_items (
          session_id TEXT NOT NULL REFERENCES shop_sessions(id) ON DELETE CASCADE,
          product_id TEXT NOT NULL REFERENCES products(id),
          quantity INTEGER NOT NULL CHECK(quantity >= 1),
          selected INTEGER NOT NULL CHECK(selected IN (0,1)), PRIMARY KEY(session_id, product_id))`);
        db.executeSync(`CREATE TABLE inventory_items (
          session_id TEXT NOT NULL REFERENCES shop_sessions(id) ON DELETE CASCADE,
          product_id TEXT NOT NULL REFERENCES products(id),
          quantity INTEGER NOT NULL CHECK(quantity > 0), PRIMARY KEY(session_id, product_id))`);
        seed.forEach((p, index) =>
          db.executeSync('INSERT INTO products VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)', [
            p.id,
            p.name,
            p.species,
            p.category,
            p.price,
            p.qty,
            p.kind,
            p.essential ? 1 : 0,
            index,
            JSON.stringify(p),
          ]),
        );
        db.executeSync('PRAGMA user_version = 3');
      });
    }
    if (version === 1) {
      migrateShopCartV2(db);
    }
    if (version > 0 && version < 3) {
      migrateShopBudgetV3(db);
    }
    if (version < 4) {
      migrateShopOwnedItemsV4(db);
    }
    db.executeSync(`CREATE TABLE IF NOT EXISTS hospital_events (
      session_id TEXT PRIMARY KEY REFERENCES shop_sessions(id) ON DELETE CASCADE,
      events TEXT NOT NULL)`);
    database = db;
    return db;
  } catch (error) {
    db.close();
    throw error;
  }
}

export function readShopProducts(): Product[] {
  return getShopDatabase()
    .executeSync('SELECT * FROM products ORDER BY display_order')
    .rows.map(
      row =>
        ({
          ...JSON.parse(String(row.metadata)),
          id: String(row.id),
          name: String(row.name),
          species: row.species,
          category: String(row.category),
          price: Number(row.price),
          qty: Number(row.bundle_quantity),
          kind: row.kind,
          essential: row.essential === 1,
        } as Product),
    );
}

export function readShopSnapshot(sessionKey: string): ShopSnapshot | null {
  const db = getShopDatabase();
  const row = db.executeSync('SELECT * FROM shop_sessions WHERE id = ?', [sessionKey]).rows[0];
  if (!row) {
    return null;
  }
  const cartRows = db.executeSync('SELECT * FROM cart_items WHERE session_id = ?', [
    sessionKey,
  ]).rows;
  const inventoryRows = db.executeSync('SELECT * FROM inventory_items WHERE session_id = ?', [
    sessionKey,
  ]).rows;
  const ownedRows = db.executeSync(
    'SELECT * FROM owned_items WHERE session_id = ? ORDER BY rowid',
    [sessionKey],
  ).rows;
  return {
    hospitalEvents: JSON.parse(
      String(
        db.executeSync('SELECT events FROM hospital_events WHERE session_id = ?', [sessionKey])
          .rows[0]?.events ?? '[]',
      ),
    ),
    sessionKey,
    budget: Number(row.budget),
    spent: Number(row.spent),
    expiresAt: Number(row.expires_at),
    cart: Object.fromEntries(cartRows.map(r => [String(r.product_id), Number(r.quantity)])),
    selectedIds: cartRows.filter(r => r.selected === 1).map(r => String(r.product_id)),
    inventory: Object.fromEntries(
      inventoryRows.map(r => [String(r.product_id), Number(r.quantity)]),
    ),
    ownedItems: ownedRows.map(r => ({
      id: String(r.id),
      productId: String(r.product_id),
      capacity: Number(r.capacity),
      remaining: Number(r.remaining),
    })),
  };
}

// Commit budget, basket, selection and inventory together before updating the UI.
export function writeShopSnapshot(state: ShopSnapshot): void {
  const db = getShopDatabase();
  transaction(db, () => {
    db.executeSync(
      `INSERT INTO shop_sessions VALUES (?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET budget=excluded.budget, spent=excluded.spent, expires_at=excluded.expires_at`,
      [state.sessionKey, state.budget, state.spent, state.expiresAt],
    );
    db.executeSync(
      `INSERT INTO hospital_events VALUES (?, ?)
      ON CONFLICT(session_id) DO UPDATE SET events=excluded.events`,
      [state.sessionKey, JSON.stringify(state.hospitalEvents ?? [])],
    );
    db.executeSync('DELETE FROM cart_items WHERE session_id = ?', [state.sessionKey]);
    db.executeSync('DELETE FROM inventory_items WHERE session_id = ?', [state.sessionKey]);
    db.executeSync('DELETE FROM owned_items WHERE session_id = ?', [state.sessionKey]);
    for (const [id, quantity] of Object.entries(state.cart)) {
      db.executeSync('INSERT INTO cart_items VALUES (?, ?, ?, ?)', [
        state.sessionKey,
        id,
        quantity,
        state.selectedIds.includes(id) ? 1 : 0,
      ]);
    }
    for (const [id, quantity] of Object.entries(state.inventory)) {
      if (quantity === 0) {
        continue;
      }
      db.executeSync('INSERT INTO inventory_items VALUES (?, ?, ?)', [
        state.sessionKey,
        id,
        quantity,
      ]);
    }
    for (const item of state.ownedItems) {
      db.executeSync('INSERT INTO owned_items VALUES (?, ?, ?, ?, ?)', [
        item.id,
        state.sessionKey,
        item.productId,
        item.capacity,
        item.remaining,
      ]);
    }
  });
}
