import { create } from 'zustand';
import { SimulationEventStatus, type SimulationEvent } from '../services/simulation/events';
import {
  availableProducts,
  findProduct,
  setShopCatalog,
  type Product,
  type Species,
} from '../services/shop/catalog';
import {
  readSessionShopCatalog,
  readShopProducts,
  readShopSnapshot,
  writeShopSnapshot,
  type ShopExpense,
  type ShopSnapshot,
} from '../services/shop/shopDatabase';

export type ShopSession = {
  petId: string;
  startedAt: number;
  weeklyBudget: number;
  expiresAt: number;
};
type ShopState = ShopSnapshot & {
  enqueueHospitalEvent: (event: SimulationEvent) => boolean;
  resolveHospitalEvent: (id: number, pay: boolean) => boolean;
  ready: boolean;
  error: string | null;
  open: (session: ShopSession) => void;
  setQuantity: (id: string, quantity: number, species: Species) => boolean;
  addToCart: (id: string, quantity: number, species: Species) => boolean;
  addBudget: (amount: number) => boolean;
  toggle: (id: string) => void;
  selectAll: (ids: string[]) => void;
  remove: (ids: string[]) => void;
  checkout: (species: Species) => string | null;
  applyServerPurchase: (
    productId: string,
    purchase: { amount: number; inventoryQuantity: number },
  ) => string | null;
  buyWalkBags: () => string | null;
  consume: (requirements: Record<string, number>, ownedItemId?: string) => boolean;
};
export const cartTotal = (cart: Record<string, number>, ids = Object.keys(cart)) =>
  ids.reduce((sum, id) => sum + (findProduct(id)?.price ?? 0) * (cart[id] ?? 0), 0);

const storageError = '기기에 저장하지 못했어요. 저장 공간을 확인하고 다시 시도해주세요.';
let expenseSequence = 0;

function purchaseExpense(
  state: ShopSnapshot,
  product: Product,
  quantity: number,
  amount: number,
  spentBefore: number,
): ShopExpense {
  expenseSequence += 1;
  return {
    id: `${state.sessionKey}:purchase:${expenseSequence}`,
    kind: 'purchase',
    title: `${product.name} × ${quantity}`,
    detail: `${product.category} · ${quantity}개 구매`,
    amount,
    balanceAfter: state.budget - (spentBefore + amount),
    occurredAt: Date.now(),
  };
}

export const useShopStore = create<ShopState>((set, get) => {
  const commit = (change: Partial<ShopSnapshot>): boolean => {
    const state = { ...get(), ...change };
    try {
      writeShopSnapshot(state);
      set({ ...change, error: null });
      return true;
    } catch {
      set({ error: storageError });
      return false;
    }
  };
  return {
    hospitalEvents: [],
    sessionKey: '',
    budget: 0,
    expiresAt: 0,
    spent: 0,
    cart: {},
    inventory: {},
    ownedItems: [],
    selectedIds: [],
    expenses: [],
    ready: false,
    error: null,
    open: session => {
      const sessionKey = `${session.petId}:${session.startedAt}`;
      setShopCatalog(readSessionShopCatalog(sessionKey) ?? readShopProducts());
      const saved = readShopSnapshot(sessionKey);
      const snapshot: ShopSnapshot = {
        hospitalEvents: saved?.hospitalEvents ?? [],
        sessionKey,
        budget: saved?.budget ?? session.weeklyBudget,
        expiresAt: session.expiresAt,
        spent: saved?.spent ?? 0,
        cart: saved?.cart ?? {},
        inventory: saved?.inventory ?? {},
        ownedItems: saved?.ownedItems ?? [],
        selectedIds: saved?.selectedIds ?? [],
        expenses: saved?.expenses ?? [],
      };
      writeShopSnapshot(snapshot);
      set({ ...snapshot, ready: true, error: null });
    },
    enqueueHospitalEvent: event => {
      const state = get();
      if (
        !state.ready ||
        Date.now() >= state.expiresAt ||
        event.status !== SimulationEventStatus.Pending ||
        !event.vetVisit ||
        !Number.isSafeInteger(event.vetVisit.cost) ||
        event.vetVisit.cost < 0 ||
        !Number.isFinite(Date.parse(event.occurredDttm)) ||
        !(Date.parse(event.expiresDttm) > Date.now()) ||
        (state.hospitalEvents ?? []).some(item => item.occurrenceSeq === event.occurrenceSeq)
      ) {
        return false;
      }
      return commit({
        hospitalEvents: [...(state.hospitalEvents ?? []), event].sort(
          (a, b) =>
            Date.parse(a.occurredDttm) - Date.parse(b.occurredDttm) ||
            a.occurrenceSeq - b.occurrenceSeq,
        ),
      });
    },
    resolveHospitalEvent: (id, pay) => {
      const state = get();
      const event = state.hospitalEvents?.find(item => item.occurrenceSeq === id);
      if (
        !state.ready ||
        Date.now() >= state.expiresAt ||
        !event?.vetVisit ||
        event.status !== SimulationEventStatus.Pending ||
        !(Date.parse(event.expiresDttm) > Date.now())
      ) {
        return false;
      }
      const cost = pay ? event.vetVisit.cost : 0;
      expenseSequence += 1;
      return commit({
        spent: state.spent + cost,
        hospitalEvents: state.hospitalEvents?.map(item =>
          item.occurrenceSeq === id
            ? {
                ...item,
                status: SimulationEventStatus.Resolved,
                resolvedDttm: new Date().toISOString(),
                resultText: pay ? '병원비 지불 완료' : '무시함',
              }
            : item,
        ),
        ...(cost > 0
          ? {
              expenses: [
                ...(state.expenses ?? []),
                {
                  id: `${state.sessionKey}:event:${event.occurrenceSeq}:${expenseSequence}`,
                  kind: 'event' as const,
                  title: '동물병원 진료비',
                  detail: event.scenario.title,
                  amount: cost,
                  balanceAfter: state.budget - (state.spent + cost),
                  occurredAt: Date.now(),
                },
              ],
            }
          : {}),
      });
    },
    setQuantity: (id, quantity, species) => {
      const state = get();
      const p = availableProducts(species).find(item => item.id === id);
      if (
        !state.ready ||
        Date.now() >= state.expiresAt ||
        !p ||
        !Number.isSafeInteger(quantity) ||
        quantity < 0
      ) {
        return false;
      }
      const cart = { ...state.cart };
      if (quantity === 0) {
        delete cart[id];
      } else {
        cart[id] = quantity;
      }
      return commit({
        cart,
        selectedIds:
          quantity === 0
            ? state.selectedIds.filter(key => key !== id)
            : [...new Set([...state.selectedIds, id])],
      });
    },
    addToCart: (id, quantity, species) => {
      if (!Number.isSafeInteger(quantity) || quantity < 1) {
        return false;
      }
      return get().setQuantity(id, (get().cart[id] ?? 0) + quantity, species);
    },
    addBudget: amount => {
      const state = get();
      if (
        !state.ready ||
        Date.now() >= state.expiresAt ||
        !Number.isSafeInteger(amount) ||
        amount <= 0 ||
        state.budget + amount > 100_000_000
      ) {
        return false;
      }
      return commit({ budget: state.budget + amount });
    },
    toggle: id => {
      const state = get();
      if (state.cart[id]) {
        commit({
          selectedIds: state.selectedIds.includes(id)
            ? state.selectedIds.filter(key => key !== id)
            : [...state.selectedIds, id],
        });
      }
    },
    selectAll: ids => {
      commit({ selectedIds: ids.filter(id => get().cart[id]) });
    },
    remove: ids => {
      commit({
        cart: Object.fromEntries(Object.entries(get().cart).filter(([id]) => !ids.includes(id))),
        selectedIds: get().selectedIds.filter(id => !ids.includes(id)),
      });
    },
    checkout: species => {
      const state = get();
      if (!state.ready) {
        return '쇼핑 정보를 불러오는 중이에요.';
      }
      if (Date.now() >= state.expiresAt) {
        return '모의 입양 기간이 종료되었어요.';
      }
      const ids = state.selectedIds.filter(id => state.cart[id]);
      if (!ids.length) {
        return '구매할 상품을 선택해주세요.';
      }
      const allowed = availableProducts(species);
      if (
        ids.some(id => {
          const p = allowed.find(item => item.id === id);
          const n = state.cart[id];
          return !p || !Number.isSafeInteger(n) || n < 1;
        })
      ) {
        return '구매할 수 없는 상품이 있어요.';
      }
      const total = cartTotal(state.cart, ids);
      const inventory = { ...state.inventory };
      const ownedItems = [...state.ownedItems];
      const expenses = [...(state.expenses ?? [])];
      let spentBefore = state.spent;
      for (const id of ids) {
        const product = findProduct(id)!;
        const capacity = product.qty;
        const quantity = state.cart[id];
        const amount = product.price * quantity;
        expenses.push(purchaseExpense(state, product, quantity, amount, spentBefore));
        spentBefore += amount;
        const previousCount = ownedItems.filter(item => item.productId === id).length;
        for (let i = 1; i <= quantity; i += 1) {
          ownedItems.push({
            id: `${state.sessionKey}:${id}:${previousCount + i}`,
            productId: id,
            capacity,
            remaining: capacity,
          });
        }
        inventory[id] = (inventory[id] ?? 0) + quantity * capacity;
      }
      return commit({
        inventory,
        ownedItems,
        expenses,
        spent: state.spent + total,
        cart: Object.fromEntries(Object.entries(state.cart).filter(([id]) => !ids.includes(id))),
        selectedIds: [],
      })
        ? null
        : storageError;
    },
    applyServerPurchase: (productId, purchase) => {
      const state = get();
      const product = findProduct(productId);
      if (!state.ready) {
        return '쇼핑 정보를 불러오는 중이에요.';
      }
      if (Date.now() >= state.expiresAt) {
        return '모의 입양 기간이 종료되었어요.';
      }
      if (
        !product ||
        product.qty < 1 ||
        !Number.isInteger(purchase.amount) ||
        purchase.amount < 0 ||
        !Number.isInteger(purchase.inventoryQuantity) ||
        purchase.inventoryQuantity < 0 ||
        !Number.isSafeInteger(state.spent + purchase.amount)
      ) {
        return '구매 결과를 저장하지 못했어요. 다시 시도해주세요.';
      }
      const ownedItems = state.ownedItems.filter(item => item.productId !== productId);
      let remainingUses = purchase.inventoryQuantity;
      let index = 1;
      while (remainingUses > 0) {
        const chunk = Math.min(product.qty, remainingUses);
        ownedItems.push({
          id: `${state.sessionKey}:${productId}:${index}`,
          productId,
          capacity: product.qty,
          remaining: chunk,
        });
        remainingUses -= chunk;
        index += 1;
      }
      const quantity = state.cart[productId] > 0 ? state.cart[productId] : 1;
      return commit({
        spent: state.spent + purchase.amount,
        inventory: {
          ...state.inventory,
          [productId]: purchase.inventoryQuantity,
        },
        ownedItems,
        ...(purchase.amount > 0
          ? {
              expenses: [
                ...(state.expenses ?? []),
                purchaseExpense(state, product, quantity, purchase.amount, state.spent),
              ],
            }
          : {}),
        cart: Object.fromEntries(Object.entries(state.cart).filter(([id]) => id !== productId)),
        selectedIds: state.selectedIds.filter(id => id !== productId),
      })
        ? null
        : storageError;
    },
    buyWalkBags: () => {
      const state = get();
      if (!state.ready) {
        return '쇼핑 정보를 불러오는 중이에요.';
      }
      if (Date.now() >= state.expiresAt) {
        return '모의 입양 기간이 종료되었어요.';
      }
      const product =
        availableProducts('dog').find(item => item.serverCategory === 'POOP_BAG') ??
        availableProducts('dog').find(item => item.id === 'bags');
      if (!product) {
        return '배변봉투 상품 정보를 불러오지 못했어요.';
      }
      if (state.budget - state.spent < product.price) {
        return '남은 예산이 부족해요.';
      }
      const count = state.ownedItems.filter(item => item.productId === product.id).length;
      return commit({
        spent: state.spent + product.price,
        expenses: [
          ...(state.expenses ?? []),
          purchaseExpense(state, product, 1, product.price, state.spent),
        ],
        inventory: {
          ...state.inventory,
          [product.id]: (state.inventory[product.id] ?? 0) + product.qty,
        },
        ownedItems: [
          ...state.ownedItems,
          {
            id: `${state.sessionKey}:${product.id}:${count + 1}`,
            productId: product.id,
            capacity: product.qty,
            remaining: product.qty,
          },
        ],
      })
        ? null
        : storageError;
    },
    consume: (requirements, ownedItemId) => {
      const state = get();
      if (!state.ready || Date.now() >= state.expiresAt || !Object.keys(requirements).length) {
        return false;
      }
      const ownedItems = state.ownedItems.map(item => ({ ...item }));
      const inventory = { ...state.inventory };
      for (const [id, amount] of Object.entries(requirements)) {
        const product = findProduct(id);
        if (
          !product ||
          product.kind !== 'consumable' ||
          !Number.isSafeInteger(amount) ||
          amount < 1
        ) {
          return false;
        }
        const candidates = ownedItems.filter(
          item => item.productId === id && (!ownedItemId || item.id === ownedItemId),
        );
        if (candidates.reduce((sum, item) => sum + item.remaining, 0) < amount) {
          set({ error: `${product.name}이 부족해요. 상점에서 구매해주세요.` });
          return false;
        }
        let needed = amount;
        for (const item of candidates) {
          const used = Math.min(item.remaining, needed);
          item.remaining -= used;
          needed -= used;
        }
        inventory[id] = (inventory[id] ?? 0) - amount;
      }
      return commit({ ownedItems, inventory });
    },
  };
});
