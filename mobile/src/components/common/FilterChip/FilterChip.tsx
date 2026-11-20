import { Pressable, StyleSheet, Text } from 'react-native';
import FilterArrowIcon from '../../../assets/icons/common/filter-arrow.svg';
import { colors, radius, typography } from '../../../theme';

export function FilterChip({
  label,
  accessibilityLabel,
  open = false,
  applied = false,
  onPress,
}: {
  label: string;
  accessibilityLabel: string;
  open?: boolean;
  applied?: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      accessibilityState={{ expanded: open, selected: applied }}
      onPress={onPress}
      style={[styles.chip, open && styles.open, applied && styles.applied]}
    >
      <Text style={[styles.text, applied && styles.appliedText]}>{label}</Text>
      <FilterArrowIcon height={12} width={12} />
    </Pressable>
  );
}
const styles = StyleSheet.create({
  chip: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.pill,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 2,
    height: 26,
    paddingLeft: 10,
    paddingRight: 5,
  },
  open: { backgroundColor: '#EBEAE5', borderColor: colors.gray500 },
  applied: { backgroundColor: '#ECFDF3', borderColor: colors.green400 },
  text: { ...typography.bodyRegular14, color: colors.gray500 },
  appliedText: { color: colors.green400 },
});
