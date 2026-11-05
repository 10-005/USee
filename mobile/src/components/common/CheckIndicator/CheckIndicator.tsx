import { StyleSheet, View } from 'react-native';
import PawCheckIcon from '../../../assets/icons/common/paw-check.svg';
import { colors } from '../../../theme';

export function CheckIndicator({ checked }: { checked: boolean }) {
  return (
    <View style={[styles.box, checked && styles.selected]}>
      {checked ? <PawCheckIcon /> : null}
    </View>
  );
}
const styles = StyleSheet.create({
  box: {
    alignItems: 'center',
    borderColor: colors.gray300,
    borderRadius: 2,
    borderWidth: 1,
    height: 18,
    justifyContent: 'center',
    width: 18,
  },
  selected: { backgroundColor: colors.green400, borderColor: colors.green400 },
});
