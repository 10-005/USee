import { type ComponentRef, useContext, useId, useRef } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, radius, typography } from '../../theme';
import { CalendarOpenField, useCalendarMenu } from './CalendarDialog';

type Props = {
  month: Date;
  onMonthChange: (date: Date) => void;
  onDayPress: (date: Date) => void;
  dayState: (date: Date) => 'selected' | 'range' | undefined;
  showHeader?: boolean;
  minimumDate?: Date;
  maximumDate?: Date;
};

const midnight = (date: Date) =>
  new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();

export function CalendarMonthFields({
  month,
  onMonthChange,
  minimumDate,
  maximumDate,
  label = '',
  onOpen,
}: Pick<Props, 'month' | 'onMonthChange' | 'minimumDate' | 'maximumDate'> & {
  label?: string;
  onOpen?: () => void;
}) {
  const header = useRef<ComponentRef<typeof View>>(null);
  const openMenu = useCalendarMenu();
  const fieldsId = useId();
  const openField = useContext(CalendarOpenField);
  const year = month.getFullYear();
  const monthIndex = month.getMonth();
  const firstMonth = (y: number) => (minimumDate?.getFullYear() === y ? minimumDate.getMonth() : 0);
  const lastMonth = (y: number) => (maximumDate?.getFullYear() === y ? maximumDate.getMonth() : 11);
  const show = (field: 'year' | 'month') => {
    onOpen?.();
    const isYear = field === 'year';
    openMenu(header, {
      fieldId: fieldsId + field,
      first: isYear ? minimumDate?.getFullYear() ?? year - 120 : firstMonth(year) + 1,
      last: isYear ? maximumDate?.getFullYear() ?? year + 100 : lastMonth(year) + 1,
      current: isYear ? year : monthIndex + 1,
      unit: isYear ? '년' : '월',
      select: option => {
        const y = isYear ? option : year;
        const m = isYear ? Math.max(firstMonth(y), Math.min(lastMonth(y), monthIndex)) : option - 1;
        onMonthChange(new Date(y, m, 1));
      },
    });
  };

  return (
    <View ref={header} collapsable={false} style={styles.header}>
      {(['year', 'month'] as const).map(field => (
        <Pressable
          key={field}
          accessibilityRole="button"
          accessibilityLabel={[label, field === 'year' ? '연도 선택' : '월 선택']
            .filter(Boolean)
            .join(' ')}
          accessibilityValue={{ text: field === 'year' ? year + '년' : monthIndex + 1 + '월' }}
          onPress={() => show(field)}
          style={[styles.field, label && openField === fieldsId + field ? styles.activeField : null]}
        >
          <Text style={styles.fieldText}>{field === 'year' ? year : monthIndex + 1}</Text>
        </Pressable>
      ))}
    </View>
  );
}

export function CalendarMonthGrid({
  month,
  onMonthChange,
  onDayPress,
  dayState,
  showHeader = true,
  minimumDate,
  maximumDate,
}: Props) {
  const year = month.getFullYear();
  const monthIndex = month.getMonth();
  const firstDay = new Date(year, monthIndex, 1).getDay();
  const dayCount = new Date(year, monthIndex + 1, 0).getDate();
  const weeks = Math.max(5, Math.ceil((firstDay + dayCount) / 7));
  const min = minimumDate ? midnight(minimumDate) : -Infinity;
  const max = maximumDate ? midnight(maximumDate) : Infinity;
  return (
    <View>
      {showHeader && (
        <CalendarMonthFields
          month={month}
          onMonthChange={onMonthChange}
          minimumDate={minimumDate}
          maximumDate={maximumDate}
        />
      )}
      <View>
        <View style={styles.row}>
          {['일', '월', '화', '수', '목', '금', '토'].map(label => (
            <Text key={label} style={styles.weekday}>
              {label}
            </Text>
          ))}
        </View>
        {Array.from({ length: weeks }, (_, week) => (
          <View key={week} style={[styles.row, styles.dayRow, { height: 240 / weeks - 8 }]}>
            {Array.from({ length: 7 }, (_cell, column) => {
              const day = week * 7 + column - firstDay + 1;
              if (day < 1 || day > dayCount) {
                return (
                  <View
                    key={column}
                    style={[styles.cell, week * 7 < firstDay + dayCount && styles.day]}
                  />
                );
              }
              const date = new Date(year, monthIndex, day);
              const disabled = date.getTime() < min || date.getTime() > max;
              const state = dayState(date);
              return (
                <Pressable
                  key={column}
                  accessibilityRole="button"
                  accessibilityLabel={year + '년 ' + (monthIndex + 1) + '월 ' + day + '일'}
                  accessibilityState={{ disabled, selected: state === 'selected' }}
                  disabled={disabled}
                  onPress={() => onDayPress(date)}
                  style={[
                    styles.cell,
                    styles.day,
                    state === 'range' && styles.range,
                    state === 'selected' && styles.selected,
                  ]}
                >
                  <Text
                    style={[
                      styles.label,
                      disabled && styles.disabledText,
                      state === 'selected' && styles.selectedText,
                    ]}
                  >
                    {day}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginBottom: 14,
  },
  field: {
    flex: 1,
    height: 44,
    borderWidth: 1,
    borderColor: colors.gray300,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  activeField: { borderColor: colors.green400 },
  fieldText: { ...typography.bodyRegular16, color: colors.gray800 },
  label: { ...typography.bodySemiBold16, color: colors.gray800 },
  row: { flexDirection: 'row', justifyContent: 'center', gap: 4, marginTop: 4 },
  dayRow: { marginTop: 8 },
  weekday: {
    flex: 1,

    textAlign: 'center',
    ...typography.captionSemiBold12,
    color: colors.gray800,
  },
  cell: { flex: 1, alignSelf: 'stretch' },
  day: {
    borderRadius: radius.md,
    backgroundColor: '#F7F6F2',
    alignItems: 'center',
    justifyContent: 'center',
  },
  range: { backgroundColor: '#DCFCE7' },
  selected: { backgroundColor: '#7BDBA1' },
  disabledText: { color: colors.gray300 },
  selectedText: { color: colors.black },
});
