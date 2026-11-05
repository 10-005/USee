import { useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Keyboard,
  TextInput,
  findNodeHandle,
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
import { AppHeader, Button, SearchBar } from '../../components/common';
import { ProductArt } from '../../components/shop/ProductArt';
import { categories, filterProducts, money } from '../../services/shop/catalog';
import { useShopStore, cartTotal } from '../../stores/shopStore';
import { useShopSession } from './useShopSession';
import { shopStyles as s } from '../../components/shop/shopStyles';
import { ShopOptionDialog } from './ShopDialogs';
import FilterArrowIcon from '../../assets/icons/common/filter-arrow.svg';
import { colors, typography } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'PetShop'>;
export function ShopScreen({ navigation, route }: Props) {
  const { petId } = route.params;
  const { pet, status, retry } = useShopSession(petId);
  const shop = useShopStore();
  const { width } = useWindowDimensions();
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState('기본순');
  const [sortOpen, setSortOpen] = useState(false);
  const items = filterProducts(pet?.category ?? 'dog', '전체', query, sort).filter(
    p =>
      !selectedCategories.length ||
      selectedCategories.some(category =>
        category === '필수 준비' ? p.essential : p.category === category,
      ),
  );
  const cartCount = Object.values(shop.cart).reduce((sum, n) => sum + n, 0);
  const toggleCategory = (category: string) => {
    Keyboard.dismiss();
    setSelectedCategories(current =>
      category === '전체'
        ? []
        : current.includes(category)
        ? current.filter(value => value !== category)
        : [...current, category],
    );
  };
  return (
    <SafeAreaView
      style={s.screen}
      onTouchStart={event => {
        const focusedInput = TextInput.State.currentlyFocusedInput();
        if (focusedInput && event.nativeEvent.target !== findNodeHandle(focusedInput)) {
          Keyboard.dismiss();
        }
      }}
    >
      <AppHeader
        title="상점"
        onBackPress={() => navigation.goBack()}
        rightAction={
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="장바구니"
            accessibilityValue={{ text: `${cartCount}개` }}
            style={styles.cartAction}
            onPress={() => navigation.navigate('ShopCart', { petId })}
          >
            <Text numberOfLines={1} style={s.green}>
              장바구니
            </Text>
            {cartCount > 0 ? (
              <View style={styles.cartBadge}>
                <Text style={styles.cartBadgeText}>{cartCount}</Text>
              </View>
            ) : null}
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
          <View style={styles.searchRow}>
            <SearchBar
              accessibilityLabel="상품 검색"
              placeholder="상품을 검색해보세요"
              value={query}
              onChangeText={setQuery}
            />
          </View>
          <View style={styles.filterBar}>
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={styles.filters}
              style={styles.filterScroll}
              keyboardShouldPersistTaps="handled"
              keyboardDismissMode="on-drag"
              onScrollBeginDrag={Keyboard.dismiss}
            >
              {categories.map(category => {
                const selected =
                  category === '전체'
                    ? !selectedCategories.length
                    : selectedCategories.includes(category);
                return (
                  <Pressable
                    key={category}
                    accessibilityRole="checkbox"
                    accessibilityLabel={`${category} 분류`}
                    accessibilityState={{ checked: selected }}
                    onPress={() => toggleCategory(category)}
                    style={[styles.category, selected && styles.selectedCategory]}
                  >
                    <Text style={[styles.categoryText, selected && styles.selectedCategoryText]}>
                      {category}
                    </Text>
                  </Pressable>
                );
              })}
            </ScrollView>
          </View>
          <View style={styles.sortRow}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="상품 정렬 필터"
              accessibilityState={{ expanded: sortOpen }}
              style={styles.sortButton}
              onPress={() => {
                Keyboard.dismiss();
                setSortOpen(true);
              }}
            >
              <Text style={styles.categoryText}>{sort}</Text>
              <FilterArrowIcon width={14} height={14} />
            </Pressable>
          </View>
          <FlatList
            data={items}
            numColumns={2}
            keyExtractor={p => p.id}
            contentContainerStyle={styles.list}
            columnWrapperStyle={styles.columns}
            style={styles.productList}
            keyboardShouldPersistTaps="never"
            keyboardDismissMode="on-drag"
            onScrollBeginDrag={Keyboard.dismiss}
            onTouchStart={Keyboard.dismiss}
            ListEmptyComponent={
              <View style={s.empty}>
                <Text style={s.label}>검색한 상품이 없어요</Text>
                <Button
                  onPress={() => {
                    setQuery('');
                    setSelectedCategories([]);
                  }}
                >
                  전체 상품 보기
                </Button>
              </View>
            }
            renderItem={({ item: p }) => (
              <Pressable
                style={[styles.card, { width: (width - 52) / 2 }]}
                accessibilityRole="button"
                accessibilityLabel={`${p.name} 상세 보기`}
                onPress={() => navigation.navigate('ShopProductDetail', { petId, productId: p.id })}
              >
                <ProductArt product={p} />
                <Text style={styles.badge}>{`${p.essential ? '필수 준비' : p.category} · ${p.qty}${
                  p.unit
                }`}</Text>
                <Text style={s.label}>{p.name}</Text>
                <Text style={s.price}>{money(p.price)}</Text>
              </Pressable>
            )}
          />
          <View style={s.footer}>
            <Text
              numberOfLines={1}
              accessibilityLiveRegion="polite"
              style={styles.remainingBudget}
            >{`남은 예산 ${money(shop.budget - shop.spent)}`}</Text>
            <Button
              onPress={() => navigation.navigate('ShopCart', { petId })}
            >{`장바구니 ${cartCount}개 · ${money(cartTotal(shop.cart))}`}</Button>
          </View>
          {sortOpen ? (
            <ShopOptionDialog
              title="정렬"
              options={['기본순', '낮은 가격순', '높은 가격순']}
              value={sort}
              onSelect={setSort}
              onClose={() => setSortOpen(false)}
            />
          ) : null}
        </>
      )}
    </SafeAreaView>
  );
}
const styles = StyleSheet.create({
  remainingBudget: { ...typography.captionRegular10, color: colors.gray500, textAlign: 'center' },
  searchRow: { flexDirection: 'row', marginHorizontal: 20, marginTop: 4 },
  filterBar: { height: 54, minHeight: 54, flexShrink: 0, backgroundColor: colors.white },
  filterScroll: { flex: 1 },
  filters: { gap: 6, paddingHorizontal: 20, alignItems: 'center' },
  category: {
    borderWidth: 1,
    borderColor: colors.gray200,
    borderRadius: 24,
    paddingHorizontal: 10,
    height: 26,
    justifyContent: 'center',
  },
  selectedCategory: { borderColor: colors.green400, backgroundColor: '#ECFDF3' },
  categoryText: { ...typography.bodyRegular14, color: colors.gray500 },
  selectedCategoryText: { color: colors.green400 },
  sortRow: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    paddingHorizontal: 20,
    paddingBottom: 8,
    flexShrink: 0,
  },
  sortButton: { flexDirection: 'row', alignItems: 'center', gap: 4, minHeight: 32 },
  productList: { flex: 1 },
  list: { paddingHorizontal: 20, paddingBottom: 24 },
  columns: { gap: 12 },
  card: { marginBottom: 20 },
  badge: {
    ...typography.captionRegular10,
    fontSize: 12,
    lineHeight: 15,
    color: colors.gray500,
    marginVertical: 8,
  },
  cartAction: { minHeight: 40, justifyContent: 'center', paddingRight: 6 },
  cartBadge: {
    position: 'absolute',
    right: -6,
    bottom: -2,
    backgroundColor: colors.danger,
    borderRadius: 12,
    minWidth: 22,
    minHeight: 18,
    paddingHorizontal: 6,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cartBadgeText: { ...typography.captionSemiBold12, color: colors.white },
});
