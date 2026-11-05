import renderer, { act } from 'react-test-renderer';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { Alert } from 'react-native';
import { ShopScreen } from '../ShopScreen';
import { ProductDetailScreen } from '../ProductDetailScreen';
import { CartScreen } from '../CartScreen';
import { useShopStore } from '../../../stores/shopStore';
import { getShopDatabase } from '../../../services/shop/shopDatabase';

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

it('searches the list, opens product details and adds an item', () => {
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
  act(() => screen.root.findByProps({ accessibilityLabel: '물그릇 수량 늘리기' }).props.onPress());
  act(() =>
    screen.root.findByProps({ accessibilityLabel: '물그릇 장바구니 담기' }).props.onPress(),
  );
  expect(useShopStore.getState().cart.waterBowl).toBe(1);
  act(() => screen.unmount());
});

it('starts at zero, caps each selection at 10 and resets after the add toast', () => {
  jest.useFakeTimers();
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
  act(() =>
    screen.root.findByProps({ accessibilityLabel: '상품 검색' }).props.onChangeText('물그릇'),
  );
  const plus = () => screen.root.findByProps({ accessibilityLabel: '물그릇 수량 늘리기' });
  const minus = () => screen.root.findByProps({ accessibilityLabel: '물그릇 수량 줄이기' });
  const add = () => screen.root.findByProps({ accessibilityLabel: '물그릇 장바구니 담기' });
  expect(minus().props.disabled).toBe(true);
  expect(add().props.disabled).toBe(true);
  for (let i = 0; i < 10; i += 1) {
    act(() => plus().props.onPress());
  }
  expect(plus().props.disabled).toBe(true);
  act(() => add().props.onPress());
  expect(useShopStore.getState().cart.waterBowl).toBe(10);
  expect(screen.root.findByProps({ children: '담기 완료' })).toBeDefined();
  expect(minus().props.disabled).toBe(true);
  expect(add().props.disabled).toBe(true);
  act(() => jest.advanceTimersByTime(1500));
  expect(screen.root.findAllByProps({ children: '담기 완료' })).toHaveLength(0);
  expect(minus().props.disabled).toBe(true);
  expect(add().props.disabled).toBe(true);
  expect(plus().props.disabled).toBe(false);
  act(() => plus().props.onPress());
  act(() => add().props.onPress());
  expect(useShopStore.getState().cart.waterBowl).toBe(11);
  act(() => screen.unmount());
  jest.useRealTimers();
});

it('uses shared filters and persists added budget from the list', () => {
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
  act(() => screen.root.findByProps({ accessibilityLabel: '상품 분류 필터' }).props.onPress());
  act(() => screen.root.findByProps({ accessibilityLabel: '간식' }).props.onPress());
  expect(screen.root.findAllByProps({ accessibilityLabel: '물그릇 상세 보기' })).toHaveLength(0);
  act(() => screen.root.findByProps({ accessibilityLabel: '예산 추가' }).props.onPress());
  act(() =>
    screen.root.findByProps({ accessibilityLabel: '추가할 예산' }).props.onChangeText('10000'),
  );
  const confirm = screen.root.findAll(
    node =>
      node.props.accessibilityRole === 'button' &&
      node.props.onPress &&
      node.findAllByProps({ children: '추가하기' }).length > 0,
  )[0];
  act(() => confirm.props.onPress());
  expect(useShopStore.getState().budget).toBe(60000);
  act(() => screen.unmount());
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
  const buttons = jest.mocked(Alert.alert).mock.calls[0][2]!;
  act(() => buttons.find(button => button.text === '구매하기')!.onPress!());
  expect(useShopStore.getState().spent).toBe(21200);
  expect(useShopStore.getState().inventory.food).toBe(24);
  expect(useShopStore.getState().cart).toEqual({});
  act(() => screen.unmount());
});
