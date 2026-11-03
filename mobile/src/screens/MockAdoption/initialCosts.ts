export type CostGroup = 'small' | 'medium' | 'large' | 'cat';

export const costGroupLabels: Record<CostGroup, string> = {
  small: '소형견',
  medium: '중형견',
  large: '대형견',
  cat: '고양이',
};

// 서비스 기획 기준의 초기 준비 비용 추정치. 단위: 원.
export const initialCostItems: Array<{ label: string; amounts: Record<CostGroup, number> }> = [
  { label: '사료·간식', amounts: { small: 50000, medium: 60000, large: 80000, cat: 80000 } },
  { label: '침대·하우스', amounts: { small: 80000, medium: 100000, large: 120000, cat: 120000 } },
  { label: '화장실·패드', amounts: { small: 50000, medium: 50000, large: 50000, cat: 50000 } },
  { label: '목줄·하네스', amounts: { small: 30000, medium: 30000, large: 30000, cat: 0 } },
  { label: '장난감·용품', amounts: { small: 30000, medium: 30000, large: 30000, cat: 30000 } },
  {
    label: '초기 건강검진',
    amounts: { small: 100000, medium: 100000, large: 100000, cat: 100000 },
  },
  { label: '예방접종', amounts: { small: 150000, medium: 150000, large: 150000, cat: 150000 } },
  { label: '중성화 수술', amounts: { small: 300000, medium: 300000, large: 300000, cat: 200000 } },
];

export function getInitialCostTotal(group: CostGroup) {
  return initialCostItems.reduce((total, item) => total + item.amounts[group], 0);
}
