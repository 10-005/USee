import { useState } from 'react';
import {
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
} from 'react-native';
import { colors, typography } from '../../theme';
import type { SimulationEvent } from '../../services/simulation/events';
import CloseIcon from '../../assets/icons/common/cancel.svg';
import FilterArrowIcon from '../../assets/icons/common/filter-arrow.svg';
import CalendarIcon from '../../assets/icons/common/calendar.svg';

const treatmentItems = [
  { label: '초진 진찰료', cost: 15000 },
  { label: '혈액검사', cost: 80000 },
  { label: '주사', cost: 15000 },
  { label: '내복약 3일분', cost: 10000 },
];

type Props = {
  event: SimulationEvent;
  remaining: number;
  onClose: () => void;
  onPay: () => void;
  onIgnore: () => void;
};

export function VetVisitDialog({ event, remaining, onClose, onPay, onIgnore }: Props) {
  const [expanded, setExpanded] = useState(true);
  const { height } = useWindowDimensions();
  const cost = event.vetVisit?.cost ?? 0;
  return (
    <Modal transparent visible animationType="fade" statusBarTranslucent onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View accessibilityViewIsModal style={[styles.card, { maxHeight: height * 0.88 - 40 }]}>
          <View style={styles.header}>
            <Text accessibilityRole="header" style={styles.title}>
              🏥 진료 결과서
            </Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="진료 결과서 닫기"
              onPress={onClose}
              style={styles.close}
            >
              <CloseIcon width={12} height={12} />
            </Pressable>
          </View>
          <ScrollView
            style={styles.scroll}
            contentContainerStyle={styles.content}
            scrollIndicatorInsets={{ right: 4 }}
          >
            <Text style={styles.label}>진단명</Text>
            <View style={styles.box}>
              <Text style={styles.label}>의학 용어: 급성 위장염</Text>
              <Text style={styles.diagnosis}>배탈</Text>
            </View>
            <Text style={styles.label}>진료 내용</Text>
            <View style={styles.treatments}>
              {(cost === 120000 ? treatmentItems : [{ label: '진료 및 치료비', cost }]).map(
                item => (
                  <View key={item.label} style={styles.treatmentRow}>
                    <Text style={styles.label}>{item.label}</Text>
                    <Text style={styles.text}>{item.cost.toLocaleString('ko-KR')}원</Text>
                  </View>
                ),
              )}
            </View>
            <View style={styles.totalRow}>
              <Text style={styles.title}>총 비용</Text>
              <Text style={styles.totalCost}>{cost.toLocaleString('ko-KR')}원</Text>
            </View>
            <Pressable
              accessibilityRole="button"
              accessibilityState={{ expanded }}
              onPress={() => setExpanded(value => !value)}
              style={styles.row}
            >
              <Text style={styles.text}>상세 정보</Text>
              <View style={expanded && styles.expandedArrow}>
                <FilterArrowIcon width={20} height={20} />
              </View>
            </Pressable>
            {expanded ? (
              <View style={styles.details}>
                <Text style={styles.label}>💊 하루 2번, 식후 복용</Text>
                <Text style={styles.label}>⚠️ 오늘은 물만 조금씩</Text>
                <View style={styles.detailRow}>
                  <CalendarIcon width={18} height={18} />
                  <Text style={styles.label}>재진: 11월 12일</Text>
                </View>
              </View>
            ) : null}
            {remaining > 1 ? (
              <Text style={styles.label}>
                대기 중인 진료 {remaining}건 · 처리 후 다음 진료가 표시됩니다.
              </Text>
            ) : null}
          </ScrollView>
          <View style={styles.row}>
            <Text style={styles.label}>병원명: 동물병원</Text>
            <Text style={styles.label}>수의사: 김의사</Text>
          </View>
          <Pressable accessibilityRole="button" onPress={onPay} style={styles.pay}>
            <Text style={styles.payLabel}>병원비 지불하기</Text>
          </Pressable>
          <Pressable accessibilityRole="button" onPress={onIgnore} style={styles.ignore}>
            <Text style={styles.label}>무시하기</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'center', padding: 24, backgroundColor: 'rgba(0,0,0,0.3)' },
  card: {
    width: '100%',
    maxWidth: 520,
    alignSelf: 'center',
    backgroundColor: colors.white,
    borderRadius: 24,
    padding: 24,
    paddingTop: 12,
    paddingBottom: 12,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
    paddingVertical: 10,
  },
  title: { ...typography.bodySemiBold16, color: colors.black },
  totalCost: { ...typography.bodySemiBold16, color: colors.danger },
  diagnosis: { ...typography.bodyRegular16, color: colors.black, marginTop: 6 },
  treatments: { gap: 12 },
  treatmentRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  totalRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 14,
  },
  details: { gap: 10 },
  detailRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  expandedArrow: { transform: [{ rotate: '180deg' }] },
  text: { ...typography.bodyRegular16, color: colors.black },
  label: { ...typography.bodyRegular14, color: colors.gray500 },
  description: { ...typography.bodyRegular14, color: colors.gray500, lineHeight: 22, marginTop: 8 },
  close: {
    minWidth: 44,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: -16,
  },
  scroll: { marginHorizontal: -24 },
  content: { gap: 14, paddingHorizontal: 24, paddingBottom: 20 },
  box: {
    backgroundColor: '#F8F9FA',
    borderWidth: 1,
    borderColor: colors.gray200,
    borderRadius: 16,
    padding: 16,
  },
  pay: {
    backgroundColor: colors.green400,
    borderRadius: 12,
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 12,
  },
  payLabel: { ...typography.bodySemiBold16, color: colors.white },
  ignore: { minHeight: 44, alignItems: 'center', justifyContent: 'center' },
});
