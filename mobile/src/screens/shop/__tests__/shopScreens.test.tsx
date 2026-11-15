import renderer, { act } from 'react-test-renderer';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { FlatList, Keyboard, StyleSheet } from 'react-native';
import { ShopScreen } from '../ShopScreen';
import { ProductDetailScreen } from '../ProductDetailScreen';
import { CartScreen } from '../CartScreen';
import { InventoryScreen } from '../InventoryScreen';
import { useShopStore } from '../../../stores/shopStore';
import { getShopDatabase, replaceSessionShopCatalog } from '../../../services/shop/shopDatabase';
import { findProduct } from '../../../services/shop/catalog';
import { ApiClientError } from '../../../services/api';
import { purchaseSimulationItems } from '../../../services/simulation/purchase';
import { loadInventoryOnEntry } from '../../../services/simulation/inventory';
import { loadTransactionsOnEntry } from '../../../services/simulation/transactions';
import {
  clearActiveSimulationRefresh,
  isActiveSimulationStale,
} from '../../../services/simulation/activeSession';
import { CommonActions } from '@react-navigation/native';
import { StackRouter, StackActions } from '@react-navigation/routers';
import { colors } from '../../../theme';

const mockShopSession: {
  pet: { id: string; name: string; category: 'dog' };
  session?: { sessionSeq: number };
  status: 'ready';
  retry: jest.Mock;
} = {
  pet: { id: 'dog-1', name: '비숑 프리제', category: 'dog' },
  status: 'ready',
  retry: jest.fn(),
};

jest.mock('../useShopSession', () => ({
  useShopSession: () => mockShopSession,
}));

jest.mock('../../../services/simulation/inventory', () => {
  const actual = jest.requireActual('../../../services/simulation/inventory');
  return {
    ...actual,
    loadInventoryOnEntry: jest.fn(async () => null),
  };
});

jest.mock('../../../services/simulation/purchase', () => {
  const actual = jest.requireActual('../../../services/simulation/purchase');
  return {
    ...actual,
    purchaseSimulationItems: jest.fn(),
  };
});

const mockShopFocusEffects = new Set<() => void | (() => void)>();
jest.mock('@react-navigation/native', () => ({
  CommonActions: jest.requireActual('@react-navigation/routers').CommonActions,
  useFocusEffect: (effect: () => void | (() => void)) => {
    require('react').useEffect(() => {
      mockShopFocusEffects.add(effect);
      const cleanup = effect();
      return () => {
        mockShopFocusEffects.delete(effect);
        if (typeof cleanup === 'function') {
          cleanup();
        }
      };
    }, [effect]);
  },
}));

jest.mock('../../../services/simulation/transactions', () => ({
  getTransactionErrorMessage: () => '지출 내역을 불러오지 못했어요. 다시 시도해주세요.',
  invalidateSimulationTransactions: jest.fn(),
  isSimulationTransactionsStale: jest.fn(() => false),
  loadMoreSimulationTransactions: jest.fn(async () => ({
    items: [],
    page: 0,
    hasNext: false,
    totalElements: 0,
  })),
  loadTransactionsOnEntry: jest.fn(async () => null),
}));
jest.mock('react-native/Libraries/Alert/Alert', () => ({
  __esModule: true,
  default: { alert: jest.fn() },
}));

function wrap(element: React.ReactNode) {
  return (
    <SafeAreaProvider
      initialMetrics={{
        frame: { height: 917, width: 412, x: 0, y: 0 },
        insets: { bottom: 0, left: 0, right: 0, top: 0 },
      }}
    >
      {element}
    </SafeAreaProvider>
  );
}
beforeEach(() => {
  const db = getShopDatabase();
  db.executeSync('DELETE FROM shop_sessions');
  db.executeSync('DELETE FROM simulation_shop_catalogs');
  db.executeSync("DELETE FROM products WHERE origin = 'server'");
  delete mockShopSession.session;
  clearActiveSimulationRefresh();
  jest.clearAllMocks();
  jest.mocked(loadTransactionsOnEntry).mockResolvedValue(null);
  useShopStore.setState({ sessionKey: '' });
  useShopStore
    .getState()
    .open({ petId: 'dog-1', startedAt: 1, expiresAt: Date.now() + 86400000, weeklyBudget: 50000 });
});

it('searches the list and opens details without quantity or add controls', () => {
  const navigate = jest.fn();
  let screen!: renderer.ReactTestRenderer;
  act(() => {
    screen = renderer.create(
      wrap(
        <ShopScreen
          navigation={{ navigate } as never}
          route={{ key: 'shop', name: 'PetShop', params: { petId: 'dog-1' } }}
        />,
      ),
    );
  });
  act(() =>
    screen.root.findByProps({ accessibilityLabel: '상품 검색' }).props.onChangeText('물그릇'),
  );
  act(() => screen.root.findByProps({ accessibilityLabel: '물그릇 상세 보기' }).props.onPress());
  expect(navigate).toHaveBeenCalledWith('ShopProductDetail', {
    petId: 'dog-1',
    productId: 'waterBowl',
  });
  expect(screen.root.findAllByProps({ accessibilityLabel: '물그릇 수량 늘리기' })).toHaveLength(0);
  expect(screen.root.findAllByProps({ accessibilityLabel: '물그릇 장바구니 담기' })).toHaveLength(
    0,
  );
  act(() => screen.root.findByProps({ accessibilityLabel: '장바구니 0개' }).props.onPress());
  expect(navigate).toHaveBeenCalledWith('ShopCart', { petId: 'dog-1' });
  act(() => screen.unmount());
});

it('adds from detail, resets to zero, shows toast and updates the cart badge', () => {
  jest.useFakeTimers();
  let screen!: renderer.ReactTestRenderer;
  act(() => {
    screen = renderer.create(
      wrap(
        <ProductDetailScreen
          navigation={{ navigate: jest.fn() } as never}
          route={{
            key: 'detail',
            name: 'ShopProductDetail',
            params: { petId: 'dog-1', productId: 'waterBowl' },
          }}
        />,
      ),
    );
  });
  const plus = () => screen.root.findByProps({ accessibilityLabel: '물그릇 수량 늘리기' });
  const minus = () => screen.root.findByProps({ accessibilityLabel: '물그릇 수량 줄이기' });
  const add = () =>
    screen.root.findAll(
      node =>
        node.props.accessibilityRole === 'button' &&
        node.props.onPress &&
        node.findAllByProps({ children: '장바구니 담기' }).length > 0,
    )[0];
  expect(minus().props.disabled).toBe(false);
  expect(add().props.disabled).toBe(false);
  for (let i = 0; i < 9; i += 1) {
    act(() => plus().props.onPress());
  }
  expect(plus().props.disabled).toBe(true);
  act(() => add().props.onPress());
  expect(useShopStore.getState().cart.waterBowl).toBe(10);
  expect(screen.root.findByProps({ children: '장바구니에 추가되었습니다' })).toBeDefined();
  expect(screen.root.findByProps({ accessibilityLabel: '장바구니 10개' })).toBeDefined();
  expect(minus().props.disabled).toBe(true);
  expect(add().props.disabled).toBe(true);
  act(() => jest.advanceTimersByTime(1500));
  expect(screen.root.findAllByProps({ children: '장바구니에 추가되었습니다' })).toHaveLength(0);
  expect(minus().props.disabled).toBe(true);
  expect(add().props.disabled).toBe(true);
  expect(plus().props.disabled).toBe(false);
  act(() => plus().props.onPress());
  act(() => add().props.onPress());
  expect(useShopStore.getState().cart.waterBowl).toBe(11);
  act(() => screen.unmount());
  jest.useRealTimers();
});

it('supports multiple category buttons', () => {
  let screen!: renderer.ReactTestRenderer;
  act(() => {
    screen = renderer.create(
      wrap(
        <ShopScreen
          navigation={{ navigate: jest.fn() } as never}
          route={{ key: 'shop', name: 'PetShop', params: { petId: 'dog-1' } }}
        />,
      ),
    );
  });
  act(() => screen.root.findByProps({ accessibilityLabel: '간식 분류' }).props.onPress());
  expect(screen.root.findAllByProps({ accessibilityLabel: '물그릇 상세 보기' })).toHaveLength(0);
  act(() => screen.root.findByProps({ accessibilityLabel: '식사 분류' }).props.onPress());
  expect(
    screen.root.findByProps({ accessibilityLabel: '간식 분류' }).props.accessibilityState.checked,
  ).toBe(true);
  expect(
    screen.root.findByProps({ accessibilityLabel: '식사 분류' }).props.accessibilityState.checked,
  ).toBe(true);
  expect(
    screen.root.findByType(FlatList).props.data.some((p: { id: string }) => p.id === 'waterBowl'),
  ).toBe(true);
  expect(
    screen.root.findByType(FlatList).props.data.some((p: { id: string }) => p.id === 'treat'),
  ).toBe(true);
  act(() => screen.root.findByProps({ accessibilityLabel: '전체 분류' }).props.onPress());
  expect(
    screen.root.findByProps({ accessibilityLabel: '간식 분류' }).props.accessibilityState.checked,
  ).toBe(false);
  act(() => screen.unmount());
});

it('shows the server inventory, balance and a used-up consumable', async () => {
  mockShopSession.session = { sessionSeq: 3001 };
  jest.mocked(loadInventoryOnEntry).mockResolvedValue({
    balance: 110000,
    expectedCost: 150000,
    equipment: [
      { key: 'foodBowl', label: '밥그릇' },
      { key: 'waterBowl', label: '물그릇' },
    ],
    items: [
      {
        itemSeq: 1,
        itemName: '밥그릇',
        category: 'FOOD_BOWL',
        isConsumable: false,
        quantity: 1,
        imageUrl: null,
      },
      {
        itemSeq: 2,
        itemName: '물그릇',
        category: 'WATER_BOWL',
        isConsumable: false,
        quantity: 1,
        imageUrl: null,
      },
      {
        itemSeq: 11,
        itemName: '건식 사료 1kg',
        category: 'FOOD',
        isConsumable: true,
        quantity: 5,
        imageUrl: null,
      },
      {
        itemSeq: 12,
        itemName: '간식',
        category: 'SNACK',
        isConsumable: true,
        quantity: 0,
        imageUrl: null,
      },
    ],
  });
  let screen!: renderer.ReactTestRenderer;
  await act(async () => {
    screen = renderer.create(
      wrap(
        <InventoryScreen
          navigation={{ navigate: jest.fn(), goBack: jest.fn() } as never}
          route={{ key: 'inventory', name: 'ShopInventory', params: { petId: 'dog-1' } }}
        />,
      ),
    );
  });
  expect(loadInventoryOnEntry).toHaveBeenCalledWith(3001);
  expect(screen.root.findByProps({ accessibilityLabel: '남은 예산 110,000원' })).toBeDefined();
  expect(screen.root.findByProps({ children: '110,000원' })).toBeDefined();
  const detailButtons = (name: string) =>
    screen.root.findAll(
      node =>
        node.props.accessibilityRole === 'button' &&
        node.props.accessibilityLabel === `${name} 상세 보기`,
    );
  expect(detailButtons('건식 사료 1kg').length).toBeGreaterThan(0);
  expect(detailButtons('밥그릇')).toHaveLength(detailButtons('건식 사료 1kg').length);
  expect(detailButtons('물그릇')).toHaveLength(detailButtons('건식 사료 1kg').length);
  expect(screen.root.findByProps({ children: '보유 5개' })).toBeDefined();
  expect(screen.root.findByProps({ children: '다 썼음' })).toBeDefined();
  act(() => screen.unmount());
});

it('shows a negative budget in the danger color', () => {
  useShopStore.setState({ spent: 60000 });
  let shop!: renderer.ReactTestRenderer;
  act(() => {
    shop = renderer.create(
      wrap(
        <ShopScreen
          navigation={{ navigate: jest.fn() } as never}
          route={{ key: 'shop', name: 'PetShop', params: { petId: 'dog-1' } }}
        />,
      ),
    );
  });
  expect(
    StyleSheet.flatten(shop.root.findByProps({ children: '남은 예산 -10,000원' }).props.style)
      .color,
  ).toBe(colors.danger);
  act(() => shop.unmount());

  let inventory!: renderer.ReactTestRenderer;
  act(() => {
    inventory = renderer.create(
      wrap(
        <InventoryScreen
          navigation={{ navigate: jest.fn(), goBack: jest.fn() } as never}
          route={{ key: 'inventory', name: 'ShopInventory', params: { petId: 'dog-1' } }}
        />,
      ),
    );
  });
  expect(
    StyleSheet.flatten(inventory.root.findByProps({ children: '-10,000원' }).props.style).color,
  ).toBe(colors.danger);
  act(() => inventory.root.findByProps({ accessibilityLabel: '지출 내역' }).props.onPress());
  const balances = inventory.root
    .findAllByProps({ children: '-10,000원' })
    .filter(node => StyleSheet.flatten(node.props.style)?.color);
  expect(balances.length).toBeGreaterThanOrEqual(2);
  balances.forEach(node => {
    expect(StyleSheet.flatten(node.props.style).color).toBe(colors.danger);
  });
  act(() => inventory.unmount());
});

it('shows paid items, remaining money and individual usage counters', () => {
  useShopStore.getState().setQuantity('food', 1, 'dog');
  useShopStore.getState().checkout('dog');
  useShopStore.getState().consume({ food: 1 });
  let screen!: renderer.ReactTestRenderer;
  act(() => {
    screen = renderer.create(
      wrap(
        <InventoryScreen
          navigation={{ navigate: jest.fn() } as never}
          route={{ key: 'inventory', name: 'ShopInventory', params: { petId: 'dog-1' } }}
        />,
      ),
    );
  });
  expect(screen.root.findByProps({ children: '39,400원' })).toBeDefined();
  expect(
    screen.root.findByProps({ accessibilityLabel: '균형 잡힌 사료 1번째 남은 수량 11끼' }),
  ).toBeDefined();
  act(() => {
    useShopStore.getState().consume({ food: 11 });
  });
  expect(
    screen.root.findByProps({ accessibilityLabel: '균형 잡힌 사료 1번째 남은 수량 0끼' }),
  ).toBeDefined();
  act(() => screen.unmount());
});

it('shows expenses loaded for the adoption session', async () => {
  mockShopSession.session = { sessionSeq: 3001 };
  jest.mocked(loadTransactionsOnEntry).mockResolvedValue({
    items: [
      {
        transactionSeq: 70001,
        transactionType: 'PURCHASE',
        item: { itemSeq: 22, itemName: '균형 잡힌 사료', category: 'FOOD' },
        occurrenceSeq: null,
        quantity: 2,
        amount: 21200,
        balanceAfter: 128800,
        description: '균형 잡힌 사료 × 2',
        regDttm: '2026-11-01T15:10:00+09:00',
      },
    ],
    page: 0,
    hasNext: false,
    totalElements: 1,
  });
  let screen!: renderer.ReactTestRenderer;
  await act(async () => {
    screen = renderer.create(
      wrap(
        <InventoryScreen
          navigation={{ navigate: jest.fn(), goBack: jest.fn() } as never}
          route={{ key: 'inventory', name: 'ShopInventory', params: { petId: 'dog-1' } }}
        />,
      ),
    );
  });
  expect(loadTransactionsOnEntry).toHaveBeenCalledWith(3001);
  act(() => screen.root.findByProps({ accessibilityLabel: '지출 내역' }).props.onPress());
  expect(screen.root.findByProps({ children: '균형 잡힌 사료 × 2' })).toBeDefined();
  expect(screen.root.findByProps({ children: '사료 · 2개 구매' })).toBeDefined();
  expect(screen.root.findByProps({ children: '-21,200원' })).toBeDefined();
  expect(screen.root.findByProps({ children: '거래 후 잔액 128,800원' })).toBeDefined();
  act(() => screen.unmount());
});

it('dismisses the keyboard on list drag or touch and keeps sorting in a bottom sheet', () => {
  const dismiss = jest.spyOn(Keyboard, 'dismiss').mockImplementation(() => {});
  let screen!: renderer.ReactTestRenderer;
  act(() => {
    screen = renderer.create(
      wrap(
        <ShopScreen
          navigation={{ navigate: jest.fn() } as never}
          route={{ key: 'shop', name: 'PetShop', params: { petId: 'dog-1' } }}
        />,
      ),
    );
  });
  act(() => screen.root.findByType(FlatList).props.onScrollBeginDrag());
  act(() => screen.root.findByType(FlatList).props.onTouchStart());
  expect(dismiss).toHaveBeenCalledTimes(2);
  act(() => screen.root.findByProps({ accessibilityLabel: '상품 정렬 필터' }).props.onPress());
  expect(screen.root.findByProps({ accessibilityLabel: '높은 가격순' })).toBeDefined();
  act(() => screen.root.findByProps({ accessibilityLabel: '높은 가격순' }).props.onPress());
  const prices = screen.root.findByType(FlatList).props.data.map((p: { price: number }) => p.price);
  expect(prices).toEqual([...prices].sort((a, b) => b - a));
  act(() => screen.unmount());
  dismiss.mockRestore();
});

it('adds the chosen quantity from detail', () => {
  const navigate = jest.fn();
  let screen!: renderer.ReactTestRenderer;
  act(() => {
    screen = renderer.create(
      wrap(
        <ProductDetailScreen
          navigation={{ navigate } as never}
          route={{
            key: 'detail',
            name: 'ShopProductDetail',
            params: { petId: 'dog-1', productId: 'food' },
          }}
        />,
      ),
    );
  });
  expect(
    screen.root.findByProps({ accessibilityLabel: '균형 잡힌 사료 수량 늘리기' }).props
      .accessibilityState.disabled,
  ).toBe(true);
  const basis = findProduct('food')!.market.basis;
  expect(screen.root.findAllByProps({ children: basis })).toHaveLength(0);
  act(() => screen.root.findByProps({ accessibilityLabel: '가격 근거' }).props.onPress());
  expect(screen.root.findByProps({ children: basis })).toBeDefined();
  act(() => screen.root.findByProps({ accessibilityLabel: '가격 근거' }).props.onPress());
  expect(screen.root.findAllByProps({ children: basis })).toHaveLength(0);
  const addButton = screen.root.findAll(
    node =>
      node.props.accessibilityRole === 'button' &&
      node.props.onPress &&
      node.findAllByProps({ children: '장바구니 담기' }).length > 0,
  )[0];
  act(() => addButton.props.onPress());
  expect(useShopStore.getState().cart.food).toBe(1);
  expect(navigate).toHaveBeenCalledWith('PetShop', { petId: 'dog-1' });
  act(() => screen.unmount());
});

it('blocks another food purchase until the owned pack is used up', () => {
  useShopStore.getState().setQuantity('food', 1, 'dog');
  expect(useShopStore.getState().checkout('dog')).toBeNull();
  let screen!: renderer.ReactTestRenderer;
  act(() => {
    screen = renderer.create(
      wrap(
        <ProductDetailScreen
          navigation={{ navigate: jest.fn() } as never}
          route={{
            key: 'detail',
            name: 'ShopProductDetail',
            params: { petId: 'dog-1', productId: 'food' },
          }}
        />,
      ),
    );
  });
  expect(
    screen.root.findByProps({ children: '가진 사료를 다 쓴 뒤에 다시 살 수 있어요.' }),
  ).toBeDefined();
  const addButton = screen.root.findAll(
    node =>
      node.props.accessibilityRole === 'button' &&
      node.props.onPress &&
      node.findAllByProps({ children: '장바구니 담기' }).length > 0,
  )[0];
  expect(addButton.props.disabled).toBe(true);
  act(() => addButton.props.onPress());
  expect(useShopStore.getState().cart.food).toBeUndefined();
  act(() => screen.unmount());
});

it('changes cart quantity and completes a confirmed mock purchase', () => {
  const reset = jest.fn();
  useShopStore.getState().setQuantity('food', 1, 'dog');
  let screen!: renderer.ReactTestRenderer;
  act(() => {
    screen = renderer.create(
      wrap(
        <CartScreen
          navigation={
            {
              reset,
              getState: () => ({
                routes: [
                  { name: 'PetShop', key: 'shop', params: { petId: 'dog-1' } },
                  { name: 'ShopProductDetail', key: 'detail' },
                  { name: 'ShopCart', key: 'cart' },
                ],
              }),
            } as never
          }
          route={{ key: 'cart', name: 'ShopCart', params: { petId: 'dog-1' } }}
        />,
      ),
    );
  });
  act(() =>
    screen.root.findByProps({ accessibilityLabel: '균형 잡힌 사료 수량 늘리기' }).props.onPress(),
  );
  expect(useShopStore.getState().cart.food).toBe(1);
  const buyButton = screen.root.findAll(
    node =>
      node.props.accessibilityRole === 'button' &&
      node.props.onPress &&
      node.findAllByProps({ children: '1종 모의 구매하기' }).length > 0,
  )[0];
  act(() => buyButton.props.onPress());
  act(() => screen.root.findByProps({ accessibilityLabel: '구매하기' }).props.onPress());
  expect(useShopStore.getState().spent).toBe(10600);
  expect(useShopStore.getState().inventory.food).toBe(12);
  expect(useShopStore.getState().cart).toEqual({});
  expect(reset).toHaveBeenCalledWith({
    index: 1,
    routes: [
      { name: 'PetShop', key: 'shop', params: { petId: 'dog-1' } },
      { key: 'cart-inventory', name: 'ShopInventory', params: { petId: 'dog-1' } },
    ],
  });
  expect(reset).toHaveBeenCalledTimes(1);
  expect(screen.root.findAllByProps({ children: '구매 완료' })).toHaveLength(0);
  act(() => screen.unmount());
});

it('does not purchase on the device when a server session has no item number', () => {
  mockShopSession.session = { sessionSeq: 3001 };
  useShopStore.getState().setQuantity('food', 1, 'dog');
  let screen!: renderer.ReactTestRenderer;
  act(() => {
    screen = renderer.create(
      wrap(
        <CartScreen
          navigation={{ popTo: jest.fn() } as never}
          route={{ key: 'cart', name: 'ShopCart', params: { petId: 'dog-1' } }}
        />,
      ),
    );
  });
  act(() =>
    screen.root
      .findAll(
        node =>
          node.props.accessibilityRole === 'button' &&
          node.props.onPress &&
          node.findAllByProps({ children: '1종 모의 구매하기' }).length > 0,
      )[0]
      .props.onPress(),
  );
  act(() => screen.root.findByProps({ accessibilityLabel: '구매하기' }).props.onPress());
  expect(purchaseSimulationItems).not.toHaveBeenCalled();
  expect(screen.root.findByProps({ children: '구매할 수 없는 상품이 있어요.' })).toBeDefined();
  expect(useShopStore.getState().spent).toBe(0);
  expect(useShopStore.getState().cart.food).toBe(1);
  act(() => screen.unmount());
});

const serverFood = {
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

it('purchases a server item from the cart and keeps the cart when purchase fails', async () => {
  const reset = jest.fn();
  const session = {
    petId: 'dog-1',
    startedAt: 1,
    expiresAt: Date.now() + 86400000,
    weeklyBudget: 150000,
  };
  replaceSessionShopCatalog(`${session.petId}:${session.startedAt}`, 'DOG', [
    { displayOrder: 0, product: serverFood },
  ]);
  useShopStore.getState().open(session);
  useShopStore.getState().setQuantity('11', 1, 'dog');
  mockShopSession.session = { sessionSeq: 3001 };
  jest.mocked(purchaseSimulationItems).mockResolvedValue({
    purchases: [
      {
        transactionSeq: 70001,
        item: { itemSeq: 11, itemName: '건식 사료 1kg', category: 'FOOD' },
        quantity: 1,
        amount: 8000,
        balanceAfter: 142000,
        inventoryQuantity: 1,
      },
    ],
    totalAmount: 8000,
    balanceAfter: 142000,
  });
  let screen!: renderer.ReactTestRenderer;
  await act(async () => {
    screen = renderer.create(
      wrap(
        <CartScreen
          navigation={
            {
              reset,
              getState: () => ({
                routes: [
                  { name: 'PetShop', key: 'shop', params: { petId: 'dog-1' } },
                  { name: 'ShopProductDetail', key: 'detail' },
                  { name: 'ShopCart', key: 'cart' },
                ],
              }),
            } as never
          }
          route={{ key: 'cart', name: 'ShopCart', params: { petId: 'dog-1' } }}
        />,
      ),
    );
  });
  const buy = () =>
    screen.root.findAll(
      node =>
        node.props.accessibilityRole === 'button' &&
        node.props.onPress &&
        node.findAllByProps({ children: '1종 모의 구매하기' }).length > 0,
    )[0];
  await act(async () => {
    buy().props.onPress();
  });
  await act(async () => {
    await screen.root.findByProps({ accessibilityLabel: '구매하기' }).props.onPress();
  });
  expect(purchaseSimulationItems).toHaveBeenCalledWith(3001, [{ itemSeq: 11, quantity: 1 }]);
  expect(isActiveSimulationStale()).toBe(true);
  expect(useShopStore.getState().inventory['11']).toBe(1);
  expect(useShopStore.getState().spent).toBe(8000);
  expect(useShopStore.getState().cart).toEqual({});
  expect(reset).toHaveBeenCalledWith({
    index: 1,
    routes: [
      { name: 'PetShop', key: 'shop', params: { petId: 'dog-1' } },
      { key: 'cart-inventory', name: 'ShopInventory', params: { petId: 'dog-1' } },
    ],
  });

  await act(async () => {
    useShopStore.setState({ inventory: { '11': 0 } });
    useShopStore.getState().setQuantity('11', 1, 'dog');
  });
  jest
    .mocked(purchaseSimulationItems)
    .mockRejectedValueOnce(
      new ApiClientError({ code: 'SIMULATION_EQUIPMENT_ALREADY_OWNED', message: 'owned' }),
    );
  await act(async () => {
    buy().props.onPress();
  });
  await act(async () => {
    await screen.root.findByProps({ accessibilityLabel: '구매하기' }).props.onPress();
  });
  expect(screen.root.findByProps({ children: '이미 가지고 있는 기구예요.' })).toBeDefined();
  expect(useShopStore.getState().cart['11']).toBe(1);
  expect(reset).toHaveBeenCalledTimes(1);
  act(() => screen.unmount());
});

it('requires a single piece of equipment before purchasing', async () => {
  const popTo = jest.fn();
  const bowl = {
    ...serverFood,
    id: '1',
    name: '스테인리스 밥그릇',
    kind: 'durable' as const,
    price: 12000,
    itemSeq: 1,
    serverCategory: 'FOOD_BOWL',
    placement: 'room',
  };
  const session = {
    petId: 'dog-1',
    startedAt: 1,
    expiresAt: Date.now() + 86400000,
    weeklyBudget: 150000,
  };
  replaceSessionShopCatalog(`${session.petId}:${session.startedAt}`, 'DOG', [
    { displayOrder: 0, product: bowl },
  ]);
  useShopStore.getState().open(session);
  useShopStore.getState().setQuantity('1', 2, 'dog');
  mockShopSession.session = { sessionSeq: 3001 };
  let screen!: renderer.ReactTestRenderer;
  await act(async () => {
    screen = renderer.create(
      wrap(
        <CartScreen
          navigation={{ popTo } as never}
          route={{ key: 'cart', name: 'ShopCart', params: { petId: 'dog-1' } }}
        />,
      ),
    );
  });
  await act(async () => {
    screen.root
      .findAll(
        node =>
          node.props.accessibilityRole === 'button' &&
          node.props.onPress &&
          node.findAllByProps({ children: '1종 모의 구매하기' }).length > 0,
      )[0]
      .props.onPress();
  });
  await act(async () => {
    await screen.root.findByProps({ accessibilityLabel: '구매하기' }).props.onPress();
  });
  expect(purchaseSimulationItems).not.toHaveBeenCalled();
  expect(screen.root.findByProps({ children: '기구는 1개만 구매할 수 있어요.' })).toBeDefined();
  expect(popTo).not.toHaveBeenCalled();
  act(() => screen.unmount());
});

it('cancels purchase without changes and shows a custom error dialog on expiry', () => {
  useShopStore.getState().setQuantity('food', 1, 'dog');
  let screen!: renderer.ReactTestRenderer;
  act(() => {
    screen = renderer.create(
      wrap(
        <CartScreen
          navigation={{ navigate: jest.fn() } as never}
          route={{ key: 'cart', name: 'ShopCart', params: { petId: 'dog-1' } }}
        />,
      ),
    );
  });
  const purchase = () =>
    screen.root
      .findAll(
        node =>
          node.props.accessibilityRole === 'button' &&
          node.props.onPress &&
          node.findAllByProps({ children: '1종 모의 구매하기' }).length > 0,
      )[0]
      .props.onPress();
  act(purchase);
  act(() => screen.root.findByProps({ accessibilityLabel: '취소' }).props.onPress());
  expect(useShopStore.getState().spent).toBe(0);
  expect(useShopStore.getState().cart.food).toBe(1);
  act(purchase);
  act(() => useShopStore.setState({ expiresAt: Date.now() - 1 }));
  act(() => screen.root.findByProps({ accessibilityLabel: '구매하기' }).props.onPress());
  expect(screen.root.findByProps({ children: '구매 확인' })).toBeDefined();
  expect(screen.root.findByProps({ children: '모의 입양 기간이 종료되었어요.' })).toBeDefined();
  expect(useShopStore.getState().spent).toBe(0);
  expect(useShopStore.getState().cart.food).toBe(1);
  act(() => screen.root.findByProps({ accessibilityLabel: '확인' }).props.onPress());
  act(() => screen.unmount());
});

it('reuses existing destinations when leaving cart instead of growing the stack', () => {
  const router = StackRouter({ initialRouteName: 'PetShop' });
  const options = {
    routeNames: ['PetShop', 'ShopProductDetail', 'ShopCart', 'ShopInventory'],
    routeParamList: {},
    routeGetIdList: {},
  };
  let state = router.getInitialState(options);
  const dispatch = (action: ReturnType<typeof StackActions.push>) => {
    const next = router.getStateForAction(state, action, options);
    if (!next || next.stale !== false) {
      throw new Error('Expected a resolved stack');
    }
    state = next;
  };
  dispatch(StackActions.push('ShopProductDetail', { petId: 'dog-1', productId: 'food' }));
  dispatch(StackActions.push('ShopCart', { petId: 'dog-1' }));
  const popTo = (name: string, params: object) => {
    const next = router.getStateForAction(state, StackActions.popTo(name, params), options);
    if (!next || next.stale !== false) {
      throw new Error('Expected a resolved stack');
    }
    state = next;
  };
  useShopStore.getState().setQuantity('food', 1, 'dog');
  let screen!: renderer.ReactTestRenderer;
  act(() => {
    screen = renderer.create(
      wrap(
        <CartScreen
          navigation={{ popTo } as never}
          route={{ key: 'cart', name: 'ShopCart', params: { petId: 'dog-1' } }}
        />,
      ),
    );
  });
  for (let i = 0; i < 3; i += 1) {
    act(() =>
      screen.root.findByProps({ accessibilityLabel: '균형 잡힌 사료 상세 보기' }).props.onPress(),
    );
    expect(state.routes.map(r => r.name)).toEqual(['PetShop', 'ShopProductDetail']);
    dispatch(StackActions.push('ShopCart', { petId: 'dog-1' }));
  }
  act(() => useShopStore.getState().remove(['food']));
  const browse = screen.root.findAll(
    node =>
      node.props.accessibilityRole === 'button' &&
      node.props.onPress &&
      node.findAllByProps({ children: '상품 둘러보기' }).length > 0,
  )[0];
  act(() => browse.props.onPress());
  expect(state.routes.map(r => r.name)).toEqual(['PetShop']);
  act(() => screen.unmount());
});

it('reloads inventory when an existing inventory screen regains focus', async () => {
  mockShopSession.session = { sessionSeq: 3001 };
  const inventory = (quantity: number) => ({
    balance: 49500,
    expectedCost: 0,
    equipment: [],
    items: [
      {
        itemSeq: 31,
        itemName: '배변봉투',
        category: 'POOP_BAG' as const,
        isConsumable: true,
        quantity,
        imageUrl: null,
      },
    ],
  });
  jest
    .mocked(loadInventoryOnEntry)
    .mockResolvedValueOnce(inventory(10))
    .mockResolvedValueOnce(inventory(9));
  let screen!: renderer.ReactTestRenderer;
  await act(async () => {
    screen = renderer.create(
      wrap(
        <InventoryScreen
          navigation={{ goBack: jest.fn() } as never}
          route={{ key: 'inventory', name: 'ShopInventory', params: { petId: 'dog-1' } }}
        />,
      ),
    );
  });
  expect(screen.root.findByProps({ children: '보유 10개' })).toBeDefined();
  await act(async () => {
    mockShopFocusEffects.forEach(effect => effect());
  });
  expect(loadInventoryOnEntry).toHaveBeenCalledTimes(2);
  expect(screen.root.findByProps({ children: '보유 9개' })).toBeDefined();
  expect(screen.root.findAllByProps({ children: '보유 10개' })).toHaveLength(0);
  act(() => screen.unmount());
});

it.each([true, false])(
  'returns directly to the shop after purchase (shop in stack: %s)',
  hasShop => {
    const router = StackRouter({ initialRouteName: 'Home' });
    const options = {
      routeNames: [
        'Home',
        'MockAdoptionActivity',
        'PetShop',
        'ShopProductDetail',
        'ShopCart',
        'ShopInventory',
      ],
      routeParamList: {},
      routeGetIdList: {},
    };
    let state = router.getInitialState(options);
    const dispatch = (action: Parameters<typeof router.getStateForAction>[1]) => {
      const next = router.getStateForAction(state, action, options);
      if (!next) {
        throw new Error('Expected navigation to handle action');
      }
      state = router.getRehydratedState(next, options);
    };
    dispatch(StackActions.push('MockAdoptionActivity', { petId: 'dog-1' }));
    if (hasShop) {
      dispatch(StackActions.push('PetShop', { petId: 'dog-1' }));
    }
    dispatch(StackActions.push('ShopProductDetail', { petId: 'dog-1', productId: 'food' }));
    dispatch(StackActions.push('ShopCart', { petId: 'dog-1' }));
    useShopStore.getState().setQuantity('food', 1, 'dog');
    let screen!: renderer.ReactTestRenderer;
    act(() => {
      screen = renderer.create(
        wrap(
          <CartScreen
            navigation={
              {
                getState: () => state,
                reset: (next: Parameters<typeof CommonActions.reset>[0]) =>
                  dispatch(CommonActions.reset(next)),
              } as never
            }
            route={{ key: 'cart', name: 'ShopCart', params: { petId: 'dog-1' } }}
          />,
        ),
      );
    });
    const buy = screen.root.findAll(
      node =>
        node.props.accessibilityRole === 'button' &&
        node.props.onPress &&
        node.findAllByProps({ children: '1종 모의 구매하기' }).length > 0,
    )[0];
    act(() => buy.props.onPress());
    act(() => screen.root.findByProps({ accessibilityLabel: '구매하기' }).props.onPress());
    expect(state.routes.map(route => route.name)).toEqual([
      'Home',
      'MockAdoptionActivity',
      'PetShop',
      'ShopInventory',
    ]);
    dispatch(StackActions.pop());
    expect(state.routes.map(route => route.name)).toEqual([
      'Home',
      'MockAdoptionActivity',
      'PetShop',
    ]);
    act(() => screen.unmount());
  },
);
