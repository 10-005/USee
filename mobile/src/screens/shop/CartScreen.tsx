import { useRef, useState } from 'react';
import { Pressable, ScrollView, Text, View, StyleSheet } from 'react-native';
import { AppDialog } from '../../components/common/AppDialog';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../app/navigation/types';
import { AppHeader, Button, CheckIndicator } from '../../components/common';
import InfoIcon from '../../assets/icons/common/info.svg';
import { colors } from '../../theme';
import { ProductArt } from '../../components/shop/ProductArt';
import { QuantityControl } from '../../components/shop/QuantityControl';
import { availableProducts, findProduct, money } from '../../services/shop/catalog';
import { useShopStore, cartTotal } from '../../stores/shopStore';
import { useShopSession } from './useShopSession';
import { shopStyles as s } from '../../components/shop/shopStyles';

type Props = NativeStackScreenProps<RootStackParamList, 'ShopCart'>;
export function CartScreen({ navigation, route }: Props) {
  const { petId } = route.params;
  const { pet, status, retry } = useShopSession(petId);
  const shop = useShopStore();
  const confirming = useRef(false);
  const [purchaseDialog, setPurchaseDialog] = useState<{
    kind: 'confirm' | 'result';
    title: string;
    message: string;
  } | null>(null);
  const [guideVisible, setGuideVisible] = useState(false);
  const ids = Object.keys(shop.cart);
  const selected = ids.filter(id => shop.selectedIds.includes(id));
  const total = cartTotal(shop.cart, selected);
  const remaining = shop.budget - shop.spent;
  const essentials = availableProducts(pet?.category ?? 'dog').filter(p => p.essential);
  const prepared = essentials.filter(p => shop.inventory[p.id] || shop.cart[p.id]);
  const allChecked = ids.length > 0 && selected.length === ids.length;
  const preparationGuide = (
    <View style={styles.guide}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="필수 준비물 안내"
        accessibilityState={{ expanded: guideVisible }}
        onPress={() => setGuideVisible(current => !current)}
        style={styles.guideButton}
      >
        <Text style={s.label}>필수 준비물</Text>
        <InfoIcon width={20} height={20} />
      </Pressable>
      {guideVisible ? (
        <View style={styles.guideContent}>
          <Text
            style={s.body}
          >{`필수 준비물 ${prepared.length} / ${essentials.length}종 (보유·장바구니)`}</Text>
          {prepared.length < essentials.length ? (
            <Text style={s.body}>{`아직 준비하지 않은 물품: ${essentials
              .filter(p => !prepared.includes(p))
              .map(p => p.name)
              .join(', ')}`}</Text>
          ) : (
            <Text style={s.body}>필수 준비물을 모두 준비했어요.</Text>
          )}
          <View style={s.line} />
          <Text style={s.body}>
            사료는 게임상 일주일 21끼가 필요해요. 필요한 물품을 원하는 수량만큼 준비해주세요.
          </Text>
        </View>
      ) : null}
    </View>
  );
  const purchase = () => {
    if (confirming.current || !pet || status !== 'ready') {
      return;
    }
    confirming.current = true;
    setPurchaseDialog({
      kind: 'confirm',
      title: '모의 구매',
      message: `${selected.length}종 · ${money(total)}을 예산에서 사용할까요?`,
    });
  };
  const closePurchaseDialog = () => {
    confirming.current = false;
    setPurchaseDialog(null);
  };
  const confirmPurchase = () => {
    if (!confirming.current || !pet) {
      return;
    }
    confirming.current = false;
    const error = useShopStore.getState().checkout(pet.category);
    if (error) {
      setPurchaseDialog({ kind: 'result', title: '구매 확인', message: error });
      return;
    }
    setPurchaseDialog(null);
    navigation.popTo('ShopInventory', { petId });
  };
  return (
    <SafeAreaView style={s.screen}>
      <AppHeader title="장바구니" onBackPress={() => navigation.goBack()} />
      {status !== 'ready' ? (
        <View style={s.empty}>
          <Text style={s.body}>
            {status === 'loading'
              ? '장바구니를 불러오는 중이에요.'
              : '모의 입양 정보를 확인할 수 없어요.'}
          </Text>
          {status === 'error' && <Button onPress={retry}>다시 시도</Button>}
        </View>
      ) : !ids.length ? (
        <View style={s.empty}>
          <Text style={s.title}>장바구니가 비어 있어요</Text>
          <Text style={s.body}>함께할 일상에 필요한 물품을 골라보세요.</Text>
          <Text style={s.green}>남은 예산 {money(remaining)}</Text>
          {preparationGuide}
          <Button onPress={() => navigation.popTo('PetShop', { petId })}>상품 둘러보기</Button>
        </View>
      ) : (
        <>
          <ScrollView contentContainerStyle={s.content}>
            <View style={s.banner}>
              <View style={s.between}>
                <Text style={s.label}>남은 예산</Text>
                <Text style={s.green}>{money(remaining)}</Text>
              </View>
            </View>
            {preparationGuide}
            <View style={s.between}>
              <Pressable
                accessibilityRole="checkbox"
                accessibilityLabel="전체 선택"
                accessibilityState={{ checked: allChecked }}
                onPress={() => shop.selectAll(allChecked ? [] : ids)}
                style={styles.selection}
              >
                <CheckIndicator checked={allChecked} />
                <Text style={s.label}>{`전체 선택 (${selected.length}/${ids.length})`}</Text>
              </Pressable>
              <Pressable accessibilityRole="button" onPress={() => shop.remove(selected)}>
                <Text style={[s.body, styles.deleteText]}>선택 삭제</Text>
              </Pressable>
            </View>
            {ids.map(id => {
              const p = findProduct(id);
              if (!p) {
                return null;
              }
              return (
                <View key={id} style={styles.item}>
                  <Pressable
                    accessibilityRole="checkbox"
                    accessibilityLabel={`${p.name} 선택`}
                    accessibilityState={{ checked: selected.includes(id) }}
                    onPress={() => shop.toggle(id)}
                    style={styles.itemSelection}
                  >
                    <CheckIndicator checked={selected.includes(id)} />
                  </Pressable>
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={`${p.name} 상세 보기`}
                    style={styles.image}
                    onPress={() => navigation.popTo('ShopProductDetail', { petId, productId: id })}
                  >
                    <ProductArt product={p} />
                  </Pressable>
                  <View style={[s.flex, styles.info]}>
                    <View style={s.between}>
                      <Text style={[s.label, s.flex]}>{p.name}</Text>
                      <Pressable
                        accessibilityRole="button"
                        accessibilityLabel={`${p.name} 삭제`}
                        onPress={() => shop.remove([id])}
                      >
                        <Text style={[s.body, styles.deleteText]}>삭제</Text>
                      </Pressable>
                    </View>
                    <Text style={s.body}>
                      {p.qty}
                      {p.unit} / 묶음 · 총 {p.qty * shop.cart[id]}
                      {p.unit}
                    </Text>
                    <View style={styles.itemBottom}>
                      <Text
                        numberOfLines={1}
                        adjustsFontSizeToFit
                        minimumFontScale={0.8}
                        style={[s.price, styles.itemPrice]}
                      >
                        {money(p.price * shop.cart[id])}
                      </Text>
                      <QuantityControl
                        compact
                        name={p.name}
                        value={shop.cart[id]}
                        max={Number.MAX_SAFE_INTEGER}
                        onChange={n => shop.setQuantity(id, n, pet!.category)}
                      />
                    </View>
                  </View>
                </View>
              );
            })}
          </ScrollView>
          <View style={s.footer}>
            {shop.error ? (
              <Text accessibilityLiveRegion="polite" style={s.body}>
                {shop.error}
              </Text>
            ) : null}
            <View style={s.between}>
              <Text style={s.label}>선택 상품 합계</Text>
              <Text style={s.price}>{money(total)}</Text>
            </View>
            <Text style={s.body}>{`구매 후 남은 예산 ${money(remaining - total)}`}</Text>
            <Button disabled={!selected.length} onPress={purchase}>
              {`${selected.length}종 모의 구매하기`}
            </Button>
          </View>
        </>
      )}
      {purchaseDialog ? (
        <AppDialog
          title={purchaseDialog.title}
          message={purchaseDialog.message}
          confirmLabel={purchaseDialog.kind === 'confirm' ? '구매하기' : '확인'}
          cancelLabel={purchaseDialog.kind === 'confirm' ? '취소' : undefined}
          onClose={closePurchaseDialog}
          onConfirm={purchaseDialog.kind === 'confirm' ? confirmPurchase : closePurchaseDialog}
        />
      ) : null}
    </SafeAreaView>
  );
}
const styles = StyleSheet.create({
  selection: { flexDirection: 'row', alignItems: 'center', gap: 8, minHeight: 36 },
  deleteText: { color: colors.danger },
  guide: {
    alignSelf: 'stretch',
    borderWidth: 1,
    borderColor: colors.gray200,
    borderRadius: 12,
    padding: 14,
  },
  guideButton: { flexDirection: 'row', alignItems: 'center', gap: 6, minHeight: 32 },
  guideContent: { gap: 12, marginTop: 12 },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    padding: 12,
    borderWidth: 1,
    borderColor: colors.gray200,
    borderRadius: 12,
    backgroundColor: colors.white,
  },
  itemSelection: { minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  image: { width: 64 },
  info: { gap: 4, minWidth: 0 },
  itemBottom: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
  },
  itemPrice: { flex: 1, minWidth: 0 },
});
