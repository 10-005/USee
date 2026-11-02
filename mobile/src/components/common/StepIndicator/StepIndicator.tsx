import { StyleSheet, View } from 'react-native';

import { colors, radius, spacing } from '../../../theme';

type StepIndicatorProps = {
  currentStep: number;
  totalSteps: number;
};

export function StepIndicator({ currentStep, totalSteps }: StepIndicatorProps) {
  const safeTotal = Math.max(1, totalSteps);
  const safeCurrent = Math.min(Math.max(1, currentStep), safeTotal);

  return (
    <View
      accessibilityLabel={`${safeTotal}단계 중 ${safeCurrent}단계`}
      accessibilityRole="progressbar"
      accessibilityValue={{ min: 1, max: safeTotal, now: safeCurrent }}
      style={styles.container}
    >
      {Array.from({ length: safeTotal }, (_, index) => (
        <View key={index} style={[styles.step, index < safeCurrent && styles.activeStep]} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    gap: spacing[2],
  },
  step: {
    backgroundColor: colors.gray200,
    borderRadius: radius.xs,
    height: 8,
    width: 12,
  },
  activeStep: {
    backgroundColor: colors.green200,
  },
});
