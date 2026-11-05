import renderer, { act } from 'react-test-renderer';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { FlatList, Keyboard } from 'react-native';
import { ShopScreen } from '../ShopScreen';
import { ProductDetailScreen } from '../ProductDetailScreen';
import { CartScreen } from '../CartScreen';
import { useShopStore } from '../../../stores/shopStore';
import { getShopDatabase } from '../../../services/shop/shopDatabase';
import { findProduct } from '../../../services/shop/catalog';

jest.mock('../useShopSession', () => ({
  useShopSession: () => ({
    pet: { id: 'dog-1', name: '비숑 프리제', category: 'dog' },
    status: 'ready',
    retry: jest.fn(),
  }),
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
  getShopDatabase().executeSync('DELETE FROM shop_sessions');
  jest.clearAllMocks();
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
  expect(screen.root.findByProps({ accessibilityLabel: '장바구니' })).toBeDefined();
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
  expect(minus().props.disabled).toBe(true);
  expect(add().props.disabled).toBe(true);
  for (let i = 0; i < 10; i += 1) {
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
  act(() =>
    screen.root.findByProps({ accessibilityLabel: '균형 잡힌 사료 수량 늘리기' }).props.onPress(),
  );
  act(() =>
    screen.root.findByProps({ accessibilityLabel: '균형 잡힌 사료 수량 늘리기' }).props.onPress(),
  );
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
  expect(useShopStore.getState().cart.food).toBe(2);
  act(() => screen.unmount());
});

it('changes cart quantity and completes a confirmed mock purchase', () => {
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
  act(() =>
    screen.root.findByProps({ accessibilityLabel: '균형 잡힌 사료 수량 늘리기' }).props.onPress(),
  );
  const buyButton = screen.root.findAll(
    node =>
      node.props.accessibilityRole === 'button' &&
      node.props.onPress &&
      node.findAllByProps({ children: '1종 모의 구매하기' }).length > 0,
  )[0];
  act(() => buyButton.props.onPress());
  act(() => screen.root.findByProps({ accessibilityLabel: '구매하기' }).props.onPress());
  expect(useShopStore.getState().spent).toBe(21200);
  expect(useShopStore.getState().inventory.food).toBe(24);
  expect(useShopStore.getState().cart).toEqual({});
  expect(screen.root.findByProps({ children: '구매 완료' })).toBeDefined();
  act(() => screen.root.findByProps({ accessibilityLabel: '확인' }).props.onPress());
  expect(screen.root.findAllByProps({ children: '구매 완료' })).toHaveLength(0);
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
