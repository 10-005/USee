import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Button } from '../../components/common';
import { money } from '../../services/shop/catalog';
import type { ShopItemCategory } from '../../services/simulation/shopItems';
import type { SimulationTransaction } from '../../services/simulation/transactions';
import { colors, radius, typography } from '../../theme';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const CATEGORY_LABEL: Record<ShopItemCategory, string> = {
  FOOD_BOWL: '밥그릇',
  WATER_BOWL: '물그릇',
  LITTER_BOX: '화장실',
  TOY: '장난감',
  FURNITURE: '가구',
  LEASH: '리드줄',
  GROOMING_KIT: '위생 키트',
  FOOD: '사료',
  SNACK: '간식',
  LITTER: '배변용품',
  SHAMPOO: '샴푸',
  MEDICINE: '약',
  POOP_BAG: '배변봉투',
  CATNIP: '캣닢',
};

type ExpenseFilter = 'all' | 'PURCHASE' | 'EVENT';

const FILTERS: { key: ExpenseFilter; label: string }[] = [
  { key: 'all', label: '전체' },
  { key: 'PURCHASE', label: '물품 구매' },
  { key: 'EVENT', label: '이벤트' },
];

function clock(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(
    2,
    '0',
  )}`;
}

function dateLabel(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '날짜 없음';
  }
  return `${date.getMonth() + 1}월 ${date.getDate()}일 (${WEEKDAYS[date.getDay()]})`;
}

function dateKey(value: string | null) {
  if (!value) {
    return 'unknown';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return 'unknown';
  }
  return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
}

function transactionCopy(item: SimulationTransaction) {
  if (item.transactionType === 'EVENT') {
    const [title, detail] = (item.description ?? '동물병원 진료비').split(' · ');
    return { title, detail: detail ?? '' };
  }
  const title =
    item.description ?? (item.item ? `${item.item.itemName} × ${item.quantity}` : '물품 구매');
  const detail = item.item ? `${CATEGORY_LABEL[item.item.category]} · ${item.quantity}개 구매` : '';
  return { title, detail };
}

export function ExpenseHistory({
  balance,
  transactions,
  totalElements,
  hasNext,
  loaded,
  loading,
  error,
  onLoadMore,
  onRetry,
}: {
  balance: number;
  transactions: SimulationTransaction[];
  totalElements: number;
  hasNext: boolean;
  loaded: boolean;
  loading: boolean;
  error?: string;
  onLoadMore: () => void;
  onRetry: () => void;
}) {
  const [latestFirst, setLatestFirst] = useState(true);
  const [filter, setFilter] = useState<ExpenseFilter>('all');
  const visible = transactions
    .filter(item => filter === 'all' || item.transactionType === filter)
    .sort((left, right) => {
      const leftTime = left.regDttm ? Date.parse(left.regDttm) : 0;
      const rightTime = right.regDttm ? Date.parse(right.regDttm) : 0;
      return latestFirst ? rightTime - leftTime : leftTime - rightTime;
    });
  const groups = visible.reduce<
    Array<{ key: string; label: string; items: SimulationTransaction[] }>
  >((rows, item) => {
    const key = dateKey(item.regDttm);
    const current = rows[rows.length - 1];
    if (current?.key === key) {
      current.items.push(item);
      return rows;
    }
    rows.push({
      key,
      label: item.regDttm ? dateLabel(item.regDttm) : '날짜 없음',
      items: [item],
    });
    return rows;
  }, []);
  const filterLabel = FILTERS.find(item => item.key === filter)?.label ?? '전체';
  const count = filter === 'all' && loaded ? totalElements : visible.length;

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <Text style={styles.balanceLabel}>현재 잔액</Text>
      <Text style={[styles.balance, balance < 0 && styles.negativeBalance]}>{money(balance)}</Text>
      <Text style={styles.balanceHint}>모의 입양에 사용할 수 있는 금액이에요</Text>
      <View style={styles.toolbar}>
        <Pressable
          accessibilityLabel={latestFirst ? '최신순' : '과거순'}
          accessibilityRole="button"
          onPress={() => setLatestFirst(current => !current)}
        >
          <Text style={styles.sortText}>{latestFirst ? '최신순' : '과거순'}</Text>
        </Pressable>
      </View>
      <View style={styles.filters}>
        {FILTERS.map(item => {
          const selected = filter === item.key;
          return (
            <Pressable
              key={item.key}
              accessibilityRole="tab"
              accessibilityLabel={item.label}
              accessibilityState={{ selected }}
              onPress={() => setFilter(item.key)}
              style={[styles.filter, selected && styles.filterSelected]}
            >
              <Text style={[styles.filterText, selected && styles.filterTextSelected]}>
                {item.label}
              </Text>
            </Pressable>
          );
        })}
      </View>
      <Text style={styles.resultCount}>{`${filterLabel} ${count}건`}</Text>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      {loading && !transactions.length ? (
        <Text style={styles.empty}>지출 내역을 불러오는 중이에요</Text>
      ) : null}
      {!loading && error && !transactions.length ? (
        <Button onPress={onRetry}>다시 시도</Button>
      ) : null}
      {!loading && !error && !groups.length ? (
        <Text style={styles.empty}>지출 내역이 없어요</Text>
      ) : null}
      {groups.map(group => (
        <View key={group.key} style={styles.group}>
          <Text style={styles.date}>{group.label}</Text>
          {group.items.map(item => {
            const copy = transactionCopy(item);
            return (
              <View key={item.transactionSeq} style={styles.item}>
                <View style={styles.itemTop}>
                  <View style={styles.kind}>
                    <Text style={styles.kindText}>
                      {item.transactionType === 'EVENT' ? '이벤트' : '물품 구매'}
                    </Text>
                  </View>
                  <Text style={styles.time}>{item.regDttm ? clock(item.regDttm) : ''}</Text>
                </View>
                <View style={styles.itemTitleRow}>
                  <Text style={styles.itemTitle}>{copy.title}</Text>
                  <Text style={styles.itemAmount}>{`-${money(item.amount)}`}</Text>
                </View>
                {copy.detail ? <Text style={styles.itemDetail}>{copy.detail}</Text> : null}
                <Text style={styles.after}>{`거래 후 잔액 ${money(item.balanceAfter)}`}</Text>
              </View>
            );
          })}
        </View>
      ))}
      {hasNext ? (
        <Button disabled={loading} onPress={onLoadMore}>
          {loading ? '불러오는 중' : '이전 지출 더 보기'}
        </Button>
      ) : null}
      {groups.length && !hasNext ? (
        <Text style={styles.end}>모든 지출 내역을 확인했어요</Text>
      ) : null}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: 20, paddingTop: 8, paddingBottom: 40, gap: 12 },
  balanceLabel: { ...typography.bodyRegular14, color: colors.gray500 },
  balance: {
    ...typography.titleBold18,
    fontSize: 32,
    lineHeight: 42,
    color: colors.green400,
    marginTop: 6,
  },
  negativeBalance: { color: colors.danger },
  balanceHint: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 8 },
  toolbar: { marginTop: 8, alignItems: 'flex-end' },
  sortText: { ...typography.bodyRegular14, color: colors.gray500 },
  filters: { flexDirection: 'row', gap: 8 },
  filter: {
    borderWidth: 1,
    borderColor: colors.gray200,
    borderRadius: radius.pill,
    paddingHorizontal: 16,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.white,
  },
  filterSelected: { backgroundColor: colors.black, borderColor: colors.black },
  filterText: { ...typography.bodyRegular14, color: colors.gray700 },
  filterTextSelected: { color: colors.white },
  resultCount: { ...typography.bodySemiBold16, color: colors.black, marginTop: 8 },
  error: { ...typography.bodyRegular14, color: colors.danger },
  empty: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 16, textAlign: 'center' },
  group: { marginTop: 10, gap: 18 },
  date: { ...typography.bodyRegular14, color: colors.gray500 },
  item: { gap: 8 },
  itemTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  kind: {
    backgroundColor: colors.gray10,
    borderRadius: radius.sm,
    paddingHorizontal: 8,
    paddingVertical: 4,
  },
  kindText: { ...typography.captionSemiBold12, color: colors.gray600 },
  time: { ...typography.bodyRegular14, color: colors.gray400 },
  itemTitleRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: 12,
  },
  itemTitle: { ...typography.bodySemiBold16, color: colors.black, flex: 1 },
  itemAmount: { ...typography.bodySemiBold16, color: colors.black },
  itemDetail: { ...typography.bodyRegular14, color: colors.gray500 },
  after: { ...typography.bodyRegular14, color: colors.gray400, textAlign: 'right' },
  end: {
    ...typography.bodyRegular14,
    color: colors.gray400,
    textAlign: 'center',
    marginTop: 24,
  },
});
