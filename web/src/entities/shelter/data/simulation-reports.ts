import { animalLabel } from '@/entities/shelter/animal-label';
import { queryOptions } from '@tanstack/react-query';
import { shelterRequest } from '@/shared/api/session';

type ListItem = {
  sessionSeq: number;
  animalName: string;
  breed: { breedName: string; speciesType: 'DOG' | 'CAT' | 'ETC' };
  startedDttm: string;
  endedDttm: string;
  totalSpent: number;
};
const stats = {
  satiety: ['포만감', 'satiety'],
  hydration: ['수분 섭취', 'hydration'],
  cleanliness: ['청결도', 'cleanliness'],
  bond: ['유대감', 'bond'],
  calmness: ['평온함', 'calm'],
  health: ['건강', 'health'],
  energy: ['에너지', 'energy'],
} as const;
const categoryNames: Record<string, string> = {
  FOOD_BOWL: '밥그릇',
  WATER_BOWL: '물그릇',
  LITTER_BOX: '배변함',
  TOY: '장난감',
  FURNITURE: '가구',
  LEASH: '목줄',
  GROOMING_KIT: '미용 도구',
  FOOD: '사료',
  SNACK: '간식',
  LITTER: '모래',
  SHAMPOO: '샴푸',
  MEDICINE: '약',
  POOP_BAG: '배변 봉투',
  CATNIP: '캣닢',
  EVENT: '진료비',
};
const personalities = {
  ACTIVE: '활동적',
  TIMID: '소심함',
  AGGRESSIVE: '공격적',
  FRIENDLY: '사람 친화적',
};
const outcomes = { COMPLETED: '완료', FAILED: '돌봄 부족으로 종료', ABANDONED: '중도 포기' };
type Detail = ListItem & {
  personality: keyof typeof personalities;
  status: keyof typeof outcomes;
  expectedCost: number;
  spentByCategory: Record<string, number>;
  dailySpent: Record<string, number>;
  finalStats: Record<keyof typeof stats, number>;
  actionCount: number;
  walkCount: number;
  eventCount: number;
  eventResolvedCount: number;
  summary: string | null;
};
function entry(item: ListItem) {
  return {
    id: String(item.sessionSeq),
    animal: `${animalLabel({ DOG: '개', CAT: '고양이', ETC: '기타' }[item.breed.speciesType], item.breed.breedName)} · ${item.animalName}`,
    start: item.startedDttm.slice(0, 16).replace('T', ' '),
    end: item.endedDttm.slice(0, 16).replace('T', ' '),
    total: item.totalSpent,
  };
}
export const reportListOptions = (id: string) =>
  queryOptions({
    queryKey: ['adoptions', id, 'simulation-reports'],
    queryFn: async ({ signal }) => {
      const rows = await shelterRequest<ListItem[]>(
        '/adoption-applications/' + encodeURIComponent(id) + '/simulation-reports',
        { signal },
      );
      if (!Array.isArray(rows)) throw new Error('리포트 목록을 확인할 수 없습니다.');
      return rows.map(entry);
    },
  });
export const reportDetailOptions = (id: string, session: string) =>
  queryOptions({
    queryKey: ['adoptions', id, 'simulation-reports', session],
    queryFn: async ({ signal }) => {
      const item = await shelterRequest<Detail>(
        '/simulations/' + encodeURIComponent(session) + '/report',
        { signal },
        'simulation',
      );
      if (!item || String(item.sessionSeq) !== session || !item.endedDttm)
        throw new Error('종료 리포트를 확인할 수 없습니다.');
      return {
        ...entry(item),
        status: outcomes[item.status],
        temperament: personalities[item.personality],
        budget: item.expectedCost,
        summary: item.summary,
        expenses: Object.entries(item.spentByCategory).map(([key, amount]) => ({
          key,
          label: categoryNames[key] ?? key,
          amount,
        })),
        daily: Array.from({ length: 7 }, (_, i) => item.dailySpent[String(i + 1)] ?? 0),
        state: (Object.keys(stats) as (keyof typeof stats)[]).map((key) => ({
          label: stats[key][0],
          icon: stats[key][1],
          value: item.finalStats[key] ?? 0,
        })),
        care: item.actionCount,
        walks: item.walkCount,
        events: item.eventCount,
        resolved: item.eventResolvedCount,
      };
    },
  });
