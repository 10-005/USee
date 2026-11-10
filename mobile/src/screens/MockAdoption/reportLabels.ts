import type {
  SimulationReportPersonality,
  SimulationReportStat,
  SimulationReportStatus,
} from '../../services/simulation/reports';

const CATEGORY_LABEL: Record<string, string> = {
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

export const REPORT_STATUS_LABEL: Record<SimulationReportStatus, string> = {
  IN_PROGRESS: '진행 중',
  COMPLETED: '완료',
  ABANDONED: '중도 포기',
  FAILED: '돌봄 부족으로 종료',
};

export const REPORT_PERSONALITY_LABEL: Record<SimulationReportPersonality, string> = {
  ACTIVE: '활동적',
  TIMID: '소심함',
  AGGRESSIVE: '공격적',
  FRIENDLY: '사람 친화적',
};

export const REPORT_STAT_ITEMS: { key: SimulationReportStat; label: string }[] = [
  { key: 'satiety', label: '포만감' },
  { key: 'hydration', label: '수분 섭취' },
  { key: 'cleanliness', label: '청결도' },
  { key: 'bond', label: '유대감' },
  { key: 'calmness', label: '평온함' },
  { key: 'health', label: '건강' },
  { key: 'energy', label: '에너지' },
];

export function won(amount: number): string {
  return `${amount.toLocaleString('ko-KR')}원`;
}

export function categoryLabel(key: string): string {
  return CATEGORY_LABEL[key] ?? key;
}

export function expenseItems(spent: Record<string, number>) {
  return Object.entries(spent)
    .filter(([, amount]) => amount > 0)
    .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))
    .map(([key, amount]) => ({ key, label: categoryLabel(key), amount }));
}

export function reportDailyAmounts(daily: Record<string, number>): number[] {
  return [1, 2, 3, 4, 5, 6, 7].map(day => daily[String(day)] ?? 0);
}

export function dailyAxisStep(amounts: number[]): number {
  const peak = Math.max(0, ...amounts);
  return Math.max(1000, Math.ceil(peak / 4 / 5000) * 5000);
}

export function budgetGap(difference: number): { label: string; tone: 'over' | 'saved' | 'even' } {
  if (difference < 0) {
    return { label: `${won(-difference)} 초과`, tone: 'over' };
  }
  if (difference > 0) {
    return { label: `${won(difference)} 절약`, tone: 'saved' };
  }
  return { label: '예산과 일치', tone: 'even' };
}

export function reportDateTime(value: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value);
  if (!match) {
    return value;
  }
  return `${match[1]}.${match[2]}.${match[3]} ${match[4]}:${match[5]}`;
}

export function reportPeriod(started: string, ended: string | null): string {
  const start = reportDateTime(started);
  if (!ended) {
    return start;
  }
  return `${start} ~ ${reportDateTime(ended)}`;
}

export function statTone(value: number): 'low' | 'medium' | 'high' {
  if (value < 40) {
    return 'low';
  }
  if (value < 70) {
    return 'medium';
  }
  return 'high';
}
