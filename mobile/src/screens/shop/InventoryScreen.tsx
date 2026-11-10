import { useCallback, useEffect, useState } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import { Image, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../app/navigation/types';
import { AppHeader, Button, SkeletonBlock, SkeletonGroup } from '../../components/common';
import { ProductArt } from '../../components/shop/ProductArt';
import { shopStyles as s } from '../../components/shop/shopStyles';
import { ApiClientError } from '../../services/api';
import { products, money, type Product } from '../../services/shop/catalog';
import {
  getInventoryErrorMessage,
  loadInventoryOnEntry,
  type InventoryItem,
  type SimulationInventory,
} from '../../services/simulation/inventory';
import {
  getTransactionErrorMessage,
  isSimulationTransactionsStale,
  loadMoreSimulationTransactions,
  loadTransactionsOnEntry,
  type SimulationTransactionList,
} from '../../services/simulation/transactions';
import { useShopStore } from '../../stores/shopStore';
import { useShopSession } from './useShopSession';
import { colors, typography } from '../../theme';
import { ExpenseHistory } from './ExpenseHistory';

const categoryLabel: Record<InventoryItem['category'], string> = {
  FOOD_BOWL: '식사',
  WATER_BOWL: '식사',
  FOOD: '식사',
  SNACK: '간식',
  TOY: '놀이',
  FURNITURE: '가구',
  LEASH: '생활',
  GROOMING_KIT: '위생',
  LITTER_BOX: '위생',
  LITTER: '위생',
  SHAMPOO: '위생',
  MEDICINE: '생활',
  POOP_BAG: '위생',
  CATNIP: '놀이',
};

function displayProduct(item: InventoryItem): Product {
  return {
    id: String(item.itemSeq),
    name: item.itemName,
    qty: 1,
    unit: '개',
    species: 'all',
    kind: item.isConsumable ? 'consumable' : 'durable',
    category: categoryLabel[item.category],
    description: '',
    sprite: 0,
    price: 1,
    placement: item.isConsumable ? 'inventory' : 'room',
    imageUrl: item.imageUrl,
    itemSeq: item.itemSeq,
    serverCategory: item.category,
    market: { gamePrice: 1, basis: '', sources: [] },
  };
}

type Props = NativeStackScreenProps<RootStackParamList, 'ShopInventory'>;
export function InventoryScreen({ navigation, route }: Props) {
  const { petId } = route.params;
  const { session, status, retry } = useShopSession(petId);
  const shop = useShopStore();
  const [remoteInventory, setRemoteInventory] = useState<SimulationInventory | null>(null);
  const [remoteError, setRemoteError] = useState<string>();
  const [attempt, setAttempt] = useState(0);
  const [tab, setTab] = useState<'items' | 'expenses'>('items');
  const [expenseList, setExpenseList] = useState<SimulationTransactionList | null>(null);
  const [expenseLoading, setExpenseLoading] = useState(false);
  const [expenseError, setExpenseError] = useState<string>();
  const purchased = products.filter(p => shop.ownedItems.some(item => item.productId === p.id));
  useEffect(() => {
    if (status !== 'ready' || !session?.sessionSeq) {
      return undefined;
    }
    let active = true;
    setRemoteError(undefined);
    loadInventoryOnEntry(session.sessionSeq)
      .then(inventory => {
        if (active) {
          setRemoteInventory(inventory);
        }
      })
      .catch(error => {
        if (active) {
          setRemoteError(
            error instanceof ApiClientError
              ? getInventoryErrorMessage(error)
              : '보유 물품을 불러오지 못했어요. 다시 시도해주세요.',
          );
        }
      });
    return () => {
      active = false;
    };
  }, [attempt, session?.sessionSeq, status]);
  const reloadExpenses = useCallback(async () => {
    const sessionSeq = session?.sessionSeq;
    if (!sessionSeq) {
      return;
    }
    setExpenseLoading(true);
    setExpenseError(undefined);
    try {
      setExpenseList(await loadTransactionsOnEntry(sessionSeq));
    } catch (error) {
      setExpenseError(getTransactionErrorMessage(error));
    } finally {
      setExpenseLoading(false);
    }
  }, [session?.sessionSeq]);
  useFocusEffect(
    useCallback(() => {
      let active = true;
      const sessionSeq = session?.sessionSeq;
      if (!sessionSeq) {
        return undefined;
      }
      if (isSimulationTransactionsStale(sessionSeq)) {
        setExpenseLoading(true);
        setExpenseError(undefined);
      }
      loadTransactionsOnEntry(sessionSeq)
        .then(list => {
          if (active) {
            setExpenseList(list);
          }
        })
        .catch(error => {
          if (active) {
            setExpenseError(getTransactionErrorMessage(error));
          }
        })
        .finally(() => {
          if (active) {
            setExpenseLoading(false);
          }
        });
      return () => {
        active = false;
      };
    }, [session?.sessionSeq]),
  );
  const loadMoreExpenses = async () => {
    const sessionSeq = session?.sessionSeq;
    if (!sessionSeq || expenseLoading) {
      return;
    }
    setExpenseLoading(true);
    setExpenseError(undefined);
    try {
      setExpenseList(await loadMoreSimulationTransactions(sessionSeq));
    } catch (error) {
      setExpenseError(getTransactionErrorMessage(error));
    } finally {
      setExpenseLoading(false);
    }
  };
  const reloadRemote = () => setAttempt(current => current + 1);
  const balance = remoteInventory ? remoteInventory.balance : shop.budget - shop.spent;
  const itemsLoading =
    status === 'loading' || (!!session?.sessionSeq && !remoteInventory && !remoteError);
  const itemsError = status === 'error' || !!remoteError;
  return (
    <SafeAreaView style={s.screen}>
      <AppHeader
        title="내 물건"
        onBackPress={() => navigation.goBack()}
        rightActionWidth={136}
        rightAction={
          <View accessible accessibilityLabel={`남은 예산 ${money(balance)}`} style={styles.budget}>
            <Image
              source={require('../../assets/images/shop/img_money.png')}
              resizeMode="contain"
              style={styles.moneyIcon}
            />
            <Text
              adjustsFontSizeToFit
              minimumFontScale={0.7}
              numberOfLines={1}
              style={[styles.budgetText, balance < 0 && styles.negativeBudget]}
            >
              {money(balance)}
            </Text>
          </View>
        }
      />
      <View style={styles.tabs}>
        {(
          [
            { key: 'items', label: '보유 아이템' },
            { key: 'expenses', label: '지출 내역' },
          ] as const
        ).map(item => {
          const selected = tab === item.key;
          return (
            <Pressable
              key={item.key}
              accessibilityRole="tab"
              accessibilityLabel={item.label}
              accessibilityState={{ selected }}
              onPress={() => setTab(item.key)}
              style={[styles.tab, selected && styles.tabSelected]}
            >
              <Text style={[styles.tabLabel, selected && styles.tabLabelSelected]}>
                {item.label}
              </Text>
            </Pressable>
          );
        })}
      </View>
      {tab === 'expenses' ? (
        <ExpenseHistory
          balance={balance}
          error={expenseError}
          hasNext={expenseList?.hasNext ?? false}
          loaded={expenseList !== null}
          loading={expenseLoading}
          totalElements={expenseList?.totalElements ?? 0}
          transactions={expenseList?.items ?? []}
          onLoadMore={loadMoreExpenses}
          onRetry={reloadExpenses}
        />
      ) : itemsLoading ? (
        <InventorySkeleton />
      ) : itemsError ? (
        <View style={s.empty}>
          <Text style={s.body}>{remoteError ?? '보유 물품을 불러오지 못했어요.'}</Text>
          <Button onPress={session?.sessionSeq ? reloadRemote : retry}>다시 시도</Button>
        </View>
      ) : remoteInventory ? (
        <ServerInventory petId={petId} inventory={remoteInventory} navigation={navigation} />
      ) : (
        <ScrollView contentContainerStyle={s.content}>
          {!purchased.length ? (
            <View style={s.empty}>
              <Text style={s.label}>아직 구매한 물건이 없어요</Text>
              <Text style={s.body}>장바구니에서 구매하면 여기에 표시돼요.</Text>
              <Button onPress={() => navigation.navigate('PetShop', { petId })}>
                상점 둘러보기
              </Button>
            </View>
          ) : (
            purchased.map(p => {
              const packs = shop.ownedItems.filter(item => item.productId === p.id);
              return (
                <View key={p.id} style={styles.card}>
                  <View style={s.row}>
                    <Pressable
                      accessibilityRole="button"
                      accessibilityLabel={`${p.name} 상세 보기`}
                      style={styles.image}
                      onPress={() =>
                        navigation.navigate('ShopProductDetail', { petId, productId: p.id })
                      }
                    >
                      <ProductArt product={p} />
                    </Pressable>
                    <View style={s.flex}>
                      <Text style={s.label}>{p.name}</Text>
                      <Text style={s.body}>{`구매 ${packs.length}개 · ${
                        p.kind === 'consumable'
                          ? `남은 사용량 ${shop.inventory[p.id] ?? 0}${p.unit}`
                          : `보유 ${packs.length}개`
                      }`}</Text>
                    </View>
                  </View>
                  {p.kind === 'consumable' ? (
                    <View style={styles.packs}>
                      {packs.map((item, index) => (
                        <View key={item.id} style={s.between}>
                          <Text style={s.body}>{`${index + 1}번째 물품`}</Text>
                          <Text
                            accessibilityLabel={`${p.name} ${index + 1}번째 남은 수량 ${
                              item.remaining
                            }${p.unit}`}
                            style={[s.body, item.remaining === 0 && styles.depleted]}
                          >{`${item.remaining} / ${item.capacity}${p.unit}${
                            item.remaining === 0 ? ' · 사용 완료' : ''
                          }`}</Text>
                        </View>
                      ))}
                    </View>
                  ) : (
                    <Text style={s.body}>반복 사용 가능</Text>
                  )}
                </View>
              );
            })
          )}
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

function ServerInventory({
  petId,
  inventory,
  navigation,
}: {
  petId: string;
  inventory: SimulationInventory;
  navigation: Props['navigation'];
}) {
  const rows = inventory.items.map(item => ({
    key: `item-${item.itemSeq}`,
    name: item.itemName,
    detail: item.isConsumable && item.quantity === 0 ? '다 썼음' : `보유 ${item.quantity}개`,
    depleted: item.isConsumable && item.quantity === 0,
    productId: String(item.itemSeq),
    product: displayProduct(item),
  }));
  return (
    <ScrollView contentContainerStyle={s.content}>
      {!rows.length ? (
        <View style={s.empty}>
          <Text style={s.label}>아직 구매한 물건이 없어요</Text>
          <Text style={s.body}>장바구니에서 구매하면 여기에 표시돼요.</Text>
          <Button onPress={() => navigation.navigate('PetShop', { petId })}>상점 둘러보기</Button>
        </View>
      ) : (
        rows.map(row => (
          <View key={row.key} style={styles.card}>
            <View style={s.row}>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`${row.name} 상세 보기`}
                style={styles.image}
                onPress={() => {
                  if (!row.productId) {
                    return;
                  }
                  navigation.navigate('ShopProductDetail', { petId, productId: row.productId });
                }}
              >
                <ProductArt product={row.product} />
              </Pressable>
              <View style={s.flex}>
                <Text style={s.label}>{row.name}</Text>
                <Text style={[s.body, row.depleted && styles.depleted]}>{row.detail}</Text>
              </View>
            </View>
          </View>
        ))
      )}
    </ScrollView>
  );
}

function InventorySkeleton() {
  return (
    <SkeletonGroup accessibilityLabel="보유 물품 불러오는 중" style={styles.skeletonContainer}>
      <View style={styles.skeletonBanner}>
        <SkeletonBlock style={styles.skeletonBannerTitle} />
        <SkeletonBlock style={styles.skeletonBannerAmount} />
        <SkeletonBlock style={styles.skeletonBannerSubtext} />
      </View>
      {[0, 1, 2].map(index => (
        <View key={index} style={styles.skeletonCard}>
          <SkeletonBlock style={styles.skeletonImage} />
          <View style={styles.skeletonCardInfo}>
            <SkeletonBlock style={styles.skeletonTitle} />
            <SkeletonBlock style={styles.skeletonDescription} />
          </View>
        </View>
      ))}
    </SkeletonGroup>
  );
}

const styles = StyleSheet.create({
  budget: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-end',
    gap: 4,
  },
  moneyIcon: { width: 26, height: 26 },
  budgetText: {
    ...typography.bodySemiBold16,
    fontSize: 14,
    lineHeight: 18,
    color: colors.black,
    flexShrink: 1,
  },
  negativeBudget: { color: colors.danger },
  tabs: {
    flexDirection: 'row',
    marginHorizontal: 20,
    borderBottomWidth: 1,
    borderBottomColor: colors.gray200,
  },
  tab: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 2,
    borderBottomColor: colors.transparent,
  },
  tabSelected: { borderBottomColor: colors.green400 },
  tabLabel: { ...typography.bodySemiBold16, color: colors.gray400 },
  tabLabelSelected: { color: colors.green400 },
  card: { borderWidth: 1, borderColor: colors.gray200, borderRadius: 12, padding: 14, gap: 12 },
  image: { width: 64 },
  packs: { borderTopWidth: 1, borderColor: colors.gray200, paddingTop: 12, gap: 8 },
  depleted: { color: colors.gray400 },
  skeletonBanner: {
    backgroundColor: colors.gray10,
    borderRadius: 12,
    height: 94,
    padding: 16,
  },
  skeletonBannerAmount: { height: 16, position: 'absolute', right: 16, top: 16, width: 82 },
  skeletonBannerSubtext: { height: 13, marginTop: 14, width: 120 },
  skeletonBannerTitle: { height: 16, width: 62 },
  skeletonCard: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: 12,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 14,
    height: 94,
    padding: 14,
  },
  skeletonCardInfo: { flex: 1, gap: 10 },
  skeletonContainer: { gap: 12, padding: 20 },
  skeletonDescription: { height: 13, width: '72%' },
  skeletonImage: { height: 64, width: 64 },
  skeletonTitle: { height: 16, width: '46%' },
});
