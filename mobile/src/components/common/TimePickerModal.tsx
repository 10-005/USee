import { useEffect, useRef, useState, type ComponentRef } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { colors, radius, typography } from '../../theme';

type Props = {
  onClose: () => void;
  onConfirm: (date: Date) => void;
  title?: string;
  value: Date;
  visible: boolean;
};

const ITEM_HEIGHT = 40;
const hours = Array.from({ length: 12 }, (_, index) => index + 1);
const minutes = Array.from({ length: 60 }, (_, index) => index);

export function TimePickerModal({
  onClose,
  onConfirm,
  title = '시간 선택',
  value,
  visible,
}: Props) {
  const insets = useSafeAreaInsets();
  const hourScrollRef = useRef<ComponentRef<typeof ScrollView>>(null);
  const minuteScrollRef = useRef<ComponentRef<typeof ScrollView>>(null);
  const [period, setPeriod] = useState<'am' | 'pm'>(value.getHours() < 12 ? 'am' : 'pm');
  const [hour, setHour] = useState(value.getHours() % 12 || 12);
  const [minute, setMinute] = useState(value.getMinutes());

  useEffect(() => {
    if (!visible) {
      return;
    }
    const nextHour = value.getHours() % 12 || 12;
    const nextMinute = value.getMinutes();
    setPeriod(value.getHours() < 12 ? 'am' : 'pm');
    setHour(nextHour);
    setMinute(nextMinute);
    const timer = setTimeout(() => {
      hourScrollRef.current?.scrollTo({ animated: false, y: (nextHour - 1) * ITEM_HEIGHT });
      minuteScrollRef.current?.scrollTo({ animated: false, y: nextMinute * ITEM_HEIGHT });
    }, 0);
    return () => clearTimeout(timer);
  }, [value, visible]);

  const confirm = () => {
    const selected = new Date(value);
    const hour24 = period === 'am' ? hour % 12 : (hour % 12) + 12;
    selected.setHours(hour24, minute, 0, 0);
    onConfirm(selected);
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
        <Pressable accessibilityLabel="시간 선택 닫기" onPress={onClose} style={StyleSheet.absoluteFill} />
        <View style={[styles.sheet, { paddingBottom: Math.max(insets.bottom, 16) + 12 }]}>
          <Text style={styles.title}>{title}</Text>
          <View style={styles.divider} />
          <View style={styles.periodRow}>
            {[
              { key: 'am', label: '오전' },
              { key: 'pm', label: '오후' },
            ].map(option => {
              const selected = period === option.key;
              return (
                <Pressable
                  key={option.key}
                  onPress={() => setPeriod(option.key as 'am' | 'pm')}
                  style={[styles.periodButton, selected && styles.selectedPeriodButton]}
                >
                  <Text style={[styles.periodText, selected && styles.selectedText]}>
                    {option.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>
          <View style={styles.selectorLabels}>
            <Text style={styles.selectorLabel}>시</Text>
            <Text style={[styles.colon, styles.hiddenColon]}>:</Text>
            <Text style={styles.selectorLabel}>분</Text>
          </View>
          <View style={styles.selectors}>
            <ScrollView
              ref={hourScrollRef}
              contentContainerStyle={styles.selectorContent}
              decelerationRate="fast"
              nestedScrollEnabled
              showsVerticalScrollIndicator={false}
              style={styles.selector}
            >
              {hours.map(option => (
                <TimeOption
                  key={option}
                  label={String(option).padStart(2, '0')}
                  onPress={() => setHour(option)}
                  selected={hour === option}
                />
              ))}
            </ScrollView>
            <Text style={styles.colon}>:</Text>
            <ScrollView
              ref={minuteScrollRef}
              contentContainerStyle={styles.selectorContent}
              decelerationRate="fast"
              nestedScrollEnabled
              showsVerticalScrollIndicator={false}
              style={styles.selector}
            >
              {minutes.map(option => (
                <TimeOption
                  key={option}
                  label={String(option).padStart(2, '0')}
                  onPress={() => setMinute(option)}
                  selected={minute === option}
                />
              ))}
            </ScrollView>
          </View>
          <View style={styles.actions}>
            <Pressable onPress={onClose} style={styles.cancelButton}>
              <Text style={styles.cancelText}>취소</Text>
            </Pressable>
            <Pressable onPress={confirm} style={styles.confirmButton}>
              <Text style={styles.confirmText}>확인</Text>
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

function TimeOption({
  label,
  onPress,
  selected,
}: {
  label: string;
  onPress: () => void;
  selected: boolean;
}) {
  return (
    <Pressable onPress={onPress} style={[styles.timeOption, selected && styles.selectedTimeOption]}>
      <Text style={[styles.timeOptionText, selected && styles.selectedTimeOptionText]}>{label}</Text>
    </Pressable>
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
    paddingHorizontal: 20,
    paddingTop: 18,
  },
  title: { ...typography.titleSemiBold18, color: colors.gray800, textAlign: 'center' },
  divider: { backgroundColor: colors.gray200, height: 1, marginTop: 16 },
  periodRow: { flexDirection: 'row', gap: 8, marginTop: 16 },
  periodButton: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.pill,
    borderWidth: 1,
    flex: 1,
    height: 40,
    justifyContent: 'center',
  },
  selectedPeriodButton: { backgroundColor: '#ECFDF3', borderColor: colors.green400 },
  periodText: { ...typography.bodySemiBold16, color: colors.gray500 },
  selectedText: { color: colors.green400 },
  selectorLabels: { flexDirection: 'row', gap: 16, marginTop: 16 },
  selectorLabel: {
    ...typography.captionSemiBold12,
    color: colors.gray500,
    flex: 1,
    textAlign: 'center',
  },
  selectors: { alignItems: 'center', flexDirection: 'row', gap: 16, marginTop: 6 },
  selector: {
    backgroundColor: '#F7F6F2',
    borderRadius: radius.md,
    flex: 1,
    height: 160,
  },
  selectorContent: { padding: 8 },
  colon: { ...typography.titleSemiBold18, color: colors.gray600 },
  hiddenColon: { opacity: 0 },
  timeOption: {
    alignItems: 'center',
    borderRadius: radius.md,
    height: ITEM_HEIGHT,
    justifyContent: 'center',
  },
  selectedTimeOption: { backgroundColor: colors.green400 },
  timeOptionText: { ...typography.bodySemiBold16, color: colors.gray600 },
  selectedTimeOptionText: { color: colors.white },
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
