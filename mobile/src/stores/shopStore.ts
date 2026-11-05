import { create } from 'zustand';
import {
  availableProducts,
  findProduct,
  setShopCatalog,
  type Species,
} from '../services/shop/catalog';
import {
  readShopProducts,
  readShopSnapshot,
  writeShopSnapshot,
  type ShopSnapshot,
} from '../services/shop/shopDatabase';

export type ShopSession = {
  petId: string;
  startedAt: number;
  weeklyBudget: number;
  expiresAt: number;
};
type ShopState = ShopSnapshot & {
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
};
export const cartTotal = (cart: Record<string, number>, ids = Object.keys(cart)) =>
  ids.reduce((sum, id) => sum + (findProduct(id)?.price ?? 0) * (cart[id] ?? 0), 0);

const storageError = '기기에 저장하지 못했어요. 저장 공간을 확인하고 다시 시도해주세요.';

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
    sessionKey: '',
    budget: 0,
    expiresAt: 0,
    spent: 0,
    cart: {},
    inventory: {},
    selectedIds: [],
    ready: false,
    error: null,
    open: session => {
      setShopCatalog(readShopProducts());
      const sessionKey = `${session.petId}:${session.startedAt}`;
      const saved = readShopSnapshot(sessionKey);
      const snapshot: ShopSnapshot = {
        sessionKey,
        budget: saved?.budget ?? session.weeklyBudget,
        expiresAt: session.expiresAt,
        spent: saved?.spent ?? 0,
        cart: saved?.cart ?? {},
        inventory: saved?.inventory ?? {},
        selectedIds: saved?.selectedIds ?? [],
      };
      writeShopSnapshot(snapshot);
      set({ ...snapshot, ready: true, error: null });
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
      for (const id of ids) {
        inventory[id] = (inventory[id] ?? 0) + state.cart[id] * findProduct(id)!.qty;
      }
      return commit({
        inventory,
        spent: state.spent + total,
        cart: Object.fromEntries(Object.entries(state.cart).filter(([id]) => !ids.includes(id))),
        selectedIds: [],
      })
        ? null
        : storageError;
    },
  };
});
