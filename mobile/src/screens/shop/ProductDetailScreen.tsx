import { useState } from 'react';
import { Alert, Linking, Pressable, ScrollView, Text, View } from 'react-native';
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

type Props = NativeStackScreenProps<RootStackParamList, 'ShopProductDetail'>;
export function ProductDetailScreen({ navigation, route }: Props) {
  const { petId, productId } = route.params;
  const { pet, status, retry } = useShopSession(petId);
  const p = findProduct(productId);
  const shop = useShopStore();
  const [quantity, setQuantity] = useState(1);
  const [notice, setNotice] = useState('');
  const max = 10;
  const inCart = shop.cart[productId] ?? 0;
  const allowed = pet && p && (p.species === 'all' || pet.category === p.species);
  return (
    <SafeAreaView style={s.screen}>
      <AppHeader
        title="상품 상세"
        onBackPress={() => navigation.goBack()}
        rightAction={
          <Pressable
            accessibilityRole="button"
            onPress={() => navigation.navigate('ShopCart', { petId })}
          >
            <Text style={s.green}>장바구니</Text>
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
            <Text style={s.green}>
              {p.category} · {p.essential ? '필수 준비물' : '선택 물품'}
            </Text>
            <Text style={s.title}>{p.name}</Text>
            <Text style={s.price}>{money(p.price)}</Text>
            <Text style={s.body}>{p.description}</Text>
            <View style={s.banner}>
              <Text style={s.label}>상품 정보</Text>
              <Text style={s.body}>
                {p.species === 'all'
                  ? '강아지·고양이 공용'
                  : p.species === 'dog'
                  ? '강아지 전용'
                  : '고양이 전용'}{' '}
                · 한 묶음 {p.qty}
                {p.unit}
              </Text>
              <Text style={s.body}>
                {p.kind === 'durable'
                  ? '사용 후에도 다시 사용할 수 있는 물품이에요.'
                  : '사용할 때마다 수량이 줄어드는 소모품이에요.'}
              </Text>
              <Text style={s.body}>
                {p.placement === 'room' ? '방에 설치하는 물품' : '가방에 보관하는 물품'} · 보유{' '}
                {shop.inventory[p.id] ?? 0}
                {p.unit}
              </Text>
            </View>
            <Text style={s.label}>가격 근거</Text>
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
              참고 프로젝트의 2026.10.21 조사 가격이에요. 사용 횟수는 모의 입양의 게임 단위이며 실제
              포장량과 달라요.
            </Text>
          </ScrollView>
          <View style={s.footer}>
            {status === 'error' ? <Button onPress={retry}>쇼핑 정보 다시 불러오기</Button> : null}
            <Text accessibilityLiveRegion="polite" style={s.body}>
              {shop.error ||
                notice ||
                (inCart
                  ? `장바구니에 ${inCart}묶음 담겨 있어요.`
                  : '모의 구매에 사용할 수 있어요.')}
            </Text>
            <View style={s.between}>
              <QuantityControl name={p.name} value={quantity} max={max} onChange={setQuantity} />
              <Text style={s.price}>{money(p.price * quantity)}</Text>
            </View>
            <Button
              disabled={status !== 'ready'}
              onPress={() => {
                if (shop.addToCart(p.id, quantity, pet.category)) {
                  setNotice(`${quantity}묶음을 장바구니에 담았어요.`);
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
