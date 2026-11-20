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
export const categories = ['전체', '식사', '간식', '놀이', '가구', '위생', '생활'];
export const money = (amount: number) => `${amount.toLocaleString('ko-KR')}원`;
export const findProduct = (id: string) => products.find(product => product.id === id);
export const isFoodProduct = (product: Pick<Product, 'id' | 'serverCategory'>) =>
  product.serverCategory === 'FOOD' || product.id === 'food';
export const availableProducts = (species: Species) =>
  products.filter(p => p.species === 'all' || p.species === species);

const demoProductOrder = [
  '밥그릇',
  '균형 잡힌 사료',
  '위생 관리 키트',
  '하네스와 리드줄',
  '배변봉투',
];
const demoProductRank = new Map(demoProductOrder.map((name, index) => [name, index]));
const defaultProductRank = (product: Product) =>
  demoProductRank.get(product.name.trim()) ?? demoProductOrder.length;

export function filterProducts(species: Species, category: string, query: string, sort: string) {
  const filtered = availableProducts(species).filter(
    p =>
      (category === '전체' || p.category === category) &&
      `${p.name} ${p.description}`.includes(query.trim()),
  );
  return sort === '기본순'
    ? filtered.sort((a, b) => defaultProductRank(a) - defaultProductRank(b))
    : filtered.sort((a, b) => (sort === '낮은 가격순' ? a.price - b.price : b.price - a.price));
}
