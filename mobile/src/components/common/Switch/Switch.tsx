import { Pressable, StyleSheet, View } from 'react-native';

import { colors, shadows } from '../../../theme';

type SwitchProps = {
  disabled?: boolean;
  label: string;
  onValueChange: (value: boolean) => void;
  value: boolean;
};

export function Switch({ disabled = false, label, onValueChange, value }: SwitchProps) {
  return (
    <Pressable
      accessibilityLabel={label}
      accessibilityRole="switch"
      accessibilityState={{ checked: value, disabled }}
      disabled={disabled}
      onPress={() => onValueChange(!value)}
      style={({ pressed }) => [
        styles.track,
        value ? styles.trackOn : styles.trackOff,
        pressed && !disabled && styles.pressed,
        disabled && styles.disabled,
      ]}
    >
      <View style={[styles.thumb, value && styles.thumbOn]} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  track: {
    borderRadius: 10,
    height: 20,
    justifyContent: 'center',
    paddingHorizontal: 2,
    width: 36,
  },
  trackOn: {
    backgroundColor: colors.green400,
  },
  trackOff: {
    backgroundColor: colors.gray200,
  },
  thumb: {
    ...shadows.level1,
    backgroundColor: colors.white,
    borderRadius: 8,
    height: 16,
    width: 16,
  },
  thumbOn: {
    alignSelf: 'flex-end',
  },
  pressed: {
    opacity: 0.75,
  },
  disabled: {
    opacity: 0.45,
  },
});
