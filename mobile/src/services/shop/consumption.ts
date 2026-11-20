import { useShopStore } from '../../stores/shopStore';
import { availableProducts, type Product, type Species } from './catalog';

export type CareSupplyAction = 'food' | 'treat' | 'clean' | 'bath' | 'play';

export function findCareSupply(species: Species, action: CareSupplyAction): Product | undefined {
  const shop = useShopStore.getState();
  return availableProducts(species).find(product => {
    if ((shop.inventory[product.id] ?? 0) <= 0) {
      return false;
    }
    if (action === 'food') {
      return product.serverCategory === 'FOOD' || product.id === 'food';
    }
    if (action === 'treat') {
      return product.serverCategory === 'SNACK' || product.category === '간식';
    }
    if (action === 'bath') {
      return product.serverCategory === 'SHAMPOO';
    }
    if (action === 'play') {
      return (
        product.serverCategory === 'TOY' ||
        (product.category === '놀이' && product.kind === 'durable')
      );
    }
    return (
      product.serverCategory === 'LITTER' || product.id === (species === 'dog' ? 'pads' : 'litter')
    );
  });
}

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
  const supply = findCareSupply(species, action);
  if (supply?.serverCategory) {
    return shop.consume({ [supply.id]: 1 });
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
