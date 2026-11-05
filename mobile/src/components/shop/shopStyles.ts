import { StyleSheet } from 'react-native';
import { colors, typography } from '../../theme';

export const shopStyles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.white },
  content: { padding: 20, gap: 16, paddingBottom: 32 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  between: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  title: { ...typography.titleBold18, fontSize: 24, lineHeight: 34, color: colors.black },
  label: { ...typography.bodySemiBold16, color: colors.black },
  body: { ...typography.bodyRegular14, color: colors.gray600, lineHeight: 22 },
  green: { ...typography.bodySemiBold16, color: colors.green400 },
  price: { ...typography.titleBold18, fontSize: 16, lineHeight: 21, color: colors.blue },
  banner: { backgroundColor: '#EFF9F2', padding: 18, borderRadius: 16, gap: 8 },
  chip: {
    borderWidth: 1,
    borderColor: colors.gray200,
    borderRadius: 24,
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  activeChip: { backgroundColor: colors.green400, borderColor: colors.green400 },
  activeText: { color: colors.white },
  footer: {
    padding: 20,
    gap: 12,
    borderTopWidth: 1,
    borderColor: colors.gray200,
    backgroundColor: colors.white,
  },
  empty: { padding: 32, alignItems: 'center', gap: 16 },
  line: { height: 1, backgroundColor: colors.gray200 },
  quantity: {
    borderWidth: 1,
    borderColor: colors.gray200,
    borderRadius: 10,
    flexDirection: 'row',
    alignItems: 'center',
  },
  step: { minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  disabled: { opacity: 0.35 },
  flex: { flex: 1 },
});
