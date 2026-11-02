import { forwardRef, type ComponentRef } from 'react';
import {
  StyleSheet,
  Text,
  TextInput as NativeTextInput,
  type TextInputProps as NativeTextInputProps,
  View,
} from 'react-native';

import { colors, radius, spacing, typography } from '../../../theme';

type TextInputProps = NativeTextInputProps & {
  errorMessage?: string;
  label?: string;
};

export const TextInput = forwardRef<ComponentRef<typeof NativeTextInput>, TextInputProps>(
  ({ errorMessage, label, style, ...inputProps }, ref) => (
    <View style={styles.container}>
      {label ? <Text style={styles.label}>{label}</Text> : null}
      <NativeTextInput
        ref={ref}
        accessibilityLabel={inputProps.accessibilityLabel ?? label}
        aria-invalid={Boolean(errorMessage)}
        placeholderTextColor={colors.gray300}
        style={[styles.input, errorMessage && styles.errorInput, style]}
        {...inputProps}
      />
      {errorMessage ? (
        <Text accessibilityLiveRegion="polite" style={styles.errorMessage}>
          {errorMessage}
        </Text>
      ) : null}
    </View>
  ),
);

TextInput.displayName = 'TextInput';

const styles = StyleSheet.create({
  container: {
    gap: spacing[2],
  },
  label: {
    ...typography.bodyRegular14,
    color: colors.gray600,
  },
  input: {
    ...typography.bodyRegular16,
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    color: colors.gray700,
    minHeight: 48,
    paddingHorizontal: spacing[7],
    paddingVertical: spacing[6],
  },
  errorInput: {
    borderColor: colors.danger,
  },
  errorMessage: {
    ...typography.captionRegular10,
    color: colors.danger,
  },
});
