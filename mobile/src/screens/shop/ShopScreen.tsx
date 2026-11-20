import { useState } from 'react';
import {
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
import {
  AppHeader,
  Button,
  SearchBar,
  SkeletonBlock,
  SkeletonGroup,
} from '../../components/common';
import { CartHeaderButton } from '../../components/shop/CartHeaderButton';
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
    p => !selectedCategories.length || selectedCategories.some(category => p.category === category),
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
          <CartHeaderButton
            count={cartCount}
            onPress={() => navigation.navigate('ShopCart', { petId })}
          />
        }
      />
      {status === 'loading' ? (
        <ShopSkeleton cardWidth={(width - 52) / 2} />
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
              style={[
                styles.remainingBudget,
                shop.budget - shop.spent < 0 && styles.negativeBudget,
              ]}
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

function ShopSkeleton({ cardWidth }: { cardWidth: number }) {
  return (
    <SkeletonGroup accessibilityLabel="상품 불러오는 중" style={styles.skeletonContainer}>
      <SkeletonBlock style={styles.skeletonSearch} />
      <View style={styles.skeletonFilters}>
        {[58, 72, 64, 78].map(width => (
          <SkeletonBlock key={width} style={[styles.skeletonChip, { width }]} />
        ))}
      </View>
      <View style={styles.skeletonSortRow}>
        <SkeletonBlock style={styles.skeletonSort} />
      </View>
      <View style={styles.skeletonGrid}>
        {[0, 1, 2, 3].map(index => (
          <View key={index} style={{ width: cardWidth }}>
            <SkeletonBlock style={[styles.skeletonImage, { width: cardWidth }]} />
            <SkeletonBlock style={styles.skeletonBadge} />
            <SkeletonBlock style={styles.skeletonTitle} />
            <SkeletonBlock style={styles.skeletonPrice} />
          </View>
        ))}
      </View>
    </SkeletonGroup>
  );
}

const styles = StyleSheet.create({
  remainingBudget: { ...typography.captionRegular10, color: colors.gray500, textAlign: 'center' },
  negativeBudget: { color: colors.danger },
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
  skeletonBadge: { height: 12, marginTop: 10, width: '58%' },
  skeletonChip: { borderRadius: 13, height: 26 },
  skeletonContainer: { flex: 1, paddingHorizontal: 20, paddingTop: 4 },
  skeletonFilters: { flexDirection: 'row', gap: 6, marginTop: 14 },
  skeletonGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12, marginTop: 12 },
  skeletonImage: { aspectRatio: 1, borderRadius: 14 },
  skeletonPrice: { height: 14, marginTop: 7, width: '45%' },
  skeletonSearch: { height: 46, width: '100%' },
  skeletonSort: { height: 14, width: 52 },
  skeletonSortRow: { alignItems: 'flex-end', marginTop: 18 },
  skeletonTitle: { height: 15, marginTop: 8, width: '82%' },
});
