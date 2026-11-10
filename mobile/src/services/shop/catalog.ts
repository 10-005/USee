export type Species = 'dog' | 'cat';
export type Product = {
  id: string;
  name: string;
  qty: number;
  unit: string;
  species: Species | 'all';
  kind: 'durable' | 'consumable';
  category: string;
  description: string;
  essential?: boolean;
  sprite: number;
  atlas?: string;
  price: number;
  placement: string;
  imageUrl?: string | null;
  itemSeq?: number;
  serverCategory?: string;
  effects?: Record<string, number> | null;
  market: {
    gamePrice: number;
    basis: string;
    sources: {
      shop: string;
      product: string;
      pack: string;
      price: number;
      url: string;
      note: string;
    }[];
  };
};

export let products: Product[] = [];
export function setShopCatalog(catalog: Product[]) {
  products = catalog;
}
export const categories = ['전체', '필수 준비', '식사', '간식', '놀이', '가구', '위생', '생활'];
export const money = (amount: number) => `${amount.toLocaleString('ko-KR')}원`;
export const findProduct = (id: string) => products.find(product => product.id === id);
export const availableProducts = (species: Species) =>
  products.filter(p => p.species === 'all' || p.species === species);
export function filterProducts(species: Species, category: string, query: string, sort: string) {
  const filtered = availableProducts(species).filter(
    p =>
      (category === '전체' || (category === '필수 준비' ? p.essential : p.category === category)) &&
      `${p.name} ${p.description}`.includes(query.trim()),
  );
  return sort === '기본순'
    ? filtered
    : filtered.sort((a, b) => (sort === '낮은 가격순' ? a.price - b.price : b.price - a.price));
}
