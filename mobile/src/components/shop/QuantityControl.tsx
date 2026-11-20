import { Pressable, StyleSheet, Text, View } from 'react-native';
import { shopStyles as s } from './shopStyles';

export function QuantityControl({
  value,
  max,
  onChange,
  name,
  min = 1,
  disabled = false,
  compact = false,
}: {
  value: number;
  max: number;
  onChange: (n: number) => void;
  name: string;
  min?: number;
  disabled?: boolean;
  compact?: boolean;
}) {
  return (
    <View style={s.quantity}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${name} 수량 줄이기`}
        disabled={disabled || value <= min}
        accessibilityState={{ disabled: disabled || value <= min }}
        style={[s.step, compact && styles.compactStep, (disabled || value <= min) && s.disabled]}
        onPress={() => onChange(value - 1)}
      >
        <Text style={s.label}>−</Text>
      </Pressable>
      <Text accessibilityLiveRegion="polite" style={s.label}>
        {value}
      </Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${name} 수량 늘리기`}
        disabled={disabled || value >= max}
        accessibilityState={{ disabled: disabled || value >= max }}
        style={[s.step, compact && styles.compactStep, (disabled || value >= max) && s.disabled]}
        onPress={() => onChange(value + 1)}
      >
        <Text style={s.label}>＋</Text>
      </Pressable>
    </View>
  );
}
const styles = StyleSheet.create({ compactStep: { minWidth: 28, minHeight: 36 } });
