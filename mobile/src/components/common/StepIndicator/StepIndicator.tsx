import { StyleSheet, View } from 'react-native';

import { colors, radius } from '../../../theme';

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
        <View key={index} style={[styles.step, index === safeCurrent - 1 && styles.activeStep]} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 6,
  },
  step: {
    backgroundColor: colors.gray200,
    borderRadius: radius.pill,
    height: 8,
    width: 8,
  },
  activeStep: {
    backgroundColor: colors.green200,
    width: 28,
  },
});
