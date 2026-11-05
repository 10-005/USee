import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../app/navigation/types';
import { AppHeader, Button } from '../../components/common';
import { ProductArt } from '../../components/shop/ProductArt';
import { shopStyles as s } from '../../components/shop/shopStyles';
import { products, money } from '../../services/shop/catalog';
import { useShopStore } from '../../stores/shopStore';
import { useShopSession } from './useShopSession';
import { colors } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'ShopInventory'>;
export function InventoryScreen({ navigation, route }: Props) {
  const { petId } = route.params;
  const { status, retry } = useShopSession(petId);
  const shop = useShopStore();
  const purchased = products.filter(p => shop.ownedItems.some(item => item.productId === p.id));
  return (
    <SafeAreaView style={s.screen}>
      <AppHeader title="보유 목록" onBackPress={() => navigation.goBack()} />
      {status === 'loading' ? (
        <ActivityIndicator accessibilityLabel="보유 물품 불러오는 중" />
      ) : status === 'error' ? (
        <View style={s.empty}>
          <Text style={s.body}>보유 물품을 불러오지 못했어요.</Text>
          <Button onPress={retry}>다시 시도</Button>
        </View>
      ) : (
        <ScrollView contentContainerStyle={s.content}>
          <View style={s.banner}>
            <View style={s.between}>
              <Text style={s.label}>남은 금액</Text>
              <Text style={s.green}>{money(shop.budget - shop.spent)}</Text>
            </View>
            <Text style={s.body}>{`구매 금액 ${money(shop.spent)}`}</Text>
          </View>
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
const styles = StyleSheet.create({
  card: { borderWidth: 1, borderColor: colors.gray200, borderRadius: 12, padding: 14, gap: 12 },
  image: { width: 64 },
  packs: { borderTopWidth: 1, borderColor: colors.gray200, paddingTop: 12, gap: 8 },
  depleted: { color: colors.gray400 },
});
