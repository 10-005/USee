import { Pressable, StyleSheet, Text, View } from 'react-native';

import ShoppingCartIcon from '../../assets/icons/shop/shopping-cart.svg';
import { colors, typography } from '../../theme';

type CartHeaderButtonProps = {
  count: number;
  onPress: () => void;
};

export function CartHeaderButton({ count, onPress }: CartHeaderButtonProps) {
  return (
    <Pressable
      accessibilityLabel={`장바구니 ${count}개`}
      accessibilityRole="button"
      hitSlop={8}
      onPress={onPress}
      style={styles.button}
    >
      <ShoppingCartIcon height={24} width={24} />
      {count > 0 ? (
        <View style={styles.badge}>
          <Text style={styles.badgeText}>{count}</Text>
        </View>
      ) : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 40,
    minWidth: 40,
  },
  badge: {
    position: 'absolute',
    top: 0,
    right: -2,
    backgroundColor: colors.danger,
    borderRadius: 9,
    minWidth: 18,
    height: 18,
    paddingHorizontal: 4,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: { ...typography.captionSemiBold12, color: colors.white, fontSize: 10, lineHeight: 12 },
});
