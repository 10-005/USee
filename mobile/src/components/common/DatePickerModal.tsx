import { useEffect, useMemo, useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';

import LeftArrowIcon from '../../assets/icons/common/left-arrow.svg';
import RightArrowIcon from '../../assets/icons/common/right-arrow.svg';
import { colors, radius, typography } from '../../theme';

type Props = {
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
  maximumDate,
  minimumDate,
  onClose,
  onConfirm,
  title = '날짜 선택',
  value,
  visible,
}: Props) {
  const [selectedDate, setSelectedDate] = useState(startOfDay(value));
  const [visibleDate, setVisibleDate] = useState(
    new Date(value.getFullYear(), value.getMonth(), 1),
  );

  useEffect(() => {
    if (!visible) {
      return;
    }
    const nextValue = startOfDay(value);
    setSelectedDate(nextValue);
    setVisibleDate(new Date(nextValue.getFullYear(), nextValue.getMonth(), 1));
  }, [value, visible]);

  const year = visibleDate.getFullYear();
  const month = visibleDate.getMonth();
  const firstDay = new Date(year, month, 1).getDay();
  const lastDate = new Date(year, month + 1, 0).getDate();
  const cellCount = Math.ceil((firstDay + lastDate) / 7) * 7;
  const days = useMemo(
    () => [
      ...Array.from({ length: firstDay }, () => 0),
      ...Array.from({ length: lastDate }, (_, index) => index + 1),
    ],
    [firstDay, lastDate],
  );
  const min = minimumDate ? startOfDay(minimumDate).getTime() : null;
  const max = maximumDate ? startOfDay(maximumDate).getTime() : null;

  const isOutOfRange = (date: Date) => {
    const time = date.getTime();
    return (min !== null && time < min) || (max !== null && time > max);
  };

  const moveMonth = (offset: number) => {
    setVisibleDate(current => new Date(current.getFullYear(), current.getMonth() + offset, 1));
  };

  return (
    <Modal
      animationType="fade"
      onRequestClose={onClose}
      statusBarTranslucent
      transparent
      visible={visible}
    >
      <View style={styles.overlay}>
        <Pressable accessibilityLabel="날짜 선택 닫기" onPress={onClose} style={StyleSheet.absoluteFill} />
        <View style={styles.sheet}>
          <Text style={styles.title}>{title}</Text>
          <View style={styles.divider} />
          <View style={styles.calendarHeader}>
            <Pressable
              accessibilityLabel="이전 달"
              hitSlop={8}
              onPress={() => moveMonth(-1)}
              style={styles.monthMove}
            >
              <LeftArrowIcon height={20} width={20} />
            </Pressable>
            <Text style={styles.calendarMonth}>{`${year}년 ${month + 1}월`}</Text>
            <Pressable
              accessibilityLabel="다음 달"
              hitSlop={8}
              onPress={() => moveMonth(1)}
              style={styles.monthMove}
            >
              <RightArrowIcon height={20} width={20} />
            </Pressable>
          </View>
          <View style={styles.weekRow}>
            {['일', '월', '화', '수', '목', '금', '토'].map(day => (
              <Text key={day} style={styles.weekText}>
                {day}
              </Text>
            ))}
          </View>
          <View style={styles.calendarGrid}>
            {Array.from({ length: cellCount / 7 }, (_unusedWeek, weekIndex) => (
              <View key={weekIndex} style={styles.calendarWeek}>
                {Array.from({ length: 7 }, (_unusedDay, dayIndex) => {
                  const index = weekIndex * 7 + dayIndex;
                  const day = days[index] ?? 0;
                  const date = day ? new Date(year, month, day) : null;
                  const disabled = !date || isOutOfRange(date);
                  const selected =
                    date !== null && date.getTime() === startOfDay(selectedDate).getTime();
                  return (
                    <Pressable
                      key={`${day}-${index}`}
                      disabled={disabled}
                      onPress={() => date && setSelectedDate(date)}
                      style={[styles.day, selected && styles.selectedDay]}
                    >
                      {day ? (
                        <Text
                          style={[
                            styles.dayText,
                            disabled && styles.disabledDayText,
                            selected && styles.selectedDayText,
                          ]}
                        >
                          {day}
                        </Text>
                      ) : null}
                    </Pressable>
                  );
                })}
              </View>
            ))}
          </View>
          <View style={styles.actions}>
            <Pressable onPress={onClose} style={styles.cancelButton}>
              <Text style={styles.cancelText}>취소</Text>
            </Pressable>
            <Pressable
              onPress={() => onConfirm(selectedDate)}
              style={styles.confirmButton}
            >
              <Text style={styles.confirmText}>확인</Text>
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    backgroundColor: 'rgba(17, 24, 39, 0.45)',
    flex: 1,
    justifyContent: 'flex-end',
  },
  sheet: {
    backgroundColor: colors.white,
    borderTopLeftRadius: radius.lg,
    borderTopRightRadius: radius.lg,
    paddingBottom: 28,
    paddingHorizontal: 20,
    paddingTop: 18,
  },
  title: { ...typography.titleSemiBold18, color: colors.gray800, textAlign: 'center' },
  divider: { backgroundColor: colors.gray200, height: 1, marginTop: 16 },
  calendarHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 8,
  },
  calendarMonth: { ...typography.bodySemiBold16, color: colors.gray800 },
  monthMove: { alignItems: 'center', height: 40, justifyContent: 'center', width: 40 },
  weekRow: { flexDirection: 'row', gap: 5, justifyContent: 'center', marginTop: 4 },
  weekText: {
    ...typography.captionSemiBold12,
    color: colors.black,
    flex: 1,
    maxWidth: 40,
    textAlign: 'center',
  },
  calendarGrid: { gap: 4, marginTop: 6 },
  calendarWeek: { flexDirection: 'row', gap: 5, justifyContent: 'center' },
  day: {
    alignItems: 'center',
    aspectRatio: 1,
    backgroundColor: '#F7F6F2',
    borderRadius: radius.md,
    flex: 1,
    justifyContent: 'center',
    maxWidth: 40,
  },
  selectedDay: { backgroundColor: colors.green400 },
  dayText: { ...typography.captionSemiBold12, color: colors.black },
  disabledDayText: { color: colors.gray300 },
  selectedDayText: { color: colors.white },
  actions: { flexDirection: 'row', gap: 10, marginTop: 18 },
  cancelButton: {
    alignItems: 'center',
    borderColor: colors.gray300,
    borderRadius: radius.md,
    borderWidth: 1,
    height: 48,
    justifyContent: 'center',
    width: 92,
  },
  cancelText: { ...typography.bodySemiBold16, color: colors.gray600 },
  confirmButton: {
    alignItems: 'center',
    backgroundColor: colors.green400,
    borderRadius: radius.md,
    flex: 1,
    height: 48,
    justifyContent: 'center',
  },
  confirmText: { ...typography.bodySemiBold16, color: colors.white },
});
