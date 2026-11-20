import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, radius, typography } from '../../theme';
import { CalendarDialog } from './CalendarDialog';
import { CalendarMonthGrid } from './CalendarMonthGrid';

type Props = {
  hasValue?: boolean;
  maximumDate?: Date;
  minimumDate?: Date;
  onClose: () => void;
  onConfirm: (date: Date) => void;
  title?: string;
  value: Date;
  visible: boolean;
};

const startOfDay = (date: Date) => new Date(date.getFullYear(), date.getMonth(), date.getDate());

export function DatePickerModal({
  hasValue = true,
  maximumDate,
  minimumDate,
  onClose,
  onConfirm,
  title = '날짜 선택',
  value,
  visible,
}: Props) {
  const min = minimumDate ? startOfDay(minimumDate).getTime() : -Infinity;
  const max = maximumDate ? startOfDay(maximumDate).getTime() : Infinity;
  const initialTime = Math.min(max, Math.max(min, startOfDay(value).getTime()));
  const [selectedDate, setSelectedDate] = useState<Date | null>(() =>
    hasValue ? new Date(initialTime) : null,
  );
  const [visibleDate, setVisibleDate] = useState(() => new Date(initialTime));

  useEffect(() => {
    if (visible) {
      setSelectedDate(hasValue ? new Date(initialTime) : null);
      setVisibleDate(new Date(initialTime));
    }
  }, [hasValue, initialTime, visible]);

  return (
    <CalendarDialog visible={visible} title={title} onClose={onClose}>
      <CalendarMonthGrid
        month={visibleDate}
        onMonthChange={date => {
          setVisibleDate(date);
          if (
            selectedDate?.getFullYear() !== date.getFullYear() ||
            selectedDate?.getMonth() !== date.getMonth()
          ) {
            setSelectedDate(null);
          }
        }}
        minimumDate={minimumDate}
        maximumDate={maximumDate}
        dayState={date => (date.getTime() === selectedDate?.getTime() ? 'selected' : undefined)}
        onDayPress={setSelectedDate}
      />
      <View style={styles.actions}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="날짜 선택 확인"
          disabled={!selectedDate}
          onPress={() => selectedDate && onConfirm(selectedDate)}
          style={[styles.confirmButton, !selectedDate && styles.inactive]}
        >
          <Text style={styles.confirmText}>다음</Text>
        </Pressable>
      </View>
    </CalendarDialog>
  );
}

const styles = StyleSheet.create({
  actions: { flexDirection: 'row', gap: 10, marginTop: 18 },
  inactive: { backgroundColor: '#D9D8D1' },
  confirmButton: {
    alignItems: 'center',
    backgroundColor: '#009F60',
    borderRadius: radius.md,
    flex: 1,
    height: 48,
    justifyContent: 'center',
  },
  confirmText: { ...typography.bodySemiBold16, color: colors.white },
});
