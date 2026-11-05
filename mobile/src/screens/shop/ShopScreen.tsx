import { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  ScrollView,
  Text,
  View,
  StyleSheet,
  useWindowDimensions,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../app/navigation/types';
import { AppHeader, Button, SearchBar, FilterChip } from '../../components/common';
import { ProductArt } from '../../components/shop/ProductArt';
import { QuantityControl } from '../../components/shop/QuantityControl';
import { categories, filterProducts, money } from '../../services/shop/catalog';
import { useShopStore, cartTotal } from '../../stores/shopStore';
import { useShopSession } from './useShopSession';
import { shopStyles as s } from '../../components/shop/shopStyles';
import { BudgetDialog, ShopOptionDialog } from './ShopDialogs';
import { colors, typography } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'PetShop'>;
export function ShopScreen({ navigation, route }: Props) {
  const { petId } = route.params;
  const { pet, status, retry } = useShopSession(petId);
  const shop = useShopStore();
  const { width } = useWindowDimensions();
  const [category, setCategory] = useState('전체');
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState('기본순');
  const [dialog, setDialog] = useState<'category' | 'sort' | 'budget' | null>(null);
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [toast, setToast] = useState('');
  const toastBusy = useRef(false);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  useEffect(() => () => clearTimeout(toastTimer.current), []);
  const items = filterProducts(pet?.category ?? 'dog', category, query, sort);
  const cartCount = Object.values(shop.cart).reduce((sum, n) => sum + n, 0);
  const add = (id: string) => {
    if (!pet || toastBusy.current || !(quantities[id] > 0)) {
      return;
    }
    if (!shop.addToCart(id, quantities[id], pet.category)) {
      return;
    }
    toastBusy.current = true;
    setToast('담기 완료');
    toastTimer.current = setTimeout(() => {
      setToast('');
      setQuantities(current => ({ ...current, [id]: 0 }));
      toastBusy.current = false;
    }, 1500);
  };
  return (
    <SafeAreaView style={s.screen}>
      <AppHeader
        title="상점"
        rightActionWidth={Math.min(160, (width - 100) / 2)}
        onBackPress={() => navigation.goBack()}
        rightAction={
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`장바구니 ${cartCount}개`}
            style={styles.cartAction}
            onPress={() => navigation.navigate('ShopCart', { petId })}
          >
            <Text numberOfLines={1} style={s.green}>{`담은 상품 ${cartCount}`}</Text>
          </Pressable>
        }
      />
      {status === 'loading' ? (
        <ActivityIndicator accessibilityLabel="상품 불러오는 중" />
      ) : status === 'error' || !pet ? (
        <View style={s.empty}>
          <Text style={s.body}>진행 중인 모의 입양 정보를 확인할 수 없어요.</Text>
          <Button onPress={retry}>다시 시도</Button>
        </View>
      ) : (
        <>
          <View style={styles.budget}>
            <View>
              <Text style={s.body}>남은 예산</Text>
              <Text style={s.green}>{money(shop.budget - shop.spent)}</Text>
            </View>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="예산 추가"
              onPress={() => setDialog('budget')}
              style={styles.budgetButton}
            >
              <Text style={s.green}>＋ 예산 추가</Text>
            </Pressable>
          </View>
          <View style={styles.searchRow}>
            <SearchBar
              accessibilityLabel="상품 검색"
              placeholder="상품을 검색해보세요"
              value={query}
              onChangeText={setQuery}
            />
          </View>
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.filters}
            style={styles.filterScroll}
          >
            <FilterChip
              label={category === '전체' ? '분류' : category}
              accessibilityLabel="상품 분류 필터"
              open={dialog === 'category'}
              applied={category !== '전체'}
              onPress={() => setDialog('category')}
            />
            <FilterChip
              label={sort}
              accessibilityLabel="상품 정렬 필터"
              open={dialog === 'sort'}
              applied={sort !== '기본순'}
              onPress={() => setDialog('sort')}
            />
          </ScrollView>
          <FlatList
            data={items}
            numColumns={2}
            keyExtractor={p => p.id}
            contentContainerStyle={styles.list}
            columnWrapperStyle={styles.columns}
            keyboardShouldPersistTaps="handled"
            extraData={{ quantities, toast }}
            ListEmptyComponent={
              <View style={s.empty}>
                <Text style={s.label}>검색한 상품이 없어요</Text>
                <Button
                  onPress={() => {
                    setQuery('');
                    setCategory('전체');
                  }}
                >
                  전체 상품 보기
                </Button>
              </View>
            }
            renderItem={({ item: p }) => (
              <View style={[styles.card, { width: (width - 52) / 2 }]}>
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel={`${p.name} 상세 보기`}
                  onPress={() =>
                    navigation.navigate('ShopProductDetail', { petId, productId: p.id })
                  }
                >
                  <ProductArt product={p} />
                  <Text style={styles.badge}>{`${p.essential ? '필수 준비' : p.category} · ${
                    p.qty
                  }${p.unit}`}</Text>
                  <Text style={s.label}>{p.name}</Text>
                  <Text style={s.price}>{money(p.price)}</Text>
                </Pressable>
                <View style={styles.addRow}>
                  <QuantityControl
                    compact
                    min={0}
                    max={10}
                    value={quantities[p.id] ?? 0}
                    name={p.name}
                    disabled={Boolean(toast)}
                    onChange={n =>
                      setQuantities(current => ({
                        ...current,
                        [p.id]: Math.max(0, Math.min(10, n)),
                      }))
                    }
                  />
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={`${p.name} 장바구니 담기`}
                    accessibilityState={{ disabled: Boolean(toast) || !quantities[p.id] }}
                    disabled={Boolean(toast) || !quantities[p.id]}
                    style={[styles.add, (Boolean(toast) || !quantities[p.id]) && s.disabled]}
                    onPress={() => add(p.id)}
                  >
                    <Text style={styles.addText}>담기</Text>
                  </Pressable>
                </View>
              </View>
            )}
          />
          {shop.error ? (
            <Text accessibilityLiveRegion="polite" style={styles.error}>
              {shop.error}
            </Text>
          ) : null}
          <View style={s.footer}>
            <Button
              onPress={() => navigation.navigate('ShopCart', { petId })}
            >{`장바구니 ${cartCount}개 · ${money(cartTotal(shop.cart))}`}</Button>
          </View>
          {toast ? (
            <View pointerEvents="none" style={styles.toast}>
              <Text
                accessibilityRole="alert"
                accessibilityLiveRegion="polite"
                style={styles.toastText}
              >
                {toast}
              </Text>
            </View>
          ) : null}
          {dialog === 'budget' ? <BudgetDialog onClose={() => setDialog(null)} /> : null}
          {dialog === 'category' || dialog === 'sort' ? (
            <ShopOptionDialog
              title={dialog === 'category' ? '상품 분류' : '정렬'}
              options={
                dialog === 'category' ? categories : ['기본순', '낮은 가격순', '높은 가격순']
              }
              value={dialog === 'category' ? category : sort}
              onSelect={dialog === 'category' ? setCategory : setSort}
              onClose={() => setDialog(null)}
            />
          ) : null}
        </>
      )}
    </SafeAreaView>
  );
}
const styles = StyleSheet.create({
  budget: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  budgetButton: { borderWidth: 1, borderColor: colors.green400, borderRadius: 10, padding: 10 },
  searchRow: { flexDirection: 'row', marginHorizontal: 20, marginTop: 4 },
  filterScroll: { flexGrow: 0, height: 54 },
  filters: { gap: 6, paddingHorizontal: 20, paddingVertical: 14 },
  list: { paddingHorizontal: 20, paddingBottom: 24 },
  columns: { gap: 12 },
  card: { gap: 12, marginBottom: 20 },
  badge: { ...typography.captionSemiBold12, color: colors.gray500, marginVertical: 8 },
  cartAction: { flexDirection: 'row', alignItems: 'center', minHeight: 40, paddingHorizontal: 4 },
  addRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  add: {
    backgroundColor: colors.green400,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 38,
    flex: 1,
    minWidth: 40,
  },
  addText: { ...typography.bodyRegular14, color: colors.white },
  error: { ...typography.bodyRegular14, textAlign: 'center', color: colors.danger, padding: 8 },
  toast: {
    position: 'absolute',
    bottom: 100,
    alignSelf: 'center',
    backgroundColor: colors.gray800,
    paddingHorizontal: 28,
    paddingVertical: 14,
    borderRadius: 24,
  },
  toastText: { ...typography.bodyRegular14, color: colors.white },
});
