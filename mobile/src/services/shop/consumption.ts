import { useShopStore } from '../../stores/shopStore';
import { availableProducts, type Species } from './catalog';

export function consumeCareSupplies(
  petId: string,
  species: Species,
  action: 'food' | 'treat' | 'clean',
): boolean {
  const shop = useShopStore.getState();
  if (!shop.ready || !shop.sessionKey.startsWith(`${petId}:`)) {
    useShopStore.setState({ error: '물품 정보를 불러온 후 다시 시도해주세요.' });
    return false;
  }
  if (action === 'food') {
    return shop.consume({ food: 1 });
  }
  if (action === 'clean') {
    return shop.consume(species === 'dog' ? { cleaner: 1, pads: 1 } : { cleaner: 1, litter: 1 });
  }
  const snack = availableProducts(species).find(
    p => p.category === '간식' && (shop.inventory[p.id] ?? 0) > 0,
  );
  if (!snack) {
    useShopStore.setState({ error: '간식이 부족해요. 상점에서 구매해주세요.' });
    return false;
  }
  return shop.consume({ [snack.id]: 1 });
}
