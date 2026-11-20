import { StyleSheet, TextInput, View, type TextInputProps } from 'react-native';
import SearchIcon from '../../../assets/icons/common/search.svg';
import { colors, radius, typography } from '../../../theme';

export function SearchBar(props: TextInputProps) {
  return (
    <View style={styles.box}>
      <SearchIcon height={24} width={24} />
      <TextInput
        placeholderTextColor={colors.gray400}
        {...props}
        style={[styles.input, props.style]}
      />
    </View>
  );
}
const styles = StyleSheet.create({
  box: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    flex: 1,
    flexDirection: 'row',
    height: 50,
    paddingHorizontal: 14,
  },
  input: {
    ...typography.bodyRegular16,
    color: colors.gray700,
    flex: 1,
    marginLeft: 4,
    paddingVertical: 0,
  },
});
