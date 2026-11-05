import { useState } from 'react';
import { Image, StyleSheet, View } from 'react-native';
import type { Product } from '../../services/shop/catalog';

export function ProductArt({ product }: { product: Product }) {
  const [size, setSize] = useState(0);
  return (
    <View
      accessibilityLabel={product.name}
      onLayout={e => setSize(e.nativeEvent.layout.width)}
      style={styles.frame}
    >
      {size > 0 && (
        <Image
          source={
            product.atlas === 'items-extra-v2'
              ? require('../../assets/images/shop/items-extra-v2.png')
              : require('../../assets/images/shop/items-v2.png')
          }
          resizeMode="stretch"
          style={[
            styles.atlas,
            {
              width: size * 4,
              height: size * 4,
              left: -(product.sprite % 4) * size,
              top: -Math.floor(product.sprite / 4) * size,
            },
          ]}
        />
      )}
    </View>
  );
}
const styles = StyleSheet.create({
  atlas: { position: 'absolute' },
  frame: {
    width: '100%',
    aspectRatio: 1,
    overflow: 'hidden',
    borderRadius: 16,
    backgroundColor: '#FAFAF6',
  },
});
