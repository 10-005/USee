import { useEffect, useRef, useState } from 'react';
import { Alert, Linking, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../app/navigation/types';
import { AppHeader, Button } from '../../components/common';
import { ProductArt } from '../../components/shop/ProductArt';
import { QuantityControl } from '../../components/shop/QuantityControl';
import { findProduct, money } from '../../services/shop/catalog';
import { useShopStore } from '../../stores/shopStore';
import { useShopSession } from './useShopSession';
import { shopStyles as s } from '../../components/shop/shopStyles';
import { colors, typography } from '../../theme';
import FilterArrowIcon from '../../assets/icons/common/filter-arrow.svg';

type Props = NativeStackScreenProps<RootStackParamList, 'ShopProductDetail'>;
export function ProductDetailScreen({ navigation, route }: Props) {
  const { petId, productId } = route.params;
  const { pet, status, retry } = useShopSession(petId);
  const p = findProduct(productId);
  const shop = useShopStore();
  const [quantity, setQuantity] = useState(0);
  const [toast, setToast] = useState(false);
  const [basisExpanded, setBasisExpanded] = useState(false);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const adding = useRef(false);
  useEffect(() => () => clearTimeout(toastTimer.current), []);
  useEffect(() => {
    if (quantity === 0) {
      adding.current = false;
    }
  }, [quantity]);
  const max = 10;
  const inCart = shop.cart[productId] ?? 0;
  const cartCount = Object.values(shop.cart).reduce((sum, count) => sum + count, 0);
  const allowed = pet && p && (p.species === 'all' || pet.category === p.species);
  return (
    <SafeAreaView style={s.screen}>
      <AppHeader
        title="상품 상세"
        onBackPress={() => navigation.goBack()}
        rightAction={
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`장바구니 ${cartCount}개`}
            style={styles.cartAction}
            onPress={() => navigation.navigate('ShopCart', { petId })}
          >
            <Text style={s.green}>장바구니</Text>
            {cartCount > 0 ? (
              <View style={styles.cartBadge}>
                <Text style={styles.cartBadgeText}>{cartCount}</Text>
              </View>
            ) : null}
          </Pressable>
        }
      />
      {!allowed || !p ? (
        <View style={s.empty}>
          <Text style={s.body}>이 반려동물이 사용할 수 있는 상품을 찾을 수 없어요.</Text>
        </View>
      ) : (
        <>
          <ScrollView contentContainerStyle={s.content}>
            <ProductArt product={p} />
            <View>
              <Text style={s.body}>
                {p.category} · {p.essential ? '필수 준비물' : '선택 물품'}
              </Text>
              <Text style={[s.title, styles.productName]}>{p.name}</Text>
              <Text style={[styles.price, styles.productPrice]}>{money(p.price)}</Text>
            </View>
            <Text style={s.body}>{p.description}</Text>
            <View style={styles.infoSection}>
              <Text style={styles.sectionTitle}>상품 정보</Text>
              {[
                [
                  '사용 대상',
                  p.species === 'all'
                    ? '강아지·고양이 공용'
                    : p.species === 'dog'
                    ? '강아지 전용'
                    : '고양이 전용',
                ],
                ['상품 구성', `한 묶음 ${p.qty}${p.unit}`],
                [
                  '사용 방식',
                  p.kind === 'durable'
                    ? '사용 후에도 다시 사용할 수 있는 물품이에요.'
                    : '사용할 때마다 수량이 줄어드는 소모품이에요.',
                ],
                [
                  '보관 위치',
                  p.placement === 'room' ? '방에 설치하는 물품' : '가방에 보관하는 물품',
                ],
                [
                  p.kind === 'consumable' ? '남은 사용량' : '보유 수량',
                  `${shop.inventory[p.id] ?? 0}${p.unit}`,
                ],
              ].map(([label, value]) => (
                <View key={label} style={styles.infoRow}>
                  <Text style={styles.infoLabel}>{label}</Text>
                  <Text style={styles.infoValue}>{value}</Text>
                </View>
              ))}
            </View>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="가격 근거"
              accessibilityState={{ expanded: basisExpanded }}
              onPress={() => setBasisExpanded(current => !current)}
              style={styles.basisToggle}
            >
              <Text style={s.label}>가격 근거</Text>
              <FilterArrowIcon width={18} height={18} />
            </Pressable>
            {basisExpanded ? (
              <View style={styles.basisContent}>
                <Text style={s.body}>{p.market.basis}</Text>
                {p.market.sources.map(source => (
                  <Pressable
                    key={source.url + source.product}
                    accessibilityRole="link"
                    onPress={() => {
                      Linking.openURL(source.url).catch(() =>
                        Alert.alert('링크를 열지 못했어요', '잠시 후 다시 시도해주세요.'),
                      );
                    }}
                  >
                    <Text style={s.green}>
                      {source.shop} · {source.product} ↗
                    </Text>
                    <Text style={s.body}>
                      {source.pack} · {money(source.price)}
                    </Text>
                  </Pressable>
                ))}
                <Text style={s.body}>
                  참고 프로젝트의 2026.10.21 조사 가격이에요. 사용 횟수는 모의 입양의 게임 단위이며
                  실제 포장량과 달라요.
                </Text>
              </View>
            ) : null}
          </ScrollView>
          <View style={s.footer}>
            {toast ? (
              <View pointerEvents="none" style={styles.toast}>
                <Text
                  accessibilityRole="alert"
                  accessibilityLiveRegion="polite"
                  style={styles.toastText}
                >
                  장바구니에 추가되었습니다
                </Text>
              </View>
            ) : null}
            {status === 'error' ? <Button onPress={retry}>쇼핑 정보 다시 불러오기</Button> : null}
            <Text accessibilityLiveRegion="polite" style={s.body}>
              {shop.error ||
                (inCart
                  ? `장바구니에 ${inCart}묶음 담겨 있어요.`
                  : '모의 구매에 사용할 수 있어요.')}
            </Text>
            <View style={s.between}>
              <QuantityControl
                compact
                min={0}
                name={p.name}
                value={quantity}
                max={max}
                onChange={setQuantity}
              />
              <Text style={styles.price}>{money(p.price * quantity)}</Text>
            </View>
            <Button
              disabled={status !== 'ready' || quantity === 0}
              onPress={() => {
                if (adding.current || quantity === 0) {
                  return;
                }
                adding.current = true;
                if (shop.addToCart(p.id, quantity, pet.category)) {
                  setQuantity(0);
                  setToast(true);
                  clearTimeout(toastTimer.current);
                  toastTimer.current = setTimeout(() => setToast(false), 1500);
                } else {
                  adding.current = false;
                }
              }}
            >
              장바구니 담기
            </Button>
          </View>
        </>
      )}
    </SafeAreaView>
  );
}
const styles = StyleSheet.create({
  infoSection: { borderTopWidth: 1, borderTopColor: colors.gray200, paddingVertical: 24, gap: 10 },
  sectionTitle: { ...typography.titleBold18, color: colors.black, marginBottom: 8 },
  infoRow: { flexDirection: 'row' },
  infoLabel: { ...typography.bodyRegular14, color: colors.gray400, width: 110 },
  infoValue: { ...typography.bodyRegular14, color: colors.black, flex: 1 },
  productName: { marginTop: 10 },
  productPrice: { marginTop: 16 },
  price: { ...typography.titleBold18, fontSize: 20, lineHeight: 28, color: colors.blue },
  basisToggle: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    minHeight: 44,
  },
  basisContent: { gap: 16 },
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
  toast: {
    position: 'absolute',
    bottom: '100%',
    marginBottom: 12,
    alignSelf: 'center',
    backgroundColor: colors.gray800,
    borderRadius: 24,
    paddingHorizontal: 24,
    paddingVertical: 12,
  },
  toastText: { ...typography.bodyRegular14, color: colors.white },
});
