import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../app/navigation/types';
import { AppHeader, Button } from '../../components/common';
import { ProductArt } from '../../components/shop/ProductArt';
import { shopStyles as s } from '../../components/shop/shopStyles';
import { ApiClientError } from '../../services/api';
import { products, money, type Product } from '../../services/shop/catalog';
import {
  getInventoryErrorMessage,
  getSimulationInventory,
  type InventoryItem,
  type SimulationInventory,
} from '../../services/simulation/inventory';
import { useShopStore } from '../../stores/shopStore';
import { useShopSession } from './useShopSession';
import { colors } from '../../theme';

const categoryLabel: Record<InventoryItem['category'], string> = {
  FOOD_BOWL: '식사',
  WATER_BOWL: '식사',
  FOOD: '식사',
  SNACK: '간식',
  TOY: '놀이',
  LITTER_BOX: '위생',
  LITTER: '위생',
  SHAMPOO: '위생',
  MEDICINE: '생활',
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
  const purchased = products.filter(p => shop.ownedItems.some(item => item.productId === p.id));
  useEffect(() => {
    if (status !== 'ready' || !session?.sessionSeq) {
      return undefined;
    }
    let active = true;
    setRemoteInventory(null);
    setRemoteError(undefined);
    getSimulationInventory(session.sessionSeq)
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
  const reloadRemote = () => setAttempt(current => current + 1);
  return (
    <SafeAreaView style={s.screen}>
      <AppHeader title="보유 목록" onBackPress={() => navigation.goBack()} />
      {status === 'loading' || (session?.sessionSeq && !remoteInventory && !remoteError) ? (
        <ActivityIndicator accessibilityLabel="보유 물품 불러오는 중" />
      ) : status === 'error' || remoteError ? (
        <View style={s.empty}>
          <Text style={s.body}>{remoteError ?? '보유 물품을 불러오지 못했어요.'}</Text>
          <Button onPress={session?.sessionSeq ? reloadRemote : retry}>다시 시도</Button>
        </View>
      ) : remoteInventory ? (
        <ServerInventory petId={petId} inventory={remoteInventory} navigation={navigation} />
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

function ServerInventory({
  petId,
  inventory,
  navigation,
}: {
  petId: string;
  inventory: SimulationInventory;
  navigation: Props['navigation'];
}) {
  const rows = [
    ...inventory.equipment.map(item => ({
      key: `equipment-${item.key}`,
      name: item.label,
      detail: '보유 중',
      depleted: false,
      productId: item.itemSeq ? String(item.itemSeq) : undefined,
      product: displayProduct({
        itemSeq: item.itemSeq ?? 1,
        itemName: item.label,
        category: 'TOY',
        isConsumable: false,
        quantity: 1,
        imageUrl: item.imageUrl ?? null,
      }),
    })),
    ...inventory.items.map(item => ({
      key: `item-${item.itemSeq}`,
      name: item.itemName,
      detail: item.isConsumable && item.quantity === 0 ? '다 썼음' : `보유 ${item.quantity}개`,
      depleted: item.isConsumable && item.quantity === 0,
      productId: String(item.itemSeq),
      product: displayProduct(item),
    })),
  ];
  return (
    <ScrollView contentContainerStyle={s.content}>
      <View style={s.banner}>
        <View style={s.between}>
          <Text style={s.label}>남은 금액</Text>
          <Text style={s.green}>{money(inventory.balance)}</Text>
        </View>
        <Text style={s.body}>{`예상 예산 ${money(inventory.expectedCost)}`}</Text>
      </View>
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

const styles = StyleSheet.create({
  card: { borderWidth: 1, borderColor: colors.gray200, borderRadius: 12, padding: 14, gap: 12 },
  image: { width: 64 },
  packs: { borderTopWidth: 1, borderColor: colors.gray200, paddingTop: 12, gap: 8 },
  depleted: { color: colors.gray400 },
});
